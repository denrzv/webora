# BROWSE-004 tasklist

Status: TASKLIST_READY

## TASK-1 — Model secure identity and main-frame failures

- [x] Add browser-owned security presentation and pure main-frame load-failure state transitions with focused JVM tests.
- Acceptance: identity comes only from committed HTTPS origins; unsafe/malformed destinations fail closed; main-frame failures are bounded and successful starts clear them.
- Tests: `./gradlew :app:testDebugUnitTest :app:lintDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-2 — Build regular chrome and recovery UI

- [x] Wire main-frame WebView errors into state and add accessible regular chrome, overflow actions, and Retry/Home error recovery.
- Acceptance: subresource errors cannot replace the page; security and overflow affordances remain browser-owned; Android tests compile.
- Tests: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-3 — Review, QA, validation, and closeout

- [x] Record review and QA evidence, update normative architecture notes and ROADMAP, and validate the workflow.
- Acceptance: review has no open findings, QA status is final, BROWSE-004 is marked complete, and validation succeeds.
- Tests: `bash scripts/pre-commit-check.sh` plus `/validate` checks.
- Status: complete

## TASK-FIX-1 — Keep subresource TLS failures out of page state

- [x] Cancel SSL errors without emitting a main-frame failure because the legacy callback does not identify the main frame.
- Source: `/review` subresource isolation finding.
- Acceptance: only `WebResourceRequest.isForMainFrame` errors can replace rendered content; TLS handshakes remain fail closed.
- Tests: `./gradlew :app:testDebugUnitTest :app:lintDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-FIX-2 — Preserve main-frame failure recovery at runtime

- [x] Reduce every WebView observation against BrowserScreen's latest state, keep the attached WebView alive behind browser-owned recovery UI, and preserve a main-frame failure across later completion/history callbacks until a successful new navigation starts.
- Source: Local runtime validation on the Pixel 6 API 33 emulator after completion of BROWSE-001 through BROWSE-005.
- Acceptance: failed safe HTTP(S) main-frame navigation shows Webora's recovery UI instead of Chromium's error page; Retry uses the live controller, Home returns Home, successful new navigation clears the failure, and subresource errors remain unable to replace the page.
- Tests: focused JVM/unit regression tests, `./gradlew :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest`, manual `https://127.0.0.1:44444/` Retry/Home verification with screenshot and crash/ANR logcat inspection, `bash scripts/pre-commit-check.sh`.
- Results: the new loading/history regression failed before the fix and passed after it; all app unit tests, lint, debug assembly, and both connected API 33 instrumentation tests passed. Manual Pixel 6 API 33 reproduction showed Webora recovery UI, Retry and Home worked, [screenshot evidence](../../reports/qa/BROWSE-004-runtime-recovery.png) was captured, and logcat contained no crash or ANR markers.
- Status: complete
