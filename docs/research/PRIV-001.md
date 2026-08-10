# PRIV-001: Research
Status: RESEARCH_READY

## Existing state
- `BrowserScreen` owns manifest discovery, consent prompts, integrated activation, and both regular
  and SiteSkin menu entry points. Its SiteSkin menu Settings command is currently a no-op.
- `SiteConsentStore` persists `ALLOW`/`NEVER` under a Base64 encoding of the complete canonical
  `SiteOrigin`, but its preference abstraction cannot enumerate or remove values.
- `ManifestCache` is in-memory and currently has no clear operation. Discovery creates its own
  default cache through `ManifestDiscoveryCoordinator`.
- `HardenedWebView` owns the actual WebView lifecycle; `BrowserWebViewController` is the narrow
  composition-to-WebView command seam and is the appropriate place for local cache/form clearing.
- Android platform data is split across `CookieManager`, `WebStorage`, and the live WebView. Cookie
  deletion has an asynchronous callback; Web Storage and live-WebView deletion do not.
- The manifest declares INTERNET and ACCESS_NETWORK_STATE only. Dependency inspection shows no
  analytics, advertising, crash-reporting, or telemetry SDK.

## Origin and trust-boundary map
- Preference decisions remain keyed and displayed by the complete canonical origin (scheme, ASCII
  host, and non-default port). A host suffix, registrable domain, subdomain, or page-provided label
  must never be substituted.
- The website may influence only an already-validated SiteSkin configuration after global enablement
  and exact-origin consent. It cannot influence settings labels, toggle state, deletion scope, or
  confirmation UI.
- The browser owns global enablement, stored-decision enumeration/removal, all deletion adapters,
  success/failure messaging, and Data safety documentation.
- Global disablement is checked both before discovery work is started and before a result is
  published. This publication-time check is required because disabling cannot retroactively cancel
  an outcome already queued for Compose.

## Proposed seams
- Expand the pure `SiteConsentPreferences`/`SiteConsentStore` seam to list and remove decisions while
  decoding only valid canonical origins and recognized enum values.
- Add a small browser preference store for the global SiteSkin Boolean, defaulting enabled.
- Add a pure settings model and browser-owned Compose settings surface. Keep Android reads/writes in
  thin store wrappers so JVM tests cover policy without Robolectric.
- Add a `BrowsingDataCleaner` orchestrator over closed adapters, plus an Android wrapper for cookies
  and Web Storage and controller operations for the live WebView. Completion is reported only after
  the cookie callback; cache/consent clearing is part of the same operation.

## Files likely affected
- `app/src/main/java/.../siteskin/SiteConsentStore.kt`, `ManifestCache.kt`,
  `ManifestDiscoveryCoordinator.kt`
- `app/src/main/java/.../browser/BrowserScreen.kt`, `BrowserState.kt`, and new privacy/settings files
- `app/src/main/java/.../web/BrowserWebViewController.kt`
- corresponding JVM and Compose instrumentation tests, `strings.xml`
- `docs/privacy/DATA_SAFETY.md`, `CLAUDE.md`, and `docs/ROADMAP.md`

## Risks and tests
- Negative control: with the global-off candidate guard removed, a test must show an accepted
  candidate can activate or prompt.
- Invalid or legacy preference entries must not be displayed as origins and must not crash settings.
- Deletion must preserve onboarding and the global switch; tests use separate fake adapters/stores
  to prove the requested scope rather than relying on Android framework no-ops.
- Runtime instrumentation/screenshots require a connected Android device; JVM policy and build
  compilation remain available in managed cloud.

## Question
What the plan needs decided before it can commit to a trust boundary and a file list.

## Origins involved
- serving origin(s)
- asset origin(s), and why they are same-origin

## Manifest-controlled surface
What a website can influence if this ships as scoped.

## Browser-owned remainder
What must stay browser-controlled, and the affordance that enforces it.

## Relevant code
| Path | Why it matters |
|---|---|

## Prior art
ADRs, spec sections, fixtures and tickets that already decided part of this.

## Risks
- risk → the plan's obligation in response

## Open questions
Carried into `/plan` as explicit unknowns, not silently resolved here.
