#!/usr/bin/env bash
# Exercises readiness_verdict without a device, so the emulator gate is testable on a
# machine that cannot run an emulator. Run unconditionally by scripts/pre-commit-check.sh:
# a self-test that runs only when someone remembers it is not a gate.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/android-emulator-ready.sh
source "${ROOT}/scripts/android-emulator-ready.sh"

# Shapes recorded from `adb shell dumpsys window` on an API 33 emulator. AOSP builds the
# error-dialog titles from the process name — AppNotRespondingDialog uses
# "Application Not Responding: " + processName — which is why they carry no translated text.
APP_FOCUS='mCurrentFocus=Window{3f2a1b u0 app.webora.browser.debug/app.webora.browser.MainActivity}'
SYSTEM_UI_ANR_FOCUS='mCurrentFocus=Window{9c41e7 u0 Application Not Responding: com.android.systemui}'
NULL_FOCUS='mCurrentFocus=null'

failures=0
checks=0

expect() {
  local name="$1" expected="$2"
  shift 2
  local actual
  actual="$(readiness_verdict "$@")"
  checks=$(( checks + 1 ))
  if [[ "${actual}" == "${expected}" ]]; then
    printf 'ok   %s\n' "${name}"
  else
    printf 'FAIL %s\n       expected: %s\n       actual:   %s\n' "${name}" "${expected}" "${actual}" >&2
    failures=$(( failures + 1 ))
  fi
}

expect_cpu_counters() {
  local name="$1" expected="$2" row="$3" actual
  actual="$(cpu_counters "${row}")"
  checks=$(( checks + 1 ))
  if [[ "${actual}" == "${expected}" ]]; then
    printf 'ok   %s\n' "${name}"
  else
    printf 'FAIL %s\n       expected: %s\n       actual:   %s\n' "${name}" "${expected}" "${actual}" >&2
    failures=$(( failures + 1 ))
  fi
}

expect_cpu_interval() {
  local name="$1" expected="$2"
  shift 2
  local actual
  actual="$(cpu_interval_verdict "$@")"
  checks=$(( checks + 1 ))
  if [[ "${actual}" == "${expected}" ]]; then
    printf 'ok   %s\n' "${name}"
  else
    printf 'FAIL %s\n       expected: %s\n       actual:   %s\n' "${name}" "${expected}" "${actual}" >&2
    failures=$(( failures + 1 ))
  fi
}

QUIET_CPU='cpu-quiet:25%<=50%'

expect "a settled device is ready" \
  "ready" "1" "1" "package:/system/framework/framework-res.apk" "${APP_FOCUS}" "${QUIET_CPU}"

expect "the boot broadcast has not fired" \
  "boot-incomplete:<empty>" "" "1" "package:/system/framework/framework-res.apk" "${APP_FOCUS}" "${QUIET_CPU}"

expect "the boot animation is still running" \
  "bootanim-running:running" "1" "running" "package:/system/framework/framework-res.apk" "${APP_FOCUS}" "${QUIET_CPU}"

# The regression from run 31512048008. `-no-boot-anim` means bootanim never runs, so its property is
# never set — and a gate that waits for a value the device will never produce fails every run,
# patiently, for its entire deadline. The condition is "not running", not "has finished".
expect "an emulator launched with -no-boot-anim never sets the property" \
  "ready" "1" "" "package:/system/framework/framework-res.apk" "${APP_FOCUS}" "${QUIET_CPU}"

expect "a stopped boot animation is ready" \
  "ready" "1" "stopped" "package:/system/framework/framework-res.apk" "${APP_FOCUS}" "${QUIET_CPU}"

expect "PackageManager is not answering yet" \
  "package-manager-silent:<empty>" "1" "1" "" "${APP_FOCUS}" "${QUIET_CPU}"

expect "an adb error instead of a package path is not readiness" \
  "package-manager-silent:Error: Unknown package: android" \
  "1" "1" "Error: Unknown package: android" "${APP_FOCUS}" "${QUIET_CPU}"

expect "nothing owns the display" \
  "no-focused-window:${NULL_FOCUS}" "1" "1" "package:/system/framework/framework-res.apk" "${NULL_FOCUS}" "${QUIET_CPU}"

expect "no mCurrentFocus line at all" \
  "no-focused-window:<empty>" "1" "1" "package:/system/framework/framework-res.apk" "" "${QUIET_CPU}"

# Deliberate, and the reason this file asserts it: readiness reports the focused window
# and does not judge it. A System UI ANR dialog is a *ready* device — the display is
# alive and something owns it. Whether that something may be photographed, dismissed or
# must fail the run is ScreenEvidencePolicy's decision, and duplicating it here would
# create a second copy free to disagree with the first.
expect "readiness does not classify the focused window" \
  "ready" "1" "stopped" "package:/system/framework/framework-res.apk" "${SYSTEM_UI_ANR_FOCUS}" "${QUIET_CPU}"

expect "a busy device is not ready" \
  "cpu-busy:51%>50%" "1" "stopped" "package:/system/framework/framework-res.apk" "${APP_FOCUS}" \
  "cpu-busy:51%>50%"
expect "the first CPU observation is only a baseline" \
  "cpu-baseline:first-observation" "1" "stopped" "package:/system/framework/framework-res.apk" \
  "${APP_FOCUS}" "cpu-baseline:first-observation"
expect "a missing CPU observation fails closed" \
  "cpu-invalid:missing-observation" "1" "stopped" "package:/system/framework/framework-res.apk" \
  "${APP_FOCUS}" ""

expect_cpu_counters "aggregate CPU counters are totalled" "36 15" "cpu  1 2 3 4 11 5 6 4"
expect_cpu_counters "guest time already included in user time is not double counted" \
  "36 15" "cpu 1 2 3 4 11 5 6 4 900 800"
expect_cpu_counters "a missing aggregate row is rejected" \
  "invalid:missing-aggregate-row" "cpu0 1 2 3 4"
expect_cpu_counters "a malformed counter is rejected" \
  "invalid:non-numeric-counter" "cpu 1 2 nope 4"

expect_cpu_interval "an exact-threshold interval is quiet" \
  "cpu-quiet:50%<=50%" 100 50 200 100 50
expect_cpu_interval "a slightly busy interval rounds up and is rejected" \
  "cpu-busy:51%>50%" 100 50 201 100 50
expect_cpu_interval "a quiet interval is accepted" \
  "cpu-quiet:25%<=50%" 100 50 200 125 50
expect_cpu_interval "a counter reset is rejected" \
  "cpu-invalid:non-monotonic-counter" 200 125 100 50 50
expect_cpu_interval "a zero-length interval is rejected" \
  "cpu-invalid:non-monotonic-counter" 100 50 100 50 50
expect_cpu_interval "a malformed interval is rejected" \
  "cpu-invalid:non-numeric-counter" 100 50 bad 125 50

if (( checks < 23 )); then
  printf 'FAIL only %s checks ran; the self-test lost coverage\n' "${checks}" >&2
  exit 1
fi

if (( failures > 0 )); then
  printf '%s of %s readiness checks failed\n' "${failures}" "${checks}" >&2
  exit 1
fi

printf 'all %s readiness checks passed\n' "${checks}"
