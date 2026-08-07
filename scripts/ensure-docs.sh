#!/usr/bin/env bash
# Instantiate AIDD artifacts for a ticket and make it active. Called by /idea.
# Usage: scripts/ensure-docs.sh <TICKET> "<Title>"
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

TICKET="${1:-}"
TITLE="${2:-}"

if [[ -z "${TICKET}" ]]; then
  echo "usage: scripts/ensure-docs.sh <TICKET> \"<Title>\"" >&2
  exit 1
fi

if ! [[ "${TICKET}" =~ ^[A-Z][A-Z0-9]*(-[A-Z0-9]+)*-[0-9]{3}$ ]]; then
  echo "[ensure-docs] '${TICKET}' does not match <DOMAIN>-<NNN> (e.g. CORE-001, HTTP-DEV-001)." >&2
  exit 1
fi

mkdir -p docs/prd docs/plan docs/tasklist docs/research docs/adr reports/qa reports/review reports/security

echo "${TICKET}" > docs/.active_ticket

instantiate() {
  local template="$1" target="$2"
  if [[ -f "${target}" ]]; then
    echo "[ensure-docs] keep ${target}"
    return
  fi
  sed "s/\$TICKET/${TICKET}/g; s/\$TITLE/${TITLE}/g" "${template}" > "${target}"
  echo "[ensure-docs] new  ${target}"
}

instantiate docs/prd.template.md      "docs/prd/${TICKET}.prd.md"
instantiate docs/plan.template.md     "docs/plan/${TICKET}.md"
instantiate docs/tasklist.template.md "docs/tasklist/${TICKET}.md"

echo "[ensure-docs] active ticket is now ${TICKET}"
