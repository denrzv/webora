# QA Report: BROWSE-004
Status: QA_PASSED

## Scope

Regular-mode security identity, navigation/overflow chrome, main-frame error recovery, JVM regression coverage, Android compilation, lint, and repository guardrails.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | HTTPS committed origin shows Secure and registrable domain | JVM unit test | PASS |
| 2 | HTTP/absent origin cannot claim Secure | JVM negative-control test | PASS |
| 3 | Edited address cannot spoof committed identity | JVM negative-control test | PASS |
| 4 | Unsafe failure URL cannot become Retry | JVM unit test | PASS |
| 5 | New page start clears a stale failure | JVM unit test | PASS |
| 6 | Chrome, resources, Android tests, and APK compile | Gradle compile/assemble | PASS |
| 7 | Runtime visual and TalkBack inspection | Connected-device check | NOT RUN — no device and no `/dev/kvm`; instrumentation was compiled per managed-cloud policy. |

## Edge cases

- invalid manifest → N/A — this ticket does not discover or validate manifests; existing regular browsing is unchanged.
- origin change / redirect → PASS — each main-frame observation reparses and replaces the exact committed origin.
- offline with cached manifest → N/A — no manifest cache exists in this ticket; network failure uses the regular error state.
- oversized or malformed payload → N/A — no payload parsing change.
- accessibility (TalkBack, font scale) → Compile PASS; runtime inspection unavailable without a connected device or KVM.

## Result

Status: QA_PASSED
Notes: Automated acceptance checks pass. Runtime instrumentation and screenshot capture are an environment limitation, not a product-test failure.
