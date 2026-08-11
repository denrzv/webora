#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}" || exit 1

mkdir -p artifacts/screenshots

set +e
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.webora.browser.visual.LiveSiteScreenshotTest 2>&1 | tee artifacts/instrumentation.txt
test_status=${PIPESTATUS[0]}

adb logcat -d > artifacts/logcat.txt || true

additional_output_root="app/build/outputs/connected_android_test_additional_output"
if [[ -d "$additional_output_root" ]]; then
  while IFS= read -r -d '' screenshot; do
    cp "$screenshot" artifacts/screenshots/
  done < <(find "$additional_output_root" -type f -path '*/screenshots/*.png' -print0)
fi

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
