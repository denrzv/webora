# BROWSE-003 tasklist

Status: TASKLIST_READY

## TASK-1 — Model onboarding, Home launch, and safe suggestions

- [x] Add pure onboarding launch state and an immutable HTTPS-only suggested-integration catalogue, with focused JVM tests.
- Acceptance: returning-user launch selects Home; unsafe or credential-bearing suggestion targets fail closed; no Android dependency enters pure models.
- Tests: `./gradlew :app:testDebugUnitTest :app:lintDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-2 — Build onboarding and Home UI

- [x] Add the preference wrapper, accessible onboarding carousel, Home sections, and safe navigation integration without loading a WebView on Home.
- Acceptance: skip/finish persists completion; Home exposes search, empty recents/favourites, and browser-owned suggestions; selection enters existing safe navigation; Android tests compile.
- Tests: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug`, `bash scripts/pre-commit-check.sh`.
- Status: complete

## TASK-3 — Review, QA, validation, and closeout

- [x] Record review and QA evidence, update normative architecture notes and ROADMAP, and validate the workflow.
- Acceptance: review has no open findings, QA status is final, BROWSE-003 is marked complete, and validation succeeds.
- Tests: `bash scripts/pre-commit-check.sh` plus `/validate` checks.
- Status: complete
