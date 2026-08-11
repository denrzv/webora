#!/usr/bin/env bash
# Runs inside the emulator step of .github/workflows/android-screenshots.yml.
#
# It installs and runs; it does not build. The workflow assembles both APKs in an
# ordinary step BEFORE the emulator is launched, because the first green screenshot
# run (31491580516) spent 8m39s executing 72 Gradle tasks while a freshly booted
# emulator sat on the same 4-vCPU runner, and Android's ANR timers are wall-clock.
# The `System UI isn't responding` dialog over those screenshots was the predictable
# result of that arrangement, not an emulator defect.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SCREENSHOT_TEST_CLASS="app.webora.browser.visual.LiveSiteScreenshotTest"

# Both APKs must already exist. Without this check a regressed pre-build step is
# invisible: `connectedDebugAndroidTest` would simply build them again, inside the
# emulator step, restoring the contention this whole ticket removes — and it would
# still go green.
require_prebuilt_apks() {
  local apk missing=0
  for apk in "${DEBUG_APK}" "${TEST_APK}"; do
    if [[ -f "${apk}" ]]; then
      printf 'prebuilt %s (%s bytes)\n' "${apk}" "$(wc -c < "${apk}" | tr -d ' ')"
    else
      printf 'MISSING prebuilt APK: %s\n' "${apk}"
      missing=1
    fi
  done
  return "${missing}"
}

main() {
  cd "${ROOT}" || exit 1
  mkdir -p artifacts/screenshots

  if ! require_prebuilt_apks > artifacts/prebuilt-apks.txt 2>&1; then
    cat artifacts/prebuilt-apks.txt >&2
    echo "The workflow must assemble :app:assembleDebug and :app:assembleDebugAndroidTest" >&2
    echo "before launching the emulator. Building here would starve a booting system_server." >&2
    exit 1
  fi
  cat artifacts/prebuilt-apks.txt

  # The emulator action returns once `sys.boot_completed=1`. That is where the screenshots
  # used to start going wrong, so the run does not proceed on it alone.
  if ! bash scripts/android-emulator-ready.sh; then
    adb logcat -d > artifacts/logcat.txt || true
    echo "The emulator never settled; see artifacts/readiness.txt for every sample taken." >&2
    exit 1
  fi

  set +e
  ./gradlew :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=${SCREENSHOT_TEST_CLASS}" 2>&1 \
    | tee artifacts/instrumentation.txt
  local test_status=${PIPESTATUS[0]}

  adb logcat -d > artifacts/logcat.txt || true

  local additional_output_root="app/build/outputs/connected_android_test_additional_output"
  if [[ -d "$additional_output_root" ]]; then
    while IFS= read -r -d '' screenshot; do
      cp "$screenshot" artifacts/screenshots/
    done < <(find "$additional_output_root" -type f -path '*/screenshots/*.png' -print0)
  fi

  local png_count
  png_count=$(find artifacts/screenshots -maxdepth 1 -type f -name '*.png' | wc -l | tr -d ' ')
  printf 'test_status=%s\npng_count=%s\n' "$test_status" "$png_count" | tee artifacts/result.txt

  if [[ "$png_count" -eq 0 ]]; then
    echo "No screenshots were collected from Android test storage" >&2
    if [[ -d "$additional_output_root" ]]; then
      echo "Collected test output tree:" >&2
      find "$additional_output_root" -maxdepth 6 -type f -print >&2 || true
    else
      echo "Android test additional-output directory does not exist: $additional_output_root" >&2
    fi
    exit 1
  fi

  exit "$test_status"
}

# Sourcing this file defines the functions and runs nothing, so the precondition can
# be exercised without an emulator.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
