# SKIN-004: Research
Status: RESEARCH_READY

## Existing runtime seams

- `BrowserMode` already contains `Integrated(origin, configuration)`, but `BrowserState.observePage`
  currently rewrites every page callback to `Regular`; this is the central pure transition seam.
- `BrowserScreen` creates `ManifestDiscoveryCoordinator` with a no-op outcome callback, then sends
  page starts to both discovery and state independently. The coordinator cancels the previous job,
  validates against a canonical HTTPS origin, and emits only accepted trusted configuration or
  unavailable. Its outcome does not currently identify the request origin, so runtime publication
  cannot independently prove that an asynchronous outcome belongs to the currently observed page.
- `HardenedWebView` reports main-frame starts before the renderer load completes. That callback is
  the required immediate deactivation point; discovery must remain subsequent asynchronous work.
- `SiteSkinTopBar`, `SiteSkinBottomNavigation`, `SiteSkinQuickActions`, and `SiteSkinMenu` are thin
  standalone components. `SiteSkinTheme`, `SiteSkinTopBarModel`, and `SiteSkinChromeModel` derive
  their bounded inputs from trusted configuration plus browser-observed security/page state.
- `BrandAssetCoordinator` already cancels superseded asset jobs. It needs to be driven only after an
  activation candidate remains current; monogram fallback can render while decoding completes.
- Core `ActionResolver` maps trusted `NormalizedAction` plus trusted site configuration and the
  browser-observed page URL into a closed `ResolvedAction`. Existing external-navigation confirmation,
  controller navigation/reload, and browser-owned menu state are the app-layer effect seams.
- Preferences DataStore is already a dependency and `OnboardingStore` demonstrates repository-local
  persistence construction. No per-origin consent implementation exists.

## Origins and lifecycle map

Three origins can be present during a transition and must never be conflated:

1. **Observed origin** — canonical `SiteOrigin` parsed from the latest main-frame page-start URL.
2. **Discovery origin** — the exact origin captured when a discovery generation begins and used by
   transport and validation.
3. **Active origin** — the origin stored in `BrowserMode.Integrated` beside its trusted configuration.

On each main-frame start, increment a browser-owned generation, set regular mode unless the new
origin equals the active origin, and begin discovery. An available outcome must carry its origin and
generation; activation is eligible only when both still equal current state. Same-origin callbacks
retain integrated mode. A subdomain, parent, sibling, different port, scheme transition, malformed
URL, or unsupported scheme is a change and drops the skin. Registrable domain is display-only.

The required transition cases are regular → candidate → integrated, integrated A → regular B while
B is pending/unavailable/denied, and integrated A → regular B → integrated B after independent
acceptance. There is deliberately no direct inheritance from A to B.

## Consent boundary

ADR-011 requires Allow and Never to persist per complete origin; Not now is session-only. Although
`PRIV-001` owns the global toggle, settings management, and clear-data UI, SKIN-004 must land the
minimal durable origin-decision store because activation without it violates the accepted ADR. Use
a closed `SiteConsentDecision` (`ALLOW`, `NEVER`) persisted under a collision-free encoding of the
canonical origin. Absence means ask. The store API consumes `SiteOrigin`, not strings, and exposes
suspending reads/writes; settings enumeration/removal remains for `PRIV-001`.

Consent UI is browser-owned. Trusted site name may provide context, but the immutable registrable
domain and explanation of retained address/security controls are browser-authored. A pending prompt
must be cleared on origin/generation change. Allow persists then activates only if still current;
Never persists and remains regular; Not now persists nothing and remains regular.

## Influence and browser-owned remainder

After validation and consent, the manifest may influence only the already-closed SiteSkin surfaces:
trusted title/subtitle, bounded decoded logo, projected colour roles, capped navigation/menu labels
and local icons, and allow-listed typed actions. It cannot influence whether consent is required,
origin comparison, generation checks, domain/TLS text or visibility, dialog wording, WebView policy,
generic intents, Android permissions, or regular-mode fallback.

Resolved internal navigation and refresh can use `BrowserWebViewController`. External HTTPS,
phone, email, and map effects must become the existing typed `ExternalNavigation` confirmation
rather than launch directly. Share needs a browser-owned callback because the current external model
does not represent Android shares. Open-menu changes only local UI state. Unknown/inconsistent
resolution returns null and does nothing.

## Files likely affected

| File | Purpose |
|---|---|
| `app/.../browser/BrowserState.kt` | Pure origin-bound retain/drop/activate transitions and generation/candidate state. |
| `app/.../siteskin/ManifestDiscoveryCoordinator.kt` | Publish request origin/generation with outcomes for stale-result checks. |
| `app/.../siteskin/SiteConsentStore.kt` | Minimal origin-keyed durable Allow/Never contract and DataStore implementation. |
| `app/.../browser/BrowserScreen.kt` | Runtime orchestration, consent sheet, integrated composition, asset work, and typed effect dispatch. |
| `app/.../browser/SecurityPresentation.kt` | Derive browser-owned identity for both regular and integrated modes. |
| `app/src/main/res/values/strings.xml` | Browser-authored consent and integrated interaction labels. |
| `app/src/test/.../BrowserStateTest.kt` | Same-origin retention and cross-origin/malformed origin drop negative controls. |
| `app/src/test/.../ManifestDiscoveryCoordinatorTest.kt` | Origin/generation attribution and supersession coverage. |
| `app/src/test/.../SiteConsentStoreTest.kt` | Exact-origin consent isolation and decision semantics. |
| `app/src/test/.../SiteSkinRuntimeTest.kt` | Candidate/consent/stale/swap and closed action-effect pure tests. |
| `app/src/androidTest/.../SiteSkinRuntimeInstrumentedTest.kt` | Compile and, where a device exists, exercise integrated composition and consent UI. |

Exact factoring may add a small pure runtime coordinator/model rather than overload Compose; the
plan should keep lifecycle/security decisions deterministic and JVM-testable.

## Risks and controls

- **Stale asynchronous publication:** carry origin plus monotonic generation through discovery and
  recheck at the state transition, even though coroutine cancellation also exists.
- **Persistence key collision/leak:** encode full canonical origins reversibly or hash them; never
  use registrable domain or host alone.
- **Brand flash during redirect:** drop on page start before invoking discovery.
- **Composable complexity:** isolate pure transition and effect resolution from UI state/rendering.
- **Asset race:** cancel asset work on every activation change and ignore results not bound to the
  active origin/configuration.
- **Test environment:** deterministic origin/swap cases belong in JVM tests. Android test sources
  must compile; runtime instrumentation and screenshots require a connected device or `/dev/kvm`.

## External dependencies and migrations

No new library or core/schema change is required. A new preferences DataStore namespace is a new
local persistence surface but has no legacy migration because the feature has never shipped. Its
format must be additive so `PRIV-001` can enumerate/reverse decisions later.

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
