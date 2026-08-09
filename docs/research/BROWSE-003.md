# BROWSE-003 research

Status: RESEARCH_READY

## Existing seams

BROWSE-002 provides `BrowserMode.Home`, immutable `BrowserState`, strict `AddressResolver`, `BrowserScreen`, and a controller around the hardened WebView. `MainActivity` currently creates state in Regular mode with `https://example.com`, so Home exists in the model but is not a rendered or launchable destination. Material 3 is already available; no navigation or persistence library is configured.

Android-touching code follows the repository's thin-wrapper/pure-function pattern because Robolectric is forbidden. A small preferences wrapper can read/write the onboarding flag while a pure launch reducer and immutable UI models remain covered by local JVM tests.

## Origins and browser-owned boundary

Home and onboarding are local browser UI and have no serving origin. A suggestion becomes an origin only after the user selects it and the existing resolver accepts an absolute HTTPS target. Recents and favourites have no backing source in this ticket and must display empty states rather than invented browsing data.

No manifest is fetched or interpreted. Suggested names, descriptions, and destinations are compiled browser-owned product data. They must not accept remote artwork, redirects as identity, arbitrary schemes, or manifest-controlled labels. After navigation, page content owns only the rendered document; existing browser callbacks observe it and can produce Regular mode but cannot rewrite Home or onboarding.

## Relevant code

| Path | Why it matters |
|---|---|
| `app/.../MainActivity.kt` | Composition root and first-launch decision. |
| `app/.../browser/BrowserScreen.kt` | Existing browser chrome/WebView composition to extend with Home. |
| `app/.../browser/BrowserState.kt` | Existing reducer and navigation command seam. |
| `app/.../browser/AddressResolver.kt` | Required allow-listed route for typed and suggested navigation. |
| `app/.../web/HardenedWebView.kt` | Renderer remains unchanged and is created only for browsing mode. |
| `app/src/main/res/values/strings.xml` | User-visible and accessibility copy. |

## Prior decisions

ADR-008 requires the sealed Home/Regular/Integrated model. ADR-006 requires domain and TLS affordances to remain browser-owned once branded chrome exists, and ADR-011 reserves per-origin SiteSkin consent for later runtime work. The roadmap scopes this ticket to mockup screens 1–2: onboarding, recents, favourites, and suggested integrations.

## Risks and obligations

- A hard-coded suggestion could bypass URL safety → validate the entire catalogue through an HTTPS-only pure constructor and route selections through `AddressResolver`.
- Preferences could make tests Android-dependent → isolate `SharedPreferences` behind a thin store and test pure launch decisions.
- Creating a hidden WebView on Home would load network content unexpectedly → render the WebView only outside Home mode.
- Decorative carousel state could be inaccessible or lost on rotation → use saveable page state, explicit progress semantics, text labels, and 48dp actions.
- Pretending temporary sample data is history would mislead users → use explicit empty states until persistence has a dedicated contract.

## Open questions

None. History/favourite mutation and SiteSkin first-use consent remain explicitly deferred to their owning tickets.
