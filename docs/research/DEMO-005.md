# Research: DEMO-005 — Expressive Bloom Flowers integrated showcase
Status: RESEARCH_READY

## Scope and upstream state

Webora issues #84–#86 are represented by completed UX-013..015 artifacts and current production
code. Public GitHub issue state shows Bloom `BLOOM-001` (#3) and `BLOOM-002` (#4) closed. The public
site source defines `/catalog/happy-days/`; the live origin and its network availability remain an
external prerequisite for hosted instrumentation rather than something this repository can seed.

`LiveSiteScreenshotTest` is the integration owner already exercised by the manual hosted workflow.
It currently waits for `SITESKIN_BOTTOM_NAV_TAG`, captures one integrated frame, and immediately
uses header Back to reach Home before navigating to a regular origin. UX-015 deliberately removed
that persistent integrated layer in favor of `SITESKIN_DOCK_TAG` and `SITESKIN_DOCK_HUB_TAG`, so the
test is semantically stale even though it compiles.

## Origins involved

- `https://denrzv.github.io`: exact page, manifest, logo, and product-asset origin. Same-origin
  product navigation must retain the active trusted configuration.
- `https://example.com`: distinct regular HTTPS origin used only for teardown evidence.
- `https://bloomflowers.example`: deterministic fixture origin only; never a deployment alias.

No suffix, registrable-domain, or DOM-derived comparison belongs in the test. Production
`SiteOrigin`/mode handoff remains authoritative.

## Manifest-controlled surface

The deployed untrusted manifest may influence only the already-validated Bloom name, colors, local
brand asset, Home/Flowers/Cart/Account labels and typed Call action. Page HTML supplies a normal
accessible product link. The journey may locate those visible labels, but cannot treat them as proof
of origin, TLS, command ownership, or successful action resolution.

## Browser-owned remainder

Consent attribution, canonical TLS/domain identity, expressive geometry, Back/Forward enabled state,
dock order, hub attribution/sections, Tabs/More, active-tab callbacks, teardown, timeouts, screenshot
guard, and evidence storage remain compiled browser/harness policy. The test must assert browser tags
around each transition and must not inject JavaScript, rewrite WebView state, seed consent, or expose
a website-only test hook.

## Relevant code

| Path | Why it matters |
|---|---|
| `app/src/androidTest/.../visual/LiveSiteScreenshotTest.kt` | Existing black-box live journey and stale integrated assertion. |
| `app/src/main/.../siteskin/SiteSkinDock.kt` | Fixed dock/hub tags and explicit Back/Forward callbacks. |
| `app/src/main/.../siteskin/SiteSkinChrome.kt` | Hub semantics and bounded manifest labels. |
| `app/src/main/.../browser/BrowserScreen.kt` | Exact active projection, current controller, hub invalidation, regular teardown. |
| `.github/workflows/android-screenshots.yml` | Connected hosted runner; CI-009 will expand final evidence. |
| `spec/fixtures/valid/bloom-flowers.json` | Canonical manifest route/action vocabulary. |

## Prior art

- ADR-004 exact-origin binding; ADR-006 non-suppressible browser identity; ADR-007 typed actions;
  ADR-009 non-blocking discovery; ADR-010 regular fallback; ADR-011 consent; ADR-013 compiled geometry.
- UX-014 owns expressive identity and UX-015 owns fixed dock/hub semantics.
- CI-008 owns the current integrated→regular screenshot sentinel; CI-009 will own expanded final
  visual evidence and two cold runs.

## Recommended implementation direction

Update only the live journey: assert the expressive header/dock after consent, activate the normal
`Happy Days Bouquet` web link by accessible text, wait for Forward to become available after Back,
open the hub through its fixed dock tag, assert all current manifest labels, dismiss through a typed
route selection, and finally enter `example.com` through browser-owned address UI after returning
Home. Add a JVM source contract that rejects the retired tag and requires the M9 checkpoints; this
is the runnable negative control available without a device.

## Risks

- Product link inaccessible to Compose semantics → use UiAutomator/accessibility only if the WebView
  bridge does not surface it; do not add production DOM hooks.
- Remote deployment lag → compile/source gates still run locally; hosted runtime remains a stated
  prerequisite, never silently replaced by fixtures.
- Hub remains open during teardown → select Home/dismiss first, then use the protected header Back
  and ordinary address input; assert hub/dock absence at the regular origin.

## Open questions resolved for planning

- DEMO-005 changes the journey contract, not final evidence filenames; CI-009 owns screenshots.
- Call is asserted reachable by accessible label but not invoked, avoiding an external Android dialer
  that would contest the screenshot and test a capability already covered by resolver/effect tests.
