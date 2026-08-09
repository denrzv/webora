# CORE-006: Tasklist
Status: TASKLIST_READY

## TASK-1 — Deterministic navigation matcher

- Add a pure-JVM `NavMatcher` consuming trusted ordered navigation items and a browser-observed URL.
- Extract only hierarchical HTTP(S) URL paths and fail closed for malformed/unsupported input.
- Implement exact precedence, longest literal-prefix glob precedence, and document-order ties.
- Implement segment-bounded `*` and whole-segment `**` without compiling manifest regex.
- Add focused tests for grammar, ranking, URL boundaries, literal metacharacters, no-selection
  fallback, and adversarial wildcard inputs.
- Tests: `NavMatcherTest`, `:siteskin-core:test`, `:siteskin-core:check`, and the pre-commit gate.
- Acceptance: all PRD criteria pass; the API remains pure JVM and returns at most one trusted item.
- Negative control: temporarily bypassing restricted glob evaluation for every star-bearing pattern
  made `NavMatcherTest.single star stays within one segment and may match nothing` fail at its
  cross-segment assertion (`./gradlew ...`, exit 1); the bounded matcher was restored.
- Status: complete

References:
- PRD: `docs/prd/CORE-006.prd.md`
- Plan: `docs/plan/CORE-006.md`

## Tasks
- [ ] TASK-1: ...
  - New: `path`
  - Acceptance:
  - Tests:
