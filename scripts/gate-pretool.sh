#!/usr/bin/env bash
# PreToolUse hook wrapper. Silent when the gate passes; echoes the reason and
# blocks (exit 2) when it does not.
#
# Artifact directories are exempt: writing the PRD that satisfies the gate must
# not itself be blocked by the gate. Claude Code and Codex both pass tool
# payloads on stdin as JSON, but use different shapes. We only need to detect
# whether the requested edit targets an exempt repository path.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

payload=""
if [[ ! -t 0 ]]; then
  payload="$(cat || true)"
fi

# Exempt paths: AIDD artifacts, reports, spec text, and governance docs.
# Claude Edit/Write payloads expose a file_path. Codex apply_patch payloads
# carry patch text inside tool_input.command, so match repository paths anywhere
# in the JSON rather than depending on one provider-specific field name.
if grep -qE '(^|[/"[:space:]])(docs|reports|spec)/' <<<"${payload}"; then
  exit 0
fi

if "${ROOT}/scripts/gate-workflow.sh" >/dev/null 2>&1; then
  exit 0
fi

"${ROOT}/scripts/gate-workflow.sh" >&2 || true
exit 2
