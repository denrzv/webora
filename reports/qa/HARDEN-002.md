# QA Report: HARDEN-002
Status: QA_PASSED

## Scope

Browser-owned SiteSkin identity, bounded decorative brand presentation, canonical-origin first-use
consent, exact-origin persistence, and stale/cross-origin activation fallback.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Hostile title/subtitle cannot replace domain/TLS identity | JVM model and compiled Compose tests | PASS |
| 2 | Extreme bitmap remains in the 40 dp logo slot | Compiled Compose instrumentation test | PASS (compiled) |
| 3 | Monogram logo is decorative while security description remains exposed | Compiled Compose instrumentation test | PASS (compiled) |
| 4 | Consent names canonical scheme/host/non-default port and browser-owned boundary | Compiled Compose instrumentation test | PASS (compiled) |
| 5 | Allow/Not now/Never actions are all present | Compiled Compose instrumentation test | PASS (compiled) |
| 6 | Undecided candidate asks before activation; Never ignores | `SiteSkinRuntimeTest` in app unit suite | PASS |
| 7 | Different origin and stale generation cannot activate with Allow | `SiteSkinRuntimeTest` negative controls | PASS |
| 8 | Scheme, sibling host, and non-default port decisions are isolated | `SiteConsentStoreTest` in app unit suite | PASS |
| 9 | Full repository guardrail | `bash scripts/pre-commit-check.sh` | PASS |

## Edge cases
- invalid manifest → regular browser mode: PASS — validator/discovery rejection cannot create a candidate.
- origin change / redirect: PASS — exact origin and generation checks ignore stale/mismatched results and actions.
- offline with cached manifest: PASS — no cache policy changed; a cached accepted candidate still requires exact-origin consent.
- oversized or malformed payload: PASS — no parser behavior changed; existing full suite keeps rejection coverage.
- accessibility (TalkBack, font scale): PASS at semantics/compile level — identity has a dedicated
  description and decorative logo descendants are cleared; runtime TalkBack/font-scale execution
  is unavailable without a connected Android device.

## Result
Status: QA_PASSED
Notes: `:app:compileDebugAndroidTestKotlin` passed. `:app:connectedDebugAndroidTest` built both APKs
but could not execute because no Android device is connected and `/dev/kvm` is absent; per repository
policy no software-only emulator was provisioned. A screenshot is unavailable for the same managed-
cloud limitation. The first full gate attempt encountered a Gradle test-results race caused by
overlapping leftover daemons; after stopping them and clearing only the incomplete result directory,
the full gate passed without source changes.
