# SKIN-002: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/SKIN-002.prd.md`
- Research: `docs/research/SKIN-002.md`
- Plan: `docs/plan/SKIN-002.md`

## Tasks

## TASK-1 — Add the browser-owned SiteSkin top bar

- [x] Add a pure immutable top-bar model created from trusted configuration, existing brand asset,
  projected colour scheme, and mandatory browser-observed security presentation.
- [x] Render trusted branding in a fixed logo slot beside a weighted text region, with an
  unconditional domain/TLS security row and independent accessibility semantics.
- [x] Add JVM negative-control coverage for identity preservation and Android Compose coverage for
  security semantics and bounded logo geometry.
- [x] Run the focused JVM test after each code change, compile instrumentation tests, run app unit
  tests, Detekt, debug APK assembly, and `bash scripts/pre-commit-check.sh`.

Negative-control result: replacing the factory's browser-observed `SecurityPresentation` with a
brand-controlled constant failed `browser observed identity is structurally preserved independent
of branding`; the required value was restored and the focused suite passed.

## TASK-2 — Review, QA, validation, and closeout

- [x] Run `/review` and resolve every finding through `TASK-FIX-*` commits if needed; two related
  accessibility findings were resolved by `TASK-FIX-1`.
- [x] Produce a `QA_PASSED` report with focused and full-gate evidence.
- [x] Update shared architecture notes and mark `SKIN-002` complete in the roadmap.
- [x] Run `/validate` and `bash scripts/pre-commit-check.sh`; record managed-cloud device/remote
  limitations without weakening the required compile and local test gates.

## TASK-FIX-1 — Preserve text contrast and large-font layout

- Source: `/review` findings 1–2
- [x] Render normal-size SiteSkin text using the theme's 4.5:1 body pair rather than its 3:1 UI
  control pair.
- [x] Let the bar grow beyond its baseline height so scaled title, subtitle, and security lines do
  not overlap or clip.
- [x] Run focused model tests, compile instrumentation tests, and pass the full pre-commit gate.
