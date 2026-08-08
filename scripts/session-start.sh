#!/usr/bin/env bash
# Claude Code SessionStart adapter.
#
# The actual toolchain provisioning is provider-neutral and lives in
# scripts/bootstrap.sh so the same logic can be reused by Codex Cloud.
#
# SessionStart remains deliberately non-fatal: core-only work must still be
# possible if Android SDK provisioning is temporarily unavailable.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log() { echo "[session-start] $*"; }

if bash "${ROOT}/scripts/bootstrap.sh"; then
  log "development environment ready"
else
  status=$?
  log "bootstrap failed with exit ${status}; ':app' tasks may fail, ':siteskin-core' remains usable"
fi

exit 0
