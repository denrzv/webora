# /validate

Verify artifacts and gates are satisfied for the active ticket:

- PRD `PRD_READY`, research `RESEARCH_READY`, plan `PLAN_APPROVED`, tasklist `TASKLIST_READY`,
  QA `QA_PASSED`
- every task ticked or explicitly deferred with a reason
- `bash scripts/pre-commit-check.sh` green
- CI green on the branch
- `CLAUDE.md` updated if the change established a new architectural convention
