# BROWSE-002 QA

Status: QA_PASSED

## Scenario results

| # | Scenario | Evidence | Result |
|---|---|---|---|
| 1 | Explicit HTTP(S) navigation | resolver JVM table | PASS |
| 2 | Host-like input defaults to HTTPS | resolver JVM assertion | PASS |
| 3 | Search text uses encoded browser endpoint | resolver JVM assertion | PASS |
| 4 | Denied schemes, credentials, fragments, controls, and malformed URL fail closed | negative resolver table | PASS |
| 5 | Page observation enters only Regular mode and updates URL/loading/history | state JVM assertions | PASS |
| 6 | Address editing does not alter observed mode | state JVM assertion | PASS |
| 7 | URL bar and back/forward/reload chrome integrate with hardened host | Android Kotlin compile + debug APK assembly | PASS |
| 8 | Existing WebView hardening remains intact | existing unit/device-source checks | PASS |
| 9 | Full repository guardrails | `bash scripts/pre-commit-check.sh` | PASS |

## Edge cases

- invalid manifest → N/A — this ticket does not discover or consume manifests; page input cannot activate Integrated mode.
- origin change / redirect → the callback URL replaces the Regular-mode observed origin and cannot create Integrated mode.
- offline with cached manifest → N/A — no manifest cache exists; renderer loading/history state remains descriptive.
- oversized or malformed payload → N/A for manifests; malformed address input is not navigated and malformed callback URLs retain no trusted origin.
- accessibility (TalkBack, font scale) → Material 3 controls expose text labels and scale text; exhaustive validation remains A11Y-001.
- system/predictive back → the activity dispatcher checks live renderer history and delegates when stale or unavailable.
- screenshot/device execution → unavailable because `adb devices` reports no connected device; APK and instrumentation sources compile.

## Result

All automated acceptance evidence passes. Device runtime instrumentation and screenshot capture are unavailable in this managed checkout and are recorded as an environment limitation, not a product failure.
