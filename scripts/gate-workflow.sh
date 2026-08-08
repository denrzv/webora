#!/usr/bin/env bash
# Blocks implementation edits until the active ticket's AIDD artifacts are ready.
# Exit 0 = allowed, exit 2 = blocked.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

ACTIVE="docs/.active_ticket"

if [[ ! -f "${ACTIVE}" ]]; then
  echo "[GATE] Missing ${ACTIVE}. Run /idea <TICKET> \"Title\" first." >&2
  exit 2
fi

TICKET="$(head -n1 "${ACTIVE}" | tr -d '[:space:]')"

if [[ -z "${TICKET}" ]]; then
  echo "[GATE] ${ACTIVE} is empty. Run /idea <TICKET> \"Title\" first." >&2
  exit 2
fi

# BOOTSTRAP is the escape hatch used while the repo itself is being scaffolded,
# before any ticket exists. Remove it from .active_ticket once FOUND-* lands.
if [[ "${TICKET}" == "BOOTSTRAP" ]]; then
  echo "[GATE] BOOTSTRAP mode — artifact gate bypassed."
  exit 0
fi

PRD="docs/prd/${TICKET}.prd.md"
RESEARCH="docs/research/${TICKET}.md"
PLAN="docs/plan/${TICKET}.md"
TASKS="docs/tasklist/${TICKET}.md"

fail=0

check_status() {
  local file="$1" want="$2" label="$3"
  if [[ ! -f "${file}" ]]; then
    echo "[GATE] Missing ${label} file: ${file}" >&2
    fail=1
    return
  fi
  if ! grep -E "^Status:[[:space:]]*${want}[[:space:]]*$" -m1 "${file}" >/dev/null 2>&1; then
    echo "[GATE] ${label} is not ready. Set 'Status: ${want}' in ${file}." >&2
    fail=1
  fi
}

check_status "${PRD}"      "PRD_READY"       "PRD"
check_status "${RESEARCH}" "RESEARCH_READY"  "Research"
check_status "${PLAN}"     "PLAN_APPROVED"   "Plan"
check_status "${TASKS}"    "TASKLIST_READY"  "Tasklist"

if [[ "${fail}" -ne 0 ]]; then
  exit 2
fi

echo "[GATE] OK for ${TICKET}"
