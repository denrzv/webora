# SKIN-004: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/SKIN-004.prd.md`
- Research: `docs/research/SKIN-004.md`
- Plan: `docs/plan/SKIN-004.md`

## Tasks

## TASK-1 — Wire consent-aware origin-bound SiteSkin runtime

- [x] Add pure runtime transitions and attributed discovery outcomes that retain same-origin skins,
  synchronously drop cross-origin skins, reject stale origin/generation results, and support an
  independently accepted destination skin.
- [x] Persist the closed Allow/Never decisions by exact canonical origin, model Not now as ephemeral,
  and add browser-owned first-use consent UI.
- [x] Compose trusted SiteSkin top/navigation/action surfaces in the live browser with cancellable
  brand assets, immutable domain/TLS identity, and closed `ActionResolver` effect dispatch.
- [x] Add JVM coverage for exact-origin persistence and all activation/retention/drop/swap/consent/
  stale-result transitions, with origin and generation negative controls; add relevant Compose
  instrumentation coverage and compile it.
- [x] Run focused tests after every code change, app unit tests, instrumentation compilation, Detekt,
  debug APK assembly, and `bash scripts/pre-commit-check.sh`.

Negative-control result: independently removing the exact-origin comparison and the navigation-
generation comparison caused their focused runtime tests to fail; both protections were restored
and the suite passed. No Android device is connected in managed cloud, so runtime instrumentation
and a screenshot are unavailable; Android test sources compile successfully.

## TASK-2 — Review, QA, validation, and closeout

- [x] Run `/review` and resolve every finding through `TASK-FIX-*` commits if needed.
- [x] Produce a `QA_PASSED` report with focused and full-gate evidence.
- [x] Update shared architecture notes and mark `SKIN-004` complete in the roadmap.
- [x] Run `/validate` and `bash scripts/pre-commit-check.sh`; record device/runtime limitations
  without weakening compilation and local test gates.

Validation confirmed every workflow status, completed task, review resolution, QA result, shared
architecture note, roadmap state, and local gate. The managed-cloud checkout has no push-capable
remote, so branch CI cannot be queried; the platform PR/export is the durable handoff mechanism.

## TASK-FIX-1 — Unify SiteSkin effect dispatch and confirm external HTTPS

- Source: `/review` finding 1
- [x] Route trusted selections from bottom navigation, quick actions, and the SiteSkin menu through
  the same exhaustive `ResolvedAction` dispatcher.
- [x] Require browser-owned confirmation before dispatching an external HTTPS URL and cover the
  closed confirmation UI in compiled Compose instrumentation.
- [x] Run app unit tests, compile instrumentation, and pass the full pre-commit gate.

The full app unit suite initially hit the pre-existing timing-sensitive OkHttp cancellation test;
its focused rerun passed, followed by a green full pre-commit gate.
