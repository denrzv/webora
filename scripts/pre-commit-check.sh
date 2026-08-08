#!/usr/bin/env bash
# Run before every commit. Also what CI runs.
#
# Note on detekt: it is invoked UNCONDITIONALLY and on purpose. The reference
# repo guarded it with `if ./gradlew -q tasks --all | grep -q detekt`, which
# silently no-opped for that project's entire history because the plugin was
# never wired. A gate you cannot tell apart from a passing build is not a gate.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
USER_BIN_DIR="${HOME}/.local/bin"
fail=0

# The shared bootstrap installs cloud-only guardrail binaries here. Add the path
# explicitly because cloud setup scripts run in a separate shell from the agent.
if [[ -d "${USER_BIN_DIR}" ]]; then
  export PATH="${USER_BIN_DIR}:${PATH}"
fi

run() {
  local name="$1"; shift
  echo "==> ${name}"
  if ! "$@"; then
    echo "[FAIL] ${name}" >&2
    fail=1
  fi
}

echo "[checks] Running Webora guardrails in ${ROOT}"

if command -v gitleaks >/dev/null 2>&1; then
  run "gitleaks (secret scan)" gitleaks detect --source "${ROOT}" --no-git --redact
else
  echo "[warn] gitleaks not found (CI runs it)."
fi

if command -v shellcheck >/dev/null 2>&1; then
  if compgen -G "${ROOT}/scripts/*.sh" > /dev/null; then
    # shellcheck disable=SC2046
    run "shellcheck scripts" shellcheck $(compgen -G "${ROOT}/scripts/*.sh")
  fi
else
  echo "[warn] shellcheck not found (CI runs it)."
fi

if [[ ! -x "${ROOT}/gradlew" ]]; then
  echo "[FAIL] gradlew not found or not executable at ${ROOT}/gradlew" >&2
  exit 1
fi

# :siteskin-core must build and test with no Android SDK present. Running it
# first means a leaked Android dependency fails fast and unmistakably.
run "siteskin-core tests (no Android SDK)" bash -c "cd '${ROOT}' && ANDROID_HOME= ANDROID_SDK_ROOT= ./gradlew --quiet :siteskin-core:test"

run "unit tests" bash -c "cd '${ROOT}' && ./gradlew --quiet test"
run "detekt" bash -c "cd '${ROOT}' && ./gradlew --quiet detekt"

if [[ "${fail}" -ne 0 ]]; then
  exit 1
fi
echo "[checks] OK"
