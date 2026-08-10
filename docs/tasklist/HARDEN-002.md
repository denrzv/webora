# HARDEN-002: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/HARDEN-002.prd.md`
- Research: `docs/research/HARDEN-002.md`
- Plan: `docs/plan/HARDEN-002.md`

## Tasks

## TASK-1 — Harden identity presentation and exact-origin consent

- [x] Show the canonical full origin in first-use consent while retaining registrable domain/TLS
  as the non-suppressible active-chrome identity.
- [x] Make bounded logo content explicitly decorative and preserve independent browser-owned
  security semantics under hostile brand title, asset, and palette inputs.
- [x] Strengthen pure and Compose negative controls for pre-consent regular mode, stale and
  cross-origin candidates, exact scheme/host/port storage, consent copy/actions, logo bounds, and
  non-replaceable identity.
- [x] Run focused tests after each code change, compile instrumentation, execute available runtime
  tests, run Detekt, and pass the full pre-commit gate.

The existing `SiteSkinRuntimeTest` and `SiteConsentStoreTest` already name the pre-consent,
stale/cross-origin, and scheme/host/port negative controls, so they were reused rather than
duplicated. Removing the runtime origin guard makes `different origin cannot activate even with
allow` fail; the guard was restored. Compose controls now pin the exact visible consent origin,
browser-owned explanatory copy, decorative logo semantics, bounded logo width, and the dedicated
security description. Runtime instrumentation was unavailable because this checkout has no
connected Android device or `/dev/kvm`; its test sources were compiled instead.

## TASK-2 — Review, QA, validation, and closeout

- [x] Run `/review`; resolve every finding through a `TASK-FIX-*` commit if necessary.
- [x] Produce a `QA_PASSED` report with focused, negative-control, and full-gate evidence.
- [x] Update shared architecture notes and mark HARDEN-002 complete in the roadmap.
- [x] Run `/validate` and the final pre-commit gate.
