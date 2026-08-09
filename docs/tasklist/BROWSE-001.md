# BROWSE-001: Tasklist
Status: TASKLIST_READY

## TASK-1 — Hardened WebView policy

- [x] Add the immutable browser-owned settings policy, framework applicator, and restrictive URL
  classification/client.
- [x] Add JVM tests asserting every fixed setting and HTTP(S)/file navigation behavior.
- Acceptance: policy is applied before loads; local resources, file-URL escalation, and mixed
  content are disabled; Safe Browsing is requested when supported; no JavaScript bridge exists.
- Tests: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:compileDebugAndroidTestKotlin`,
  `bash scripts/pre-commit-check.sh`.
- Negative control: the policy test directly asserts every hardened value and the navigation test
  rejects `file:`, `content:`, `javascript:`, and malformed input; relaxing a value or admitting one
  of those schemes makes its corresponding assertion fail.
- Status: complete

## TASK-2 — Compose host and runnable integration

- [x] Add the reusable AndroidView host and replace the bootstrap screen with an HTTPS WebView.
- [x] Add a real-framework instrumentation assertion for all readable WebSettings values.
- Acceptance: the app assembles and launches into a hardened WebView without duplicate recomposition
  loads; the Android test observes the fixed policy.
- Tests: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`,
  `bash scripts/pre-commit-check.sh`.
- Environment note: the instrumentation source compiles, but this checkout provides neither `adb`
  nor an emulator, so execution and a screenshot are deferred to device CI.
- Status: complete

## TASK-3 — Review, QA, validation, and roadmap closeout

- [x] Resolve review findings, record QA and validation evidence, tick BROWSE-001, and reconcile the
  completed CORE-002..004 markers in `docs/ROADMAP.md`.
- Acceptance: review is resolved, QA passes, all completed M1 core tickets and BROWSE-001 are marked
  done, and workflow validation succeeds.
- Tests: `bash scripts/pre-commit-check.sh` plus the `/validate` checks.
- Status: complete

## TASK-FIX-1 — Make the Safe Browsing policy assertable

- Source: `/review` finding 1.
- [x] Represent Safe Browsing in the immutable policy and assert its enabled value in both the JVM
  policy test and the supported-provider instrumentation path.
- Acceptance: weakening the Safe Browsing request fails a test just like every readable WebSettings
  control.
- Tests: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`,
  `bash scripts/pre-commit-check.sh`.
- Status: complete
