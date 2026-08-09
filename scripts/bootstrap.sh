#!/usr/bin/env bash
# Shared development bootstrap for cloud agents and local Linux/macOS shells.
#
# Claude Code calls this through scripts/session-start.sh (best-effort).
# Codex Cloud should call this script directly from its environment setup.
#
# This script is intentionally provider-neutral, idempotent, and strict: callers
# that require a usable development toolchain get a non-zero exit code on failure.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ANDROID_SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/android-sdk}}"
CMDLINE_TOOLS_VERSION="${WEBORA_CMDLINE_TOOLS_VERSION:-13114758}" # cmdline-tools 17.0
COMPILE_SDK="${WEBORA_COMPILE_SDK:-36}"
BUILD_TOOLS="${WEBORA_BUILD_TOOLS:-36.0.0}"
GITLEAKS_VERSION="${WEBORA_GITLEAKS_VERSION:-8.29.1}"
SHELLCHECK_VERSION="${WEBORA_SHELLCHECK_VERSION:-0.11.0}"
PREPARE_GRADLE="${WEBORA_BOOTSTRAP_PREPARE_GRADLE:-0}"
PERSIST_PATH="${WEBORA_BOOTSTRAP_PERSIST_PATH:-0}"
USER_BIN_DIR="${HOME}/.local/bin"

log() { echo "[bootstrap] $*"; }
fail() { log "ERROR: $*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

host_os() {
  case "$(uname -s)" in
    Linux) echo "linux" ;;
    Darwin) echo "darwin" ;;
    *) fail "unsupported host OS: $(uname -s)" ;;
  esac
}

host_arch() {
  case "$(uname -m)" in
    x86_64|amd64) echo "x86_64" ;;
    arm64|aarch64) echo "aarch64" ;;
    *) fail "unsupported host architecture: $(uname -m)" ;;
  esac
}

ensure_user_bin_path() {
  mkdir -p "${USER_BIN_DIR}"
  case ":${PATH}:" in
    *":${USER_BIN_DIR}:"*) ;;
    *) export PATH="${USER_BIN_DIR}:${PATH}" ;;
  esac
}

installed_gitleaks_version() {
  command -v gitleaks >/dev/null 2>&1 || return 1
  gitleaks version 2>/dev/null | head -n1 | sed 's/^v//'
}

installed_shellcheck_version() {
  command -v shellcheck >/dev/null 2>&1 || return 1
  shellcheck --version 2>/dev/null | awk '/^version:/ {print $2}'
}

install_gitleaks() {
  local installed=""
  installed="$(installed_gitleaks_version || true)"
  if [[ "${installed}" == "${GITLEAKS_VERSION}" ]]; then
    return 0
  fi

  require_cmd curl
  require_cmd tar

  local os arch asset url tmpdir archive
  os="$(host_os)"
  case "$(host_arch)" in
    x86_64) arch="x64" ;;
    aarch64) arch="arm64" ;;
  esac

  asset="gitleaks_${GITLEAKS_VERSION}_${os}_${arch}.tar.gz"
  url="https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/${asset}"
  tmpdir="$(mktemp -d)"
  archive="${tmpdir}/${asset}"

  if [[ -n "${installed}" ]]; then
    log "Replacing Gitleaks ${installed} with ${GITLEAKS_VERSION}"
  else
    log "Installing Gitleaks ${GITLEAKS_VERSION}"
  fi
  curl -fsSL "${url}" -o "${archive}"
  tar -xzf "${archive}" -C "${tmpdir}"
  [[ -f "${tmpdir}/gitleaks" ]] || {
    rm -rf "${tmpdir}"
    fail "gitleaks executable missing from downloaded archive"
  }
  cp "${tmpdir}/gitleaks" "${USER_BIN_DIR}/gitleaks"
  chmod 0755 "${USER_BIN_DIR}/gitleaks"
  rm -rf "${tmpdir}"
}

install_shellcheck() {
  local installed=""
  installed="$(installed_shellcheck_version || true)"
  if [[ "${installed}" == "${SHELLCHECK_VERSION}" ]]; then
    return 0
  fi

  require_cmd curl
  require_cmd tar

  local os arch asset url tmpdir archive binary
  os="$(host_os)"
  arch="$(host_arch)"
  asset="shellcheck-v${SHELLCHECK_VERSION}.${os}.${arch}.tar.gz"
  url="https://github.com/koalaman/shellcheck/releases/download/v${SHELLCHECK_VERSION}/${asset}"
  tmpdir="$(mktemp -d)"
  archive="${tmpdir}/${asset}"
  binary="${tmpdir}/shellcheck-v${SHELLCHECK_VERSION}/shellcheck"

  if [[ -n "${installed}" ]]; then
    log "Replacing ShellCheck ${installed} with ${SHELLCHECK_VERSION}"
  else
    log "Installing ShellCheck ${SHELLCHECK_VERSION}"
  fi
  curl -fsSL "${url}" -o "${archive}"
  tar -xzf "${archive}" -C "${tmpdir}"
  [[ -f "${binary}" ]] || {
    rm -rf "${tmpdir}"
    fail "shellcheck executable missing from downloaded archive"
  }
  cp "${binary}" "${USER_BIN_DIR}/shellcheck"
  chmod 0755 "${USER_BIN_DIR}/shellcheck"
  rm -rf "${tmpdir}"
}

provision_guardrail_tools() {
  ensure_user_bin_path
  install_gitleaks
  install_shellcheck
  log "guardrail tools ready: gitleaks $(installed_gitleaks_version), shellcheck $(installed_shellcheck_version)"
}

install_cmdline_tools() {
  local sdkmanager="${ANDROID_SDK_DIR}/cmdline-tools/latest/bin/sdkmanager"
  [[ -x "${sdkmanager}" ]] && return 0

  require_cmd curl
  require_cmd unzip

  local platform tmpdir zip url
  case "$(host_os)" in
    linux) platform="linux" ;;
    darwin) platform="mac" ;;
  esac
  tmpdir="$(mktemp -d)"
  zip="${tmpdir}/cmdline-tools.zip"
  url="https://dl.google.com/android/repository/commandlinetools-${platform}-${CMDLINE_TOOLS_VERSION}_latest.zip"

  log "Installing Android command-line tools ${CMDLINE_TOOLS_VERSION}"
  mkdir -p "${ANDROID_SDK_DIR}/cmdline-tools"
  curl -fsSL "${url}" -o "${zip}"
  unzip -q "${zip}" -d "${tmpdir}/unpacked"

  [[ -x "${tmpdir}/unpacked/cmdline-tools/bin/sdkmanager" ]] || {
    rm -rf "${tmpdir}"
    fail "sdkmanager missing from downloaded command-line tools"
  }

  rm -rf "${ANDROID_SDK_DIR}/cmdline-tools/latest"
  mv "${tmpdir}/unpacked/cmdline-tools" "${ANDROID_SDK_DIR}/cmdline-tools/latest"
  rm -rf "${tmpdir}"
}

provision_android_sdk() {
  install_cmdline_tools

  local sdkmanager="${ANDROID_SDK_DIR}/cmdline-tools/latest/bin/sdkmanager"
  local -a packages=()

  [[ -d "${ANDROID_SDK_DIR}/platform-tools" ]] || packages+=("platform-tools")
  [[ -d "${ANDROID_SDK_DIR}/platforms/android-${COMPILE_SDK}" ]] || packages+=("platforms;android-${COMPILE_SDK}")
  [[ -d "${ANDROID_SDK_DIR}/build-tools/${BUILD_TOOLS}" ]] || packages+=("build-tools;${BUILD_TOOLS}")

  if (( ${#packages[@]} == 0 )); then
    log "Android SDK ${COMPILE_SDK} already present at ${ANDROID_SDK_DIR}"
    return 0
  fi

  log "Provisioning Android SDK packages into ${ANDROID_SDK_DIR}: ${packages[*]}"
  yes | "${sdkmanager}" --sdk_root="${ANDROID_SDK_DIR}" --licenses >/dev/null 2>&1 || true
  "${sdkmanager}" --sdk_root="${ANDROID_SDK_DIR}" "${packages[@]}" >/dev/null
  log "Android SDK ready"
}

expose_adb() {
  ensure_user_bin_path

  local adb="${ANDROID_SDK_DIR}/platform-tools/adb"
  [[ -x "${adb}" ]] || fail "adb missing after Android SDK provisioning: ${adb}"

  ln -sf "${adb}" "${USER_BIN_DIR}/adb"
  log "adb ready at ${USER_BIN_DIR}/adb"
}

persist_tool_path() {
  [[ "${PERSIST_PATH}" == "1" ]] || return 0

  local bashrc="${HOME}/.bashrc"
  local path_line='export PATH="$HOME/.local/bin:$PATH"'
  path_line='export PATH="$HOME/.local/bin:$PATH"'

  touch "${bashrc}"
  if ! grep -Fqx "${path_line}" "${bashrc}"; then
    printf '\n# Webora bootstrap tools\n%s\n' "${path_line}" >> "${bashrc}"
  fi
  log "persisted Webora tool PATH in ${bashrc}"
}

write_local_properties() {
  local lp="${ROOT}/local.properties"
  local tmp
  tmp="$(mktemp)"

  # Preserve developer-local values (for example signing properties) and only
  # replace sdk.dir. The old SessionStart script overwrote the whole file.
  if [[ -f "${lp}" ]]; then
    grep -v '^sdk\.dir=' "${lp}" > "${tmp}" || true
  fi
  printf 'sdk.dir=%s\n' "${ANDROID_SDK_DIR}" >> "${tmp}"
  mv "${tmp}" "${lp}"
  log "configured sdk.dir in ${lp}"
}

report_java() {
  require_cmd java
  local version
  version="$(java -version 2>&1 | head -n1 || true)"
  log "java: ${version:-unknown}"
}

prepare_gradle() {
  [[ "${PREPARE_GRADLE}" == "1" ]] || return 0
  [[ -x "${ROOT}/gradlew" ]] || fail "gradlew not found or not executable"

  # This intentionally runs only a configuration task. It downloads the Gradle
  # distribution and build plugins while setup-phase internet is available,
  # without making cloud environment creation depend on the current test state.
  log "Preparing Gradle wrapper and project configuration"
  (cd "${ROOT}" && ./gradlew --no-daemon --quiet help >/dev/null)
  log "Gradle ready"
}

report_java
provision_guardrail_tools
provision_android_sdk
expose_adb
persist_tool_path
write_local_properties
prepare_gradle
log "bootstrap complete"
