# SKIN-002: Research
Status: RESEARCH_READY

## Question
How the top bar can consume existing trusted runtime values while making the origin/TLS identity
structurally non-optional and keeping Compose testable without Android-framework unit tests.

## Origins involved
- The committed main-frame serving origin is parsed into `SiteOrigin`; its canonical scheme and
  registrable domain are browser observations, not manifest values.
- `branding.logoUrl` is already normalized to the exact serving origin by core. `BrandAssetLoader`
  and `OkHttpBrandAssetSource` independently enforce that same-origin boundary before returning a
  decoded bitmap. No new transport is needed.
- No subdomain, CDN, redirect origin, or edited address-field value is eligible to supply identity.

## Manifest-controlled surface
- Trusted `toolbar.title` and `toolbar.subtitle`, already length-clamped by `SecurityValidator`.
- A validated same-origin PNG/WebP logo, or the site-name-derived monogram produced by the browser.
- The closed `SiteSkinColorScheme` roles produced from validated branding by `SiteSkinTheme`.
- The site cannot influence top-bar dimensions, ordering, typography, semantics, TLS wording,
  displayed domain, overflow capability, or whether the security region is present.

## Browser-owned remainder
- The `SecurityPresentation` derived from the committed `BrowserMode` supplies both registrable
  domain and transport state. The render model should require this value rather than make either
  field nullable.
- Domain and TLS indicator occupy a dedicated security row/region with browser-authored accessible
  wording. Branding is a sibling, never a replacement.
- Logo slot size, `ContentScale.Fit`, clipping, title/subtitle line limits, ellipsis, and layout
  weights are constants in the component.
- Overflow/menu and security-sheet behavior remain browser-owned. This ticket may display a fixed
  overflow affordance, but `SKIN-003`/existing regular chrome own menu contents and actions.
- Integrated-mode activation remains `SKIN-004`; this ticket exposes the top bar and pure model but
  must not infer activation from a manifest result.

## Relevant code
| Path | Why it matters |
|---|---|
| `app/.../browser/SecurityPresentation.kt` | Existing pure seam deriving eTLD+1 and TLS state from committed browser mode. |
| `app/.../browser/BrowserScreen.kt` | Existing regular address/security chrome and future composition site; must remain unchanged by this ticket. |
| `app/.../siteskin/SiteSkinTheme.kt` | Closed trusted colour projection; only eligible source of site colours. |
| `app/.../siteskin/BrandAssetLoader.kt` | Closed bitmap/monogram result after same-origin, format, size, and decode checks. |
| `app/.../siteskin/BrandAsset.kt` | Pure brand limits and monogram behavior. |
| `siteskin-core/.../model/SiteSkinConfiguration.kt` | Trusted toolbar/site fields; constructors remain validator-only. |
| `app/src/test/.../siteskin/` | JVM pure-model tests belong here; Compose/Android layout behavior can be covered by instrumentation compilation/tests. |
| `app/src/androidTest/.../siteskin/` | Device Compose assertions for fixed logo bounds and visible security content, unavailable at runtime without a device. |
| `app/src/main/res/values/strings.xml` | Browser-owned TLS and accessibility wording. |

## Prior art
- ADR-006 requires an always-visible registrable domain and TLS indicator in browser-owned
  typography/contrast, with branding confined beside it.
- ADR-004 and `CORE-001` define `SiteOrigin`, eTLD+1, and the mixed-script signal. This ticket does
  not reparse URLs or accept display identity from the page.
- `spec/SPEC.md` §§3 and 8.3 define same-origin logos, monogram fallback, and the deliberate absence
  of `toolbar.showDomain`.
- `NET-003` bounds and decodes PNG/WebP assets off-main-thread. `SKIN-001` supplies light/dark closed
  schemes. Both are complete prerequisites.
- The existing regular bar already proves the pure `SecurityPresentation` seam; the SiteSkin bar
  should reuse it rather than invent a parallel origin display rule.

## Risks
- Branding crowds security identity → separate required render-model fields and fixed weighted
  regions; test long text and extreme bitmap aspect ratios.
- A Compose-only test cannot prove the protection without a device → add a JVM negative-control test
  over a pure immutable model whose security presentation is mandatory, plus compile device layout
  tests when practical.
- Bitmap is an Android type → keep it at the final rendering edge; state creation must not inspect
  intrinsic dimensions or execute decode/network work.
- Site colours could reduce security legibility → consume only the guarded projected colour roles,
  and use a browser-owned content role for all identity text/iconography.
- Premature integration could accidentally activate a skin on validation alone → do not modify
  `BrowserMode`, discovery callbacks, or `BrowserScreen` activation in this ticket.

## Open questions
- Whether the existing mixed-script signal needs a distinct warning icon is deferred to
  `HARDEN-002`; the domain must still be shown here. No open question blocks this ticket.
