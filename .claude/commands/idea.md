# /idea

Initialize ticket artifacts and set the active ticket.

## Steps
1. `bash scripts/ensure-docs.sh <TICKET> "<Title>"`
2. Fill `docs/prd/<TICKET>.prd.md` from the concept and `docs/DEVELOPMENT_PLAN.md`.
3. Acceptance criteria are a numbered list of testable assertions. The last one is always the
   command gate: `` `bash scripts/pre-commit-check.sh` passes. ``
4. Set `Status: PRD_READY`.

## Outputs
- `docs/prd/<TICKET>.prd.md`, `docs/research/<TICKET>.md`, `docs/plan/<TICKET>.md`,
  `docs/tasklist/<TICKET>.md` (stubs)
- `docs/.active_ticket`

Next step is `/researcher <TICKET>`, not `/plan`.
