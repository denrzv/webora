# BROWSE-002 tasklist

Status: TASKLIST_READY

## TASK-1 — Model browser state and resolve address input

- [x] Add sealed modes, immutable state transitions, and strict URL/search resolution with JVM tests.
- Acceptance: only safe HTTP(S)/search results are emitted; observed pages create only Regular state; Integrated requires trusted values.
- Tests: `./gradlew :app:testDebugUnitTest`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-2 — Add browser chrome, WebView controls, and back integration

- [x] Expose hardened WebView control/observation seams and build address/history/reload UI with activity back handling.
- Acceptance: controls reflect renderer observations; commands operate on the live WebView; back consumes renderer history first; hardening remains fixed.
- Tests: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-3 — Review, QA, validation, and closeout

- [x] Record review and QA evidence, update normative architecture notes and ROADMAP, and validate the workflow.
- Acceptance: review has no open findings, QA status is final, BROWSE-002 is marked complete, and validation succeeds.
- Tests: `bash scripts/pre-commit-check.sh` plus `/validate` checks.
- Status: complete

## TASK-FIX-1 — Delegate stale WebView back state safely

- Source: `/review` finding 1.
- [x] Consult live WebView history when back is dispatched and delegate to the activity dispatcher
  if a stale observation cannot be consumed; use callback URLs rather than a potentially stale
  `WebView.url` during navigation.
- Acceptance: predictive/system back cannot be swallowed by stale renderer state and page-start
  observations describe the destination callback supplied by WebView.
- Tests: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`,
  `bash scripts/pre-commit-check.sh`.
- Status: complete
