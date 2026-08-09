# QA Report: BROWSE-004
Status: QA_PASSED

## Scope

Regular-mode security identity, navigation/overflow chrome, main-frame error recovery, JVM and real Android regression coverage, Android compilation, lint, and repository guardrails.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | HTTPS committed origin shows Secure and registrable domain | JVM unit test | PASS |
| 2 | HTTP/absent origin cannot claim Secure | JVM negative-control test | PASS |
| 3 | Edited address cannot spoof committed identity | JVM negative-control test | PASS |
| 4 | Unsafe failure URL cannot become Retry | JVM unit test | PASS |
| 5 | New page start clears a stale failure | JVM unit test | PASS |
| 6 | Chrome, resources, Android tests, and APK compile | Gradle compile/assemble | PASS |
| 7 | Main-frame connection failure keeps browser-owned recovery UI and a live Retry controller | Pixel 6 API 33 instrumentation | PASS |
| 8 | Original `https://127.0.0.1:44444/` failure, Retry, Home, screenshot, and crash/ANR inspection | Pixel 6 API 33 manual validation | PASS — [recovery screenshot](BROWSE-004-runtime-recovery.png) |

## Edge cases

- invalid manifest → N/A — this ticket does not discover or validate manifests; existing regular browsing is unchanged.
- origin change / redirect → PASS — each main-frame observation reparses and replaces the exact committed origin.
- offline with cached manifest → N/A — no manifest cache exists in this ticket; network failure uses the regular error state.
- oversized or malformed payload → N/A — no payload parsing change.
- accessibility (TalkBack, font scale) → unchanged by TASK-FIX-2; existing compile coverage remains green.

## Result

Status: QA_PASSED
Notes: Automated acceptance checks pass. Runtime instrumentation and manual recovery validation pass on the Pixel 6 API 33 emulator, with no crash or ANR markers in logcat.
