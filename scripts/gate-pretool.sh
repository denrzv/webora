#!/usr/bin/env bash
# PreToolUse hook wrapper. Silent when the gate passes; echoes the reason and
# blocks (exit 2) when it does not.
#
# Artifact directories are exempt: writing the PRD that satisfies the gate must
# not itself be blocked by the gate. Claude Code passes the tool payload on
# stdin as JSON; we look for the target path in it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

payload=""
if [[ ! -t 0 ]]; then
  payload="$(cat || true)"
fi

# Exempt paths: AIDD artifacts, reports, spec text, and the governance docs.
if grep -qE '"file_path"[[:space:]]*:[[:space:]]*"[^"]*/(docs|reports|spec)/' <<<"${payload}"; then
  exit 0
fi

if "${ROOT}/scripts/gate-workflow.sh" >/dev/null 2>&1; then
  exit 0
fi

"${ROOT}/scripts/gate-workflow.sh" >&2 || true
exit 2
