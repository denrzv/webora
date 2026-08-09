# SKIN-002: Implementation plan
Status: PLAN_APPROVED

## Overview
Add an app-layer immutable presentation model whose factory accepts only a trusted
`SiteSkinConfiguration`, browser-observed `SecurityPresentation`, a projected `SiteSkinColorScheme`,
and the already-validated `BrandAsset`. Render that model in a standalone Compose top bar with a
fixed logo slot and a structurally required, browser-owned domain/TLS row. Activation and wiring
into the browsing screen remain deliberately outside this ticket.

## Flow
- Discovery and validation: unchanged. Manifest and brand-asset discovery continue concurrently
  with page rendering, and core remains the remote-input trust boundary.
- Normalization: the validator supplies bounded title/subtitle and same-origin logo URL; the existing
  asset loader supplies a decoded bounded bitmap or monogram; `SiteSkinTheme` supplies guarded
  colours. The top bar does no parsing, fetching, decoding, or colour conversion.
- UI state: a pure factory chooses the trusted toolbar title or trusted site name fallback and
  combines it with mandatory browser-observed security presentation. The immutable result contains
  no nullable security region and exposes no constructor/factory accepting raw URLs or DTOs.
- Rendering: Compose selects the already-projected light/dark scheme at the caller boundary, lays
  out a fixed logo slot and weighted text region, and renders domain/TLS semantics independently
  from brand semantics.

## Data
- `SiteSkinTopBarModel` contains title, optional subtitle, `BrandAsset`, registrable domain, and a
  closed `TransportSecurity`. Its factory inputs are already trusted or browser-observed types.
- The logo bitmap remains opaque to model construction. Rendering always uses a 40 dp clipped slot
  and `ContentScale.Fit`; monograms use the same slot.
- Title/subtitle/domain use one line and ellipsis so arbitrary bounded strings cannot change region
  geometry. TLS and domain get distinct browser-authored content descriptions.
- No storage or cache is added. The model is deterministic; assets retain the existing coordinator
  lifetime.

## Security
- Origin boundary: `SecurityPresentation` comes only from committed `BrowserMode`/`SiteOrigin` and
  cannot be supplied by a manifest. Logo bytes have already passed exact-origin checks. The top bar
  never compares or resolves URL strings.
- The required model fields and unconditional security-row rendering are the enforcement mechanism
  for ADR-006. There is no `showDomain`, hidden state, generic icon, or website-provided TLS label.
- The closed `BrandAsset` variants are the image allow-list. Bitmap intrinsic dimensions never
  participate in measurement; failures arrive as the existing monogram fallback.
- Only the projected closed scheme can colour the component. Security content uses its guarded
  foreground role and is never independently manifest-styled.
- A pure negative-control test asserts that every constructible model retains the browser-observed
  domain and TLS state. Device layout tests verify visible semantics and fixed logo bounds when a
  device is available.

## File-by-file plan
### New: `app/src/main/java/app/webora/browser/siteskin/SiteSkinTopBar.kt`
Define the immutable presentation model/factory and standalone Compose renderer with bounded logo,
brand text, mandatory security identity, stable semantics/test tags, and light/dark previews.

### New: `app/src/test/java/app/webora/browser/siteskin/SiteSkinTopBarModelTest.kt`
Create trusted configurations through `SiteSkinValidator`; cover trusted toolbar mapping, fallback
name, optional subtitle, brand variant preservation, and the browser-identity negative control.

### New: `app/src/androidTest/java/app/webora/browser/siteskin/SiteSkinTopBarTest.kt`
Exercise Compose semantics for the title, domain, TLS label, and fixed logo slot. Compile this test
even when managed-cloud infrastructure has no connected device.

### Modified: `app/src/main/res/values/strings.xml`
Add browser-authored SiteSkin security descriptions and preview-safe visible TLS wording.

### Modified: `CLAUDE.md`
Record the stable top-bar ownership boundary and its non-integration with activation.

## Tests
- `./gradlew :app:testDebugUnitTest --tests app.webora.browser.siteskin.SiteSkinTopBarModelTest`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:compileDebugAndroidTestKotlin`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`
- `bash scripts/pre-commit-check.sh` before the task commit.
- Negative control: temporarily replace browser-observed security values in the model factory and
  verify the identity-preservation test fails, then restore and record the result in the tasklist.
- Runtime instrumentation/screenshot is attempted only with a connected Android device; managed
  cloud must not provision a software-only emulator.

## Rollout / versioning
No schema, persisted data, activation state, or ordinary-browser UI changes. `SKIN-004` will wire
the standalone component to an origin-bound integrated mode, so this ticket needs no migration or
feature flag.

## Open questions
- None. ADR-006, the trusted core model, `NET-003`, and `SKIN-001` fix all security-relevant inputs
  and ownership decisions.
