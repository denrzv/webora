#!/usr/bin/env bash
# SessionStart hook: provision the toolchain a fresh container lacks.
#
# Cloud/web sessions start from a bare image with a JDK but no Android SDK, so
# `./gradlew :app:assembleDebug` fails on a missing sdk.dir. :siteskin-core is
# deliberately Android-free and needs none of this — it builds either way, which
# is why core work is never blocked on this script succeeding.
#
# Idempotent and non-fatal: a failure here must not abort the session.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ANDROID_SDK_DIR="${ANDROID_SDK_ROOT:-${HOME}/android-sdk}"
CMDLINE_TOOLS_VERSION="13114758"   # cmdline-tools 17.0
COMPILE_SDK="36"
BUILD_TOOLS="36.0.0"

log() { echo "[session-start] $*"; }

provision_sdk() {
  if [[ -d "${ANDROID_SDK_DIR}/platforms/android-${COMPILE_SDK}" ]]; then
    log "Android SDK ${COMPILE_SDK} already present at ${ANDROID_SDK_DIR}"
    return 0
  fi

  log "Provisioning Android SDK ${COMPILE_SDK} into ${ANDROID_SDK_DIR}"
  mkdir -p "${ANDROID_SDK_DIR}/cmdline-tools" || return 1

  local zip="/tmp/cmdline-tools.zip"
  local url="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

  if [[ ! -x "${ANDROID_SDK_DIR}/cmdline-tools/latest/bin/sdkmanager" ]]; then
    curl -fsSL "${url}" -o "${zip}" || { log "download failed"; return 1; }
    unzip -q -o "${zip}" -d "${ANDROID_SDK_DIR}/cmdline-tools" || return 1
    rm -f "${zip}"
    mv "${ANDROID_SDK_DIR}/cmdline-tools/cmdline-tools" \
       "${ANDROID_SDK_DIR}/cmdline-tools/latest" 2>/dev/null || true
  fi

  local sdkmanager="${ANDROID_SDK_DIR}/cmdline-tools/latest/bin/sdkmanager"
  [[ -x "${sdkmanager}" ]] || { log "sdkmanager missing after unzip"; return 1; }

  yes | "${sdkmanager}" --licenses >/dev/null 2>&1 || true
  "${sdkmanager}" \
    "platform-tools" \
    "platforms;android-${COMPILE_SDK}" \
    "build-tools;${BUILD_TOOLS}" >/dev/null 2>&1 || { log "sdkmanager install failed"; return 1; }

  log "Android SDK ready"
}

write_local_properties() {
  local lp="${ROOT}/local.properties"
  # local.properties is gitignored; regenerate rather than edit in place.
  if [[ -d "${ANDROID_SDK_DIR}/platforms" ]]; then
    echo "sdk.dir=${ANDROID_SDK_DIR}" > "${lp}"
    log "wrote ${lp}"
  fi
}

# Java 25 is the toolchain target. If the container's JDK is older, Gradle's
# foojay resolver downloads 25 on first build; we do not fight it here.
report_java() {
  local v
  v="$(java -version 2>&1 | head -n1 || echo unknown)"
  log "java: ${v}"
}

report_java
if provision_sdk; then
  write_local_properties
else
  log "Android SDK unavailable — ':app' tasks will fail, ':siteskin-core' is unaffected."
fi

exit 0
