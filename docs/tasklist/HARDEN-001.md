# HARDEN-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/HARDEN-001.prd.md`
- Research: `docs/research/HARDEN-001.md`
- Plan: `docs/plan/HARDEN-001.md`

## Tasks

## TASK-1 — Complete and enforce the adversarial matrix

- [x] Add the documented pre-tree 64-level JSON nesting bound with boundary, malformed-structure,
  string/escape, exact-byte-limit, and negative-control coverage.
- [x] Add portable fixtures and corpus assertions for deep nesting, all five denied schemes,
  duplicate first-wins behavior, and navigation/quick-action/menu limits in document order.
- [x] Add explicit same-origin redirect-loop and Unicode/punycode homograph matrix coverage without
  weakening exact origin binding or regular-mode fallback.
- [x] Run focused tests after every code change, then core/app unit tests, Detekt, and the full
  pre-commit gate.

Negative-control result: increasing only the parser's structural capacity to 65 made
`jsonNestingIsBoundedBeforeTreeParsing` fail; raising the quick-action limit to six made
`SecurityConformanceTest` fail against the first-five canonical fixture. Both protections were
restored and their focused suites passed. Existing explicit tests retain the corresponding
first-wins duplicate, five-scheme allow-list, redirect-budget, and Unicode/punycode controls.

## TASK-2 — Review, QA, validation, and closeout

- [x] Run `/review`; resolve every finding through a `TASK-FIX-*` commit if necessary.
- [x] Produce a `QA_PASSED` report with focused, adversarial, and full-gate evidence.
- [x] Update shared architecture notes and mark HARDEN-001 complete in the roadmap source.
- [x] Run `/validate` and the final pre-commit gate.
