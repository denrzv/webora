# BROWSE-003 review

Status: RESOLVED

## Summary

Reviewed the two implementation commits for startup persistence, Compose lifecycle, navigation safety, browser/Home state transitions, privacy, accessibility semantics, test coverage, and repository complexity constraints.

## Architecture

| Concern | Assessment |
|---|---|
| State model | General onboarding is a closed launch decision; Home remains the ADR-008 browser mode. |
| Android boundary | `OnboardingStore` is a thin preferences wrapper; launch and catalogue decisions are pure JVM code. |
| Renderer lifecycle | Home does not compose `HardenedWebView`; explicit safe navigation changes mode before the renderer is created. |
| Future ownership | History/favourite persistence and per-origin SiteSkin consent remain outside this ticket. |

## Security

| Property | Assessment |
|---|---|
| Suggestions | Browser-owned catalogue construction rejects non-HTTPS, relative, credential-bearing, fragment-bearing, and malformed targets. |
| Typed navigation | Reuses `AddressResolver`; existing negative scheme controls remain intact. |
| Remote influence | No page or manifest can alter onboarding or Home labels, ordering, targets, or artwork. |
| Privacy | Only one local completion Boolean is persisted; no browsing data or telemetry is added. |

## Findings

No open findings.

## Not findings

- The suggested demo domains need not currently host working integrations: these entries are browser-owned product suggestions, and network failure remains ordinary renderer behavior.
- Recents and favourites intentionally show honest empty states. Fabricating sample history or creating an unplanned persistence contract would be worse than deferring mutation to its owning ticket.
- General product onboarding is not ADR-011 consent. Per-origin permission must still occur after a manifest validates and is not pre-approved by this flag.
- UI strings remain local Kotlin literals like the pre-existing browser chrome. Localization extraction is not introduced selectively in this ticket.
- Runtime screenshots and TalkBack interaction could not be reviewed because no Android device is connected and `/dev/kvm` is absent; compilation and semantics-oriented source review provide the available managed-cloud evidence.

## Test coverage

| Area | Evidence |
|---|---|
| First/returning launch decision | `HomeModelsTest` |
| Suggested HTTPS trust boundary | `HomeModelsTest` positive and negative cases |
| Home-to-Regular transition | `BrowserStateTest` |
| Existing URL scheme allow-list | `AddressResolverTest` |
| Android/Compose integration | Android-test Kotlin compilation and debug APK assembly |
| Quality/security | Android lint, Detekt, unit suite, and pre-commit gate |

## Verdict

RESOLVED — no open findings remain.
