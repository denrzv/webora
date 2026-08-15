# BROWSE-007: Research
Status: RESEARCH_READY

## Current product seams

- `HomeScreen.kt` renders static Recent sites and Favourites empty cards, then suggested sites. It
  already receives a validated `onNavigate(String)` callback and is the natural projection point.
- `BrowserScreen.kt` owns the active `BrowserSession`, all retained tab controllers, settings, and
  the clear-data flow. It receives committed WebView observations but no page-title/completion event
  dedicated to durable history.
- `HardenedWebViewClient.kt` distinguishes main-frame callbacks. `onPageFinished` supplies the final
  URL and the `WebView` can supply its title; `doUpdateVisitedHistory` is not suitable for recording
  because it fires during reload/history mutation and would multiply visits.
- `BrowserState.observe` and `SiteOrigin` already reject invalid/non-HTTP(S) page identity. The new
  store needs its own total persisted-input validator because local storage becomes untrusted after
  backup, downgrade, corruption, or manual restoration.
- `BrowsingDataCleaner` clears cookies, WebStorage, every live WebView, manifest cache, consent, and
  inspector trace with failure aggregation. History should be another injected clear operation;
  favourites must deliberately remain outside it.
- Existing settings storage uses small SharedPreferences adapters with pure injectable interfaces.
  DataStore is available but a bounded, versioned string-set/list codec behind a pure repository is
  sufficient and keeps JVM tests fast without Android.

## Origins and manifest-controlled surface

History is recorded from browser-observed successful main-frame completion for any canonical
HTTP(S) page, including a page whose separately validated SiteSkin is active. `SiteOrigin` derives
the canonical origin. The stored navigation key is the full canonical page URL (fragment removed),
not a manifest URL, registrable-domain approximation, title, or origin-only key.

A page may indirectly supply its HTML title. That title is untrusted presentation only: strip
control/format characters, collapse whitespace, and bound it before persistence and display. It
never becomes the favourite key or destination. A missing/rejected title falls back to the
browser-observed registrable domain. A manifest cannot supply actions, timestamps, favourite state,
clear semantics, row order, or destination URLs.

Browser-owned UI authors Add/Remove/Open labels and invokes navigation only with a record that the
store decoded and revalidated. Favourites capture the exact current canonical URL when the user
acts; later page title changes cannot silently create a different favourite.

## Persistence and privacy boundary

- Persist a versioned bounded representation in app-private SharedPreferences: maximum 200 history
  visits and 100 favourites. Parse each entry independently and drop malformed/oversized/unsafe
  values rather than failing the whole store.
- A clock is injected. Ordering is visit time descending with a deterministic sequence/id tie-break;
  Home recents deduplicate canonical URL after ordering and cap at ten.
- Favourites are unique by canonical URL. Re-adding may refresh the bounded display title but cannot
  change the destination. Removal compares the canonical key, never display text.
- Clear browsing data removes history/recents but retains favourites. The confirmation text must say
  this before execution. No record leaves app-private storage and no new networking dependency or
  Android permission is needed.

## UI and accessibility

Home receives immutable recent/favourite models and renders real section cards only when populated.
Rows have browser-authored accessible Open and Remove favourite controls with existing 48 dp wrapper
components. Current-page favourite actions belong in the browser-owned overflow menus, including
integrated mode; they are unavailable on Home or invalid/uncommitted pages.

Stable tags should identify recent/favourite rows and current-page favourite actions. At 200% font,
labels should wrap vertically rather than competing in a dense horizontal action row.

## Tests and risks

- Pure JVM tests should prove canonicalization, fragments, ports, unsupported schemes, hostile
  titles, corrupt persisted entries, collection caps, duplicate recents, deterministic ordering,
  favourite identity and clear retention.
- A client/source-contract test should fail if recording moves to non-main-frame/history-update
  callbacks or if a Webora networking client is introduced.
- Compose instrumentation should cover populated/empty Home and Add/Remove semantics. This checkout
  has no connected device or `/dev/kvm`; compile instrumentation and report runtime/screenshots as
  an environment limitation.
- A completion may be reported more than once by provider quirks. Use a per-tab completed navigation
  guard so one document completion produces one visit; a new `onPageStarted` resets it.
- Page title may arrive late or be hostile. Sanitization and fallback must happen in the pure store
  boundary, never only in Compose.

## Likely affected files

- New: `browser/BrowsingRecords.kt`, `browser/BrowsingRecordStore.kt`, and focused JVM tests.
- Modified: `HardenedWebView.kt`, `HardenedWebViewClient.kt`, `BrowserScreen.kt`, `HomeScreen.kt`,
  browser menus, `BrowsingDataCleaner.kt`, resources, unit and Compose tests.
- Documentation: review/QA reports, `CLAUDE.md`, tasklist results, roadmap/backlog status.
