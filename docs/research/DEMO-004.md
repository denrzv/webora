# DEMO-004: Research
Status: RESEARCH_READY

## Question

Which shipped controls, local-data seams, and canonical frames can form one honest browser-first
walkthrough without adding a demo path or extending the evidence harness?

## Origins involved

- `https://example.com` is the deterministic ordinary HTTPS destination already used by CI-007. Its
  page decoration is untrusted and irrelevant; Webora's observed `Secure · example.com` identity and
  regular shell are the acceptance signals.
- `https://denrzv.github.io` is the existing Bloom Flowers reference origin. Its manifest remains
  untrusted until the complete validator and consent flow accept it for that exact origin.
- There is no second SiteSkin origin. `DEMO-002` remains descoped, and the journey demonstrates mode
  continuity by switching between an ordinary tab and Bloom rather than inventing a skin-swap site.

## Manifest-controlled surface

After validation and consent, Bloom may provide bounded branding, closed semantic navigation items,
and typed quick actions within its site surface. It does not choose tab titles, Recent/Favourite
storage, walkthrough prose, canonical filenames, evidence captions, ordinary destination, browser
Back, domain/TLS identity, or Android navigation.

## Browser-owned remainder

- The Home address field and persistent Back/Forward/Reload/Home/Tabs/More shell provide every
  walkthrough transition.
- `BrowserSession` retains independent tab state and caps the session; the switcher labels derive
  from browser-observed destinations rather than manifest branding or editable text.
- `BrowsingRecordStore` records successful main-frame visits locally and exposes real Recents and
  Favourites; clear-browsing-data owns their deletion.
- Exact observed origin plus consent decides SiteSkin activation. Integrated chrome retains fixed
  browser Back and security identity, while switching to the ordinary tab restores regular chrome.
- Android owns gesture or three-button Back/Home/Recents and system insets. Webora does not draw
  substitutes; similarly named browser controls operate inside the current browser tab.
- The screenshot guard owns evidence acceptance. Page text and manifest labels never decide whether
  a frame demonstrates regular or integrated mode.

## Relevant files

| Path | Why it matters |
|---|---|
| `docs/BACKLOG.md` | Defines DEMO-004 scope and acceptance, including two tabs/local data. |
| `docs/DEVELOPER_PLAN.md` | Defines the three navigation layers and M8 exit story. |
| `docs/INSTALL.md` | Current product tour starts at SiteSkin and needs a browser-first entry. |
| `docs/SCREENSHOTS.md` | Defines the four canonical frames and their evidence limitations. |
| `app/src/main/java/app/webora/browser/browser/BrowserChrome.kt` | Visible regular shell, tabs and favourite commands. |
| `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt` | Composes Home, tabs, records and mode-specific chrome. |
| `app/src/main/java/app/webora/browser/browser/BrowserSession.kt` | Non-empty bounded independent-tab authority. |
| `app/src/main/java/app/webora/browser/browser/BrowsingRecordStore.kt` | Local history, Recents and Favourites authority. |
| `app/src/main/java/app/webora/browser/siteskin/SiteSkinTopBar.kt` | Fixed integrated Back and security identity. |
| `app/src/androidTest/java/app/webora/browser/visual/LiveSiteScreenshotTest.kt` | Produces Home, consent, integrated and regular evidence. |

## Prior art

- BROWSE-006 supplies isolated tabs and safe restoration; BROWSE-007 supplies local browsing records.
- UX-011 makes the browser shell persistent; UX-012 makes regular and SiteSkin chrome mutually
  exclusive while retaining browser escape/security.
- CI-007 adds the fourth ordinary frame and explicitly does not use page decoration as acceptance.
- ADR-004 and ADR-006 keep origin equality and domain/TLS presentation browser-owned; ADR-011 keeps
  consent browser-owned.

## Risks

- **Evidence overclaim** → list exactly what each frame shows and explicitly call tabs/records an
  installed-build interaction rather than an uncaptured frame.
- **Ambiguous “Back”** → distinguish Android system Back from Webora's in-app browser Back and tell
  the reviewer to use labelled Webora controls for the reference flow.
- **Live-site drift/outage** → say that Bloom safely remains regular if discovery/validation fails;
  never suggest bypassing consent or using the Inspector to force integration.
- **Local state from an earlier run** → begin from Home/new tab and phrase Recent/Favourite results
  as records created by the walkthrough, not pre-seeded fixtures.

## Open questions
None.
