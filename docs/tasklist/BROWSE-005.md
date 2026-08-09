# BROWSE-005 tasklist

Status: TASKLIST_READY

## TASK-1 — Define safe navigation and transfer policy

- [x] Add closed external-navigation, HTTP(S) download, MIME normalization, and selected-upload policies; emit safe top-level external requests from the WebView client.
- Acceptance: arbitrary schemes and malformed inputs fail closed; supported external requests are inert data; download and upload policy is bounded and covered by negative controls.
- Tests: `./gradlew :app:testDebugUnitTest :app:lintDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-2 — Wire browser-owned Android capability flows

- [x] Add explicit confirmation, typed external intents, DownloadManager, and SAF file chooser wiring with accessible browser-owned feedback.
- Acceptance: no external app launches before confirmation; absent handlers/cancellation are safe; downloads use browser-owned settings; upload callbacks resolve exactly once; Android tests compile.
- Tests: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-3 — Review, QA, validation, and closeout

- [x] Record review and QA evidence, update normative architecture notes and ROADMAP, and validate the workflow.
- Acceptance: review has no open findings, QA status is final, BROWSE-005 is marked complete, and validation succeeds.
- Tests: `bash scripts/pre-commit-check.sh` plus `/validate` checks.
- Status: complete

## TASK-FIX-1 — Tighten frame and upload boundaries

- [x] Ignore non-main-frame external navigation requests and cancel upload requests whose MIME hints do not produce an allow-listed picker contract.
- Source: `/review` findings 1–2.
- Acceptance: subframes cannot prompt external navigation; unsafe, absent, or excessive MIME hints cannot open an unrestricted picker; focused negative controls pass.
- Tests: `./gradlew :app:testDebugUnitTest :app:lintDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete
