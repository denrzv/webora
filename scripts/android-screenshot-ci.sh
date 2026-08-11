#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

mkdir -p artifacts/screenshots

set +e
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.webora.browser.visual.LiveSiteScreenshotTest 2>&1 | tee artifacts/instrumentation.txt
test_status=${PIPESTATUS[0]}

adb logcat -d > artifacts/logcat.txt || true
adb pull /sdcard/Android/data/app.webora.browser.debug/files/screenshots/. artifacts/screenshots/ || true

png_count=$(find artifacts/screenshots -maxdepth 1 -type f -name '*.png' | wc -l | tr -d ' ')
printf 'test_status=%s\npng_count=%s\n' "$test_status" "$png_count" | tee artifacts/result.txt

if [[ "$png_count" -eq 0 ]]; then
  echo "No screenshots were produced" >&2
  exit 1
fi

exit "$test_status"
