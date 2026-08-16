# DEMO-005: Implementation plan
Status: PLAN_APPROVED

## Overview

Turn the existing hosted live-site instrumentation into the M9 cross-repository story. Replace its
retired bottom-navigation checkpoint with expressive header/dock checkpoints, visit the deployed
Happy Days route through ordinary WebView navigation, exercise browser-owned Back/Forward, inspect
the trusted navigation hub, then prove exact-origin teardown. Production behavior and the SiteSkin
protocol remain unchanged.

## Flow

1. Home opens the compiled Bloom suggestion; production discovery fetches the origin-root manifest.
2. Normal parse, validation, consent, exact-origin activation, and asset loading produce the trusted
   integrated projection.
3. The test waits for browser-owned expressive identity/dock tags and validates the brand decision.
4. A normal accessible page link opens `/catalog/happy-days/`; the test proves integrated chrome
   persists and uses current-controller Back/Forward to traverse real WebView history.
5. The fixed brand command opens the hub; bounded current-model labels prove route/action projection.
6. The test returns through browser UI, enters the regular HTTPS origin, and asserts ordinary chrome
   plus complete absence of expressive/hub/legacy SiteSkin surfaces.

## Origin-boundary implications and browser-owned contract

`denrzv.github.io` is the only integrated origin. Same-origin product navigation retains the
configuration; `example.com` must deactivate it. The test observes production mode tags and never
parses origins, injects JavaScript, seeds consent, or grants page content authority. Manifest input
may supply only trusted bounded site labels and typed actions. Webora owns identity, TLS state,
geometry, dock order, history callbacks, hub attribution, dismissal, tab/session addressing, and
regular fallback.

## Data

- No DTO, trusted model, schema, cache, persistence, network, or production state change.
- Test constants name the deployed product label and current manifest labels.
- No URL/controller/action is stored beyond the sequential test interaction.

## Security

- Exact-origin state is asserted through production security tags before and after history actions.
- Dock/hub commands are selected through browser-owned fixed tags and trusted accessible labels.
- Call is checked but not launched; external intent ownership is outside this visual journey.
- Regular teardown requires absence of header, dock, hub, legacy navigation, and quick-action tags.
- A JVM source negative control rejects any return to the retired bottom-navigation checkpoint.

## File-by-file plan

### Modified: `app/src/androidTest/java/app/webora/browser/visual/LiveSiteScreenshotTest.kt`

Implement the product/history/hub journey and update integrated/teardown assertions to M9 tags.
Keep current screenshots intact for CI-008 compatibility; CI-009 will expand the evidence set.

### Modified: `app/build.gradle.kts`, `gradle/libs.versions.toml`

Add AndroidX UiAutomator as an instrumentation-only dependency so the black-box journey can select
normal accessible WebView content without a production JavaScript bridge or test hook.

### New: `app/src/test/java/app/webora/browser/evidence/ExpressiveBloomJourneyContractTest.kt`

Pin the black-box source contract without an emulator: expressive dock/hub/product/history/teardown
checkpoints are required and waiting for the retired bottom navigation is rejected. Include an
unsafe fixture as the negative control.

### Modified: `docs/WALKTHROUGH.md`

Bring the manual narrative and current screenshot descriptions in line with the expressive dock,
Happy Days history, and native hub while preserving the regular-browser continuity story.

### Modified after review/QA

`reports/review/DEMO-005.md`, `reports/qa/DEMO-005.md`, `CLAUDE.md`, and `docs/ROADMAP.md` record the
resolved review, acceptance mapping, integration convention, and ticket completion.

## Tests

- Focused JVM source contract, including unsafe legacy negative fixture.
- `./gradlew :app:compileDebugAndroidTestKotlin` for the live journey.
- `./gradlew :siteskin-lint:run --args="https://denrzv.github.io"` when network policy permits.
- `bash scripts/pre-commit-check.sh` before each task commit.
- Connected runtime/screenshots only with a device/KVM runner; do not provision a software emulator.

## Rollout / versioning

No production, protocol, storage, permission, or rollout change. This test/docs integration lands
after all five upstream tickets. CI-009 remains blocked on and owns the final expanded frames and
two consecutive cold hosted runs.

## Open questions

- If WebView links are not exposed in the Compose semantics bridge, the implementation may use
  UiAutomator's accessibility selector. It must not add a JavaScript bridge or production test hook.
