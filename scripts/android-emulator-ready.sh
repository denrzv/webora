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

# Pure: four observed strings in, one verdict token out. No adb, no globals, no I/O —
# which is what lets scripts/android-emulator-ready-selftest.sh exercise every branch
# on a machine with no /dev/kvm.
readiness_verdict() {
  local boot_completed="$1" bootanim_state="$2" pm_path="$3" focus_line="$4"

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

main() {
  cd "${ROOT}" || exit 1
  mkdir -p "$(dirname "${READINESS_LOG}")"

  local deadline=$(( SECONDS + READY_DEADLINE_SECONDS ))
  local sample=0 consecutive=0 verdict=""

  {
    printf 'settle_samples=%s deadline_seconds=%s interval_seconds=%s\n' \
      "${READY_SETTLE_SAMPLES}" "${READY_DEADLINE_SECONDS}" "${READY_SAMPLE_INTERVAL_SECONDS}"
  } >> "${READINESS_LOG}"

  while (( SECONDS < deadline )); do
    sample=$(( sample + 1 ))
    verdict="$(readiness_verdict \
      "$(device_getprop sys.boot_completed)" \
      "$(device_getprop init.svc.bootanim)" \
      "$(device_package_manager)" \
      "$(device_current_focus)")"

    if [[ "${verdict}" == "ready" ]]; then
      consecutive=$(( consecutive + 1 ))
    else
      consecutive=0
    fi

    printf '%s sample=%s elapsed=%ss consecutive_ready=%s verdict=%s\n' \
      "$(date -u '+%H:%M:%S')" "${sample}" "${SECONDS}" "${consecutive}" "${verdict}" \
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
