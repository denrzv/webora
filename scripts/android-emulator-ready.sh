#!/usr/bin/env bash
# Blocks until the emulator is ready to be photographed, or fails with what it saw.
#
# `sys.boot_completed=1` means the boot broadcast was dispatched. It does not mean
# system_server has finished starting, and it is emphatically not a signal that the
# display holds anything worth capturing. This gate therefore asks four questions
# instead of one, and requires the same answer on several consecutive samples: a
# single lucky sample is how a fixed `sleep` fails, only slower.
#
# It records the focused window and deliberately does NOT classify it. Deciding what
# a window *is* — Webora, a dismissable System UI ANR, or something that must fail the
# run — belongs to ScreenEvidencePolicy, which the JVM gate can test. A second copy of
# that knowledge here would be free to drift out of agreement with the first.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

READY_SETTLE_SAMPLES="${READY_SETTLE_SAMPLES:-3}"
READY_DEADLINE_SECONDS="${READY_DEADLINE_SECONDS:-180}"
READY_SAMPLE_INTERVAL_SECONDS="${READY_SAMPLE_INTERVAL_SECONDS:-3}"
READINESS_LOG="${READINESS_LOG:-artifacts/readiness.txt}"
CPU_QUIET_BUSY_PERCENT=50

# Parse the aggregate row only. Output is `total idle`, where idle includes iowait because neither
# consumes a virtual CPU. Shell text is an observation, not a guarantee: refuse missing or malformed
# fields rather than converting them to zero and accidentally declaring a dead device quiet.
cpu_counters() {
  local row="$1"
  local -a fields
  read -r -a fields <<< "${row}"
  if (( ${#fields[@]} < 5 )) || [[ "${fields[0]:-}" != "cpu" ]]; then
    printf 'invalid:missing-aggregate-row\n'
    return 1
  fi

  local field total=0 index
  # guest and guest_nice (fields 9 and 10 after the label) are already included in user and nice.
  # Summing them twice would make virtualization activity look like extra capacity consumption.
  for (( index = 1; index < ${#fields[@]} && index <= 8; index++ )); do
    field="${fields[index]}"
    if [[ ! "${field}" =~ ^[0-9]+$ ]]; then
      printf 'invalid:non-numeric-counter\n'
      return 1
    fi
    total=$(( total + field ))
  done
  printf '%s %s\n' "${total}" "$(( fields[4] + ${fields[5]:-0} ))"
}

# Pure interval classifier. The percentage rounds up: a threshold is a maximum, and truncation must
# not turn a slightly-too-busy interval into a quiet one.
cpu_interval_verdict() {
  local previous_total="$1" previous_idle="$2" current_total="$3" current_idle="$4"
  local threshold="${5:-$CPU_QUIET_BUSY_PERCENT}"
  local value
  for value in "${previous_total}" "${previous_idle}" "${current_total}" "${current_idle}" "${threshold}"; do
    if [[ ! "${value}" =~ ^[0-9]+$ ]]; then
      printf 'cpu-invalid:non-numeric-counter\n'
      return 1
    fi
  done

  local total_delta=$(( current_total - previous_total ))
  local idle_delta=$(( current_idle - previous_idle ))
  if (( total_delta <= 0 || idle_delta < 0 || idle_delta > total_delta )); then
    printf 'cpu-invalid:non-monotonic-counter\n'
    return 1
  fi

  local busy_delta=$(( total_delta - idle_delta ))
  local busy_percent=$(( (busy_delta * 100 + total_delta - 1) / total_delta ))
  if (( busy_percent > threshold )); then
    printf 'cpu-busy:%s%%>%s%%\n' "${busy_percent}" "${threshold}"
    return 1
  fi
  printf 'cpu-quiet:%s%%<=%s%%\n' "${busy_percent}" "${threshold}"
}

# Pure: five observed strings in, one verdict token out. No adb, no globals, no I/O —
# which is what lets scripts/android-emulator-ready-selftest.sh exercise every branch
# on a machine with no /dev/kvm.
readiness_verdict() {
  local boot_completed="$1" bootanim_state="$2" pm_path="$3" focus_line="$4" cpu_verdict="$5"

  if [[ "${boot_completed}" != "1" ]]; then
    printf 'boot-incomplete:%s\n' "${boot_completed:-<empty>}"
    return 1
  fi
  # The condition is "the boot animation does not own the display", which is false only while the
  # service is actually running. It is NOT `service.bootanim.exit == 1`: this workflow launches the
  # emulator with `-no-boot-anim`, so bootanim never runs and never sets that property, and run
  # 31512048008 spent 49 samples and its whole deadline waiting for a `1` that could not arrive.
  if [[ "${bootanim_state}" == "running" ]]; then
    printf 'bootanim-running:%s\n' "${bootanim_state}"
    return 1
  fi
  if [[ "${pm_path}" != package:* ]]; then
    printf 'package-manager-silent:%s\n' "${pm_path:-<empty>}"
    return 1
  fi
  if [[ -z "${focus_line}" || "${focus_line}" == *"mCurrentFocus=null"* ]]; then
    printf 'no-focused-window:%s\n' "${focus_line:-<empty>}"
    return 1
  fi
  if [[ "${cpu_verdict}" != cpu-quiet:* ]]; then
    printf '%s\n' "${cpu_verdict:-cpu-invalid:missing-observation}"
    return 1
  fi

  printf 'ready\n'
}

device_getprop() {
  adb shell getprop "$1" 2>/dev/null | tr -d '\r\n'
}

# `pm path android` answers only once PackageManager is serving binder calls, which is
# a much later moment than the boot broadcast.
device_package_manager() {
  adb shell pm path android 2>/dev/null | tr -d '\r' | head -1
}

# `dumpsys window` with no subcommand, because it is the spelling that has been stable
# across API levels, and because ScreenEvidencePolicy parses the same dump on the device
# side. Two different commands would be two different contracts.
device_current_focus() {
  adb shell dumpsys window 2>/dev/null | tr -d '\r' \
    | grep -m1 'mCurrentFocus=' | sed 's/^[[:space:]]*//'
}

device_cpu_counters() {
  local row
  row="$(adb shell cat /proc/stat 2>/dev/null | tr -d '\r' | head -1)"
  cpu_counters "${row}"
}

main() {
  cd "${ROOT}" || exit 1
  mkdir -p "$(dirname "${READINESS_LOG}")"

  local deadline=$(( SECONDS + READY_DEADLINE_SECONDS ))
  local sample=0 consecutive=0 verdict="" previous_total="" previous_idle=""

  {
    printf 'settle_samples=%s deadline_seconds=%s interval_seconds=%s\n' \
      "${READY_SETTLE_SAMPLES}" "${READY_DEADLINE_SECONDS}" "${READY_SAMPLE_INTERVAL_SECONDS}"
  } >> "${READINESS_LOG}"

  while (( SECONDS < deadline )); do
    sample=$(( sample + 1 ))
    local counters current_total="" current_idle="" cpu_verdict=""
    local observed_previous_total="${previous_total}" observed_previous_idle="${previous_idle}"
    counters="$(device_cpu_counters)"
    if [[ "${counters}" == invalid:* ]]; then
      cpu_verdict="cpu-${counters}"
    else
      read -r current_total current_idle <<< "${counters}"
      if [[ -z "${previous_total}" ]]; then
        cpu_verdict="cpu-baseline:first-observation"
      else
        cpu_verdict="$(cpu_interval_verdict \
          "${previous_total}" "${previous_idle}" "${current_total}" "${current_idle}")"
      fi
      previous_total="${current_total}"
      previous_idle="${current_idle}"
    fi

    verdict="$(readiness_verdict \
      "$(device_getprop sys.boot_completed)" \
      "$(device_getprop init.svc.bootanim)" \
      "$(device_package_manager)" \
      "$(device_current_focus)" \
      "${cpu_verdict}")"

    if [[ "${verdict}" == "ready" ]]; then
      consecutive=$(( consecutive + 1 ))
    else
      consecutive=0
    fi

    printf '%s sample=%s elapsed=%ss consecutive_ready=%s cpu_previous=%s/%s cpu_current=%s/%s cpu=%s verdict=%s\n' \
      "$(date -u '+%H:%M:%S')" "${sample}" "${SECONDS}" "${consecutive}" \
      "${observed_previous_total:-<none>}" "${observed_previous_idle:-<none>}" \
      "${current_total:-<invalid>}" "${current_idle:-<invalid>}" "${cpu_verdict}" "${verdict}" \
      >> "${READINESS_LOG}"

    if (( consecutive >= READY_SETTLE_SAMPLES )); then
      printf 'settled after %s samples (%ss)\n' "${sample}" "${SECONDS}" >> "${READINESS_LOG}"
      cat "${READINESS_LOG}"
      return 0
    fi

    sleep "${READY_SAMPLE_INTERVAL_SECONDS}"
  done

  printf 'deadline reached after %s samples (%ss); last verdict %s\n' \
    "${sample}" "${SECONDS}" "${verdict}" >> "${READINESS_LOG}"
  cat "${READINESS_LOG}" >&2
  return 1
}

# Sourcing defines the functions and runs nothing.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
