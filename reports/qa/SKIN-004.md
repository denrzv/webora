# QA Report: SKIN-004
Status: QA_PASSED

## Scope

Consent-aware SiteSkin activation, full-origin and navigation-generation binding, same-origin
retention, cross-origin deactivation/swap eligibility, trusted integrated chrome composition,
brand-asset supersession, closed action dispatch, and regular-browser fallback.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Accepted manifest on current exact origin asks without saved consent | `SiteSkinRuntimeTest` | Pass |
| 2 | Allow activates; Never suppresses; Not now remains ephemeral | Runtime/store JVM tests plus consent Compose test compilation | Pass |
| 3 | Scheme, subdomain, and non-default port do not inherit consent | `SiteConsentStoreTest` | Pass |
| 4 | Same-origin navigation retains integrated mode | `BrowserStateTest` | Pass |
| 5 | Cross-origin navigation drops integrated mode before discovery | `BrowserStateTest` | Pass |
| 6 | Accepted destination configuration is eligible only for its own origin/generation | `SiteSkinRuntimeTest` and negative controls | Pass |
| 7 | Superseded discovery cannot publish an old result | `ManifestDiscoveryCoordinatorTest` | Pass |
| 8 | Integrated chrome uses trusted model, decoded/monogram asset, and browser security identity | JVM model suites + Android test compilation | Pass |
| 9 | All SiteSkin selections use closed resolver dispatch; external HTTPS confirms first | exhaustive Kotlin dispatch + `SiteSkinConsentDialogTest` compilation | Pass |
| 10 | Full repository guardrail suite | `bash scripts/pre-commit-check.sh` | Pass |

## Edge cases

- **Invalid manifest → regular browser mode:** Pass — coordinator emits attributed Unavailable and
  runtime does not activate.
- **Origin change / redirect:** Pass — page-start observation compares complete `SiteOrigin` and
  drops a different active origin before starting the new generation's discovery.
- **Offline with cached manifest:** Pass — existing NET-002 tests remain green; only revalidated
  accepted cached bytes reach the new activation seam and consent still applies.
- **Oversized or malformed payload:** Pass — existing transport/parser rejection suites remain green
  and Unavailable preserves regular rendering.
- **Accessibility (TalkBack, font scale):** Android sources compile and browser-owned labels cover
  all three consent actions and external confirmation. Runtime TalkBack/font-scale execution is
  unavailable because managed cloud has neither `/dev/kvm` nor a connected Android device.
- **Malformed/non-HTTPS page callback:** Pass — no activation candidate can match; browser remains
  regular with a nullable observed origin.
- **Stale consent dialog action:** Pass — Allow rechecks exact origin and generation before activation.
- **External handler missing:** Pass — Android adapter returns false without crashing; no generic
  intent syntax is accepted.

## Environment

No `/dev/kvm` and no connected `adb` device are available. Per repository policy, no software-only
emulator was provisioned. Runtime instrumentation and a perceptual screenshot are therefore an
environment limitation; instrumentation sources compile successfully.

## Result

Status: QA_PASSED

Notes: A full app-unit invocation once hit the existing timing-sensitive OkHttp cancellation test.
The focused test immediately passed and the subsequent full pre-commit gate, including all unit
tests, passed. This is recorded as observed flakiness rather than a SKIN-004 failure.
