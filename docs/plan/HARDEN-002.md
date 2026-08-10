# HARDEN-002: Implementation plan
Status: PLAN_APPROVED

## Overview
Harden the two roadmap controls already established by ADR-006/011: non-suppressible browser
identity and exact-origin first-use consent. Preserve the existing runtime architecture, close the
visible consent-identity and decorative-logo-semantic gaps, and make regressions fail focused tests.
## Flow
- Discovery and validation remain non-blocking and publish only an origin/generation-attributed
  trusted candidate.
- With no stored decision, the candidate leaves the browser in regular mode and presents a
  browser-authored prompt containing its canonical origin.
- Allow is stored for that exact origin and activates only after a fresh origin/generation check;
  Not now dismisses without storage; Never stores an exact-origin refusal.
- Integrated rendering derives security presentation from the active `BrowserMode` origin, renders
  corrected colours and bounded decorative branding, then always renders domain/TLS identity.
## Data
- Manifest DTOs and branding remain untrusted until the shared core validator constructs a trusted
  `SiteSkinConfiguration`. Browser-observed `SiteOrigin` and `SecurityPresentation` never come from
  that configuration's display strings.
- Consent storage stays keyed by encoded `SiteOrigin.canonical`; no migration or new value is
  needed. The UI receives that canonical value for honest grant presentation.
- Registrable domain remains the compact active-chrome display, while canonical origin is the
  consent identity. Neither becomes a cache/trust key for the other.
## Security
- Scheme, full ASCII host, and effective port bind discovery, configuration, consent, and
  activation. Subdomains and ports do not inherit grants.
- Website control remains limited to validated brand and closed navigation/action allow-lists.
  Logo descendants are decorative and cannot create competing accessibility identity.
- Stale, mismatched, invalid, Not now, and Never outcomes retain regular mode. No failure blocks
  WebView rendering or expands native capability.
## File-by-file plan
### Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`
Pass the candidate's browser-canonical origin to the consent UI rather than its lossy registrable
domain; name the parameter for the stronger contract.

### Modified: `app/src/main/java/app/webora/browser/siteskin/SiteSkinTopBar.kt`
Clear semantics on the bounded logo container while retaining the independently tagged security
identity and its browser-authored description.

### Modified: `app/src/androidTest/java/app/webora/browser/browser/SiteSkinConsentDialogTest.kt`
Assert canonical-origin copy, explanatory control boundary, all three choices, and callbacks.

### Modified: `app/src/androidTest/java/app/webora/browser/siteskin/SiteSkinTopBarTest.kt`
Assert hostile branding cannot replace identity, logo descendants are decorative, and the logo
slot remains bounded.

### Modified: `app/src/test/java/app/webora/browser/siteskin/SiteSkinRuntimeTest.kt`
Strengthen pre-consent, stale-candidate, and exact-origin negative controls if the existing matrix
does not already name them explicitly.

### Modified: `app/src/test/java/app/webora/browser/siteskin/SiteConsentStoreTest.kt`
Pin scheme, sibling-subdomain, and non-default-port isolation if any dimension lacks coverage.

### Modified: `CLAUDE.md`, `docs/ROADMAP.md`, ticket reports/artifacts
Record the hardened browser-owned presentation/consent contract and validation evidence.
## Tests
- Run focused app JVM tests after each production code change and compile instrumentation tests.
- Attempt connected instrumentation only when a device is available; do not provision a
  software-only emulator without KVM.
- Negative controls: restore registrable-only consent text and logo semantics, and weaken an
  origin/generation guard; verify the named focused tests fail, then restore protections.
- Run `./gradlew :app:testDebugUnitTest`, `./gradlew detekt`, and
  `bash scripts/pre-commit-check.sh`.
## Rollout / versioning
No schema, protocol version, permission, network, or storage migration. Existing decisions remain
valid because persistence keys and enum values do not change. The consent string becomes more
specific before release.
## Open questions
None.
