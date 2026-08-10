# SKIN-004: Implementation plan
Status: PLAN_APPROVED

## Overview

Introduce a pure `SiteSkinRuntime` state machine between page observations, attributed discovery
outcomes, durable consent, and `BrowserMode`. It will drop cross-origin chrome synchronously, expose
one current consent request, and accept activation only for the current origin and navigation
generation. Then wire that model into `BrowserScreen`, existing integrated components, cancellable
brand assets, and a closed action dispatcher. This completes runtime composition without moving any
security decision into Compose or blocking WebView rendering.

## Flow

1. A main-frame start is first reduced into browser state/runtime state. Same-origin navigation may
   retain integrated mode; every other URL immediately becomes regular and invalidates pending
   consent/activation by advancing a generation.
2. The discovery coordinator begins concurrently. Every outcome carries the canonical request
   origin and generation captured at start, including unavailable outcomes.
3. Runtime accepts an available configuration only when origin and generation still match. A saved
   Allow activates; a saved Never remains regular; no decision creates browser-owned pending consent.
4. Allow is durably recorded, then the candidate is rechecked and activated. Never is recorded and
   discarded. Not now discards the candidate without persistence. Navigation invalidates the prompt.
5. Integrated composition projects theme, browser security identity, top bar, and navigation model.
   Asset decoding is cancellable and publishes only for the still-active configuration.
6. Trusted item selection goes through core `ActionResolver`. Internal navigation/reload/menu use
   closed controller/UI operations; dial/email/map use typed external confirmation. External HTTPS
   and sharing use explicit browser callbacks, never a generic intent parsed from remote input.
7. Unavailable discovery, denial, malformed pages, stale work, or effect-resolution failure leaves
   the renderer usable with regular chrome.

## Origin boundary and browser-owned contract

The observed, discovery, consent, and active origins are all full canonical `SiteOrigin` values.
Only exact equality of scheme, canonical host, and normalized port can retain or activate a skin.
The runtime additionally compares a monotonic navigation generation when publishing asynchronous
results. Registrable domains are display strings only and are never authority or persistence keys.

Remote influence begins only after `SiteSkinValidator` has produced a trusted configuration and the
browser-owned consent state permits it. It is limited to existing closed branding, theme,
navigation, menu, and action models. The browser exclusively owns consent decisions and copy,
generation/origin checks, domain/TLS identity, WebView settings, capability dispatch, fallback, and
all fixed menu commands. There is no generic URI/intent bridge and no new permission.

## Data and persistence

- `SiteSkinRuntimeState` holds browser mode, navigation generation, observed origin, and at most one
  candidate. Pure functions implement observe, discovery, and consent transitions.
- `ManifestDiscoveryOutcome` includes origin and generation. The coordinator owns cancellation as a
  performance mechanism; runtime attribution is the security mechanism.
- `SiteConsentStore` persists only `ALLOW` or `NEVER` against an encoded full canonical origin.
  Missing means Ask. `NOT_NOW` is an event, never stored. Use SharedPreferences for consistency with
  existing synchronous app startup storage and a minimal atomic decision read/write; PRIV-001 owns
  management UI and global policy later.
- Active brand asset starts as deterministic monogram and may be replaced by a decoded bitmap only
  while configuration identity remains current.

## File-by-file plan

### New: `app/src/main/java/app/webora/browser/siteskin/SiteSkinRuntime.kt`
Define closed consent decisions/events, candidate/runtime state, pure origin+generation transition
functions, consent-store contract, and action resolution into an app-owned closed runtime effect.

### New: `app/src/main/java/app/webora/browser/siteskin/SiteConsentStore.kt`
Implement origin-keyed Allow/Never persistence with collision-free URL-safe encoding and no API for
raw website strings.

### Modified: `app/src/main/java/app/webora/browser/siteskin/ManifestDiscoveryCoordinator.kt`
Accept a generation at page start and attach generation plus canonical origin to all outcomes.

### Modified: `app/src/main/java/app/webora/browser/browser/BrowserState.kt`
Retain `Integrated` only for exact same-origin observations and expose explicit activation rather
than allowing generic page callbacks to construct trusted integrated state.

### Modified: `app/src/main/java/app/webora/browser/browser/SecurityPresentation.kt`
Read origin from either regular or integrated modes so both chrome variants share browser-owned
identity derivation.

### Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`
Own runtime state, attributed discovery, consent orchestration, cancellable assets, integrated
composition, and closed effect dispatch while keeping one hardened WebView alive across modes.

### Modified: `app/src/main/java/app/webora/browser/MainActivity.kt`
Construct the consent store/capability callbacks at the Android composition root.

### Modified: `app/src/main/res/values/strings.xml`
Add browser-authored consent and integrated interaction labels.

### Tests
- Extend `BrowserStateTest` and `SecurityPresentationTest` for same-origin retention and integrated
  identity.
- Update `ManifestDiscoveryCoordinatorTest` for exact origin/generation attribution and cancellation.
- Add `SiteSkinRuntimeTest` for allow/never/not-now, stale result, cross-origin drop, same-origin
  retention, skin swap, and closed effect mapping with origin/generation negative controls.
- Add `SiteConsentStoreTest` for exact scheme/host/port isolation and restart persistence.
- Add/extend Android Compose tests for consent controls and integrated surface composition; compile
  instrumentation when no device is available.

### Documentation
Update `CLAUDE.md` with the completed activation boundary and `docs/ROADMAP.md` after review/QA.

## Tests

- Focused JVM tests for each touched unit after implementation.
- Explicit negative-control runs temporarily remove origin comparison and generation comparison,
  confirm focused tests fail, and restore the protections.
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:compileDebugAndroidTestKotlin`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`
- `bash scripts/pre-commit-check.sh` before every task commit.
- Run connected instrumentation and capture a screenshot only if a device is present; do not create
  a software-only emulator in managed cloud without `/dev/kvm`.

## Rollout / versioning

No protocol/schema/core API migration is needed. Consent preferences are new and default to Ask;
there is no prior data. Any runtime failure degrades to regular browsing. `PRIV-001` can later add a
global switch and decision-management UI without changing the origin-keyed decision semantics.

## Open questions

None. ADR-011 makes minimal durable Allow/Never storage part of activation; `PRIV-001` retains the
broader privacy settings and global-control scope.
