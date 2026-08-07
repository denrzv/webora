#!/usr/bin/env bash
# Dependency vulnerability scan. Best-effort locally, authoritative in CI.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

if command -v osv-scanner >/dev/null 2>&1; then
  echo "==> osv-scanner"
  osv-scanner scan --recursive .
else
  echo "[warn] osv-scanner not found. CI runs it; install from"
  echo "       https://github.com/google/osv-scanner/releases to run locally."
fi
