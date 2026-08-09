# SKIN-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/SKIN-001.prd.md`
- Research: `docs/research/SKIN-001.md`
- Plan: `docs/plan/SKIN-001.md`

## Tasks

## TASK-1 — Add the closed trusted SiteSkin theme projector

- [x] Add immutable `SiteSkinTheme` light/dark output and a six-role `SiteSkinColorScheme` that can
  be created only from `SiteSkinConfiguration`.
- [x] Map canonical trusted branding through deterministic defaults and dark-surface derivation,
  then guard every final body/UI foreground-container pair to WCAG AA before exposing it.
- [x] Add JVM tests for complete, partial, and absent branding; exact mapping; deterministic output;
  dark derivation; pair ratios; and matching hostile colours.
- [x] Run a negative control by bypassing the final body contrast guard, verify the named matching
  colour test fails, restore it, and record the result here.
- [x] Run focused/app tests, Detekt, debug APK assembly, and `bash scripts/pre-commit-check.sh`.

Negative-control result: returning the derived background without `guardContainer` failed
`matching manifest text and background are AA before exposure`; the guard was restored and the
focused suite passed.

## TASK-2 — Review, QA, validation, and closeout

- [x] Run `/review` and resolve every finding through `TASK-FIX-*` commits if needed; review found no
  issues requiring a fix task.
- [x] Produce a `QA_PASSED` report with focused and full-gate evidence.
- [x] Update shared architecture notes and mark `SKIN-001` complete in the roadmap.
- [x] Run `/validate` and `bash scripts/pre-commit-check.sh`; artifact statuses and task completion
  are valid and the local gate passes. Branch CI is unavailable because this managed checkout has
  no configured remote; no runtime instrumentation or screenshot applies to this pure, non-visible
  projector.
