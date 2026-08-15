# BROWSE-006: Implementation plan
Status: PLAN_APPROVED

## Overview

Introduce a pure, non-empty `BrowserSession` reducer first; add a versioned, primitive snapshot at
the same boundary; then make `BrowserScreen` render identity-keyed per-tab runtimes and expose a
browser-owned tab switcher. The pure model is the authority for ordering, selection, cap and
restoration. Compose and WebView code project that authority rather than reimplementing it.

The maximum is eight live tabs. This is a conservative explicit bound on expensive WebView
instances, not a cache size: reaching it disables creation and never evicts user state.

## Flow

1. `BrowserSession.fresh()` creates one selected Home tab with a monotonically generated opaque id.
2. Create appends and selects a Home tab if below eight; select changes only `activeId`; close uses
   the deterministic following-else-previous rule and recreates Home when closing the last tab.
3. Page/WebView observations update only the addressed tab id. The active UI reads only
   `session.activeTab`; callbacks carrying a stale or closed id are ignored.
4. Each tab owns a controller and an identity-keyed renderer slot. Selecting changes visibility,
   not renderer identity or URL, so live WebView history is retained. Closing disposes/destroys that
   tab's renderer.
5. A versioned snapshot projects only Home or a committed canonical HTTP(S) page URL plus ordering
   and selection. Decode validates and bounds every field and restores page tabs in regular mode.
6. The switcher lists browser-derived tab summaries, selected state and close controls. New tab is
   visibly disabled at eight with an explanatory browser-authored message.

## Trust and origin boundary

The session is entirely browser-owned. Website/manifest data cannot choose ids, order, selection,
count, lifecycle, action labels or persisted shape. A renderer observation can update only its own
tab and still passes through `BrowserState.observe`, where `SiteOrigin` performs canonical parsing.

`BrowserMode.Integrated` remains ephemeral trusted state scoped to one tab id and exact observed
origin. It is never encoded. A restored URL becomes `BrowserMode.Regular(origin)` and must traverse
normal discovery, consent lookup, validation and exact-origin activation again. Async discovery and
asset results publish only when both owning tab id and generation/configuration are still current.

Tab labels use browser-authored Home/Page text and a bounded registrable domain derived from the
observed origin; manifest title/subtitle and editable address input never label browser actions.

## Data and fallback

- `BrowserTab`: opaque `Long` id plus immutable `BrowserState`.
- `BrowserSession`: ordered non-empty list, active id and next-id allocator; constructor hidden
  behind validated operations.
- Snapshot wire shape: version, active id, next id, and at most eight entries of `(id, kind, url?)`.
  Compose saves only primitive lists supported by Android saved state.
- Unsupported version, malformed fields, duplicate/non-positive ids and unsafe URLs are dropped or
  cause a fresh-session fallback. An invalid active id selects the first valid entry.
- Page snapshots accept only absolute HTTP(S) URLs for which `SiteOrigin.parse` succeeds. Stored
  loading, errors, editable text and native/manifest objects do not exist in the wire shape.

## Security and privacy

- Exact-origin SiteSkin activation remains in `BrowserState`; tab identity adds a second mandatory
  publication guard rather than weakening it.
- Per-tab discovery generation, pending consent, brand asset and native action state cannot display
  under another active id. Switching dismisses transient UI that is unsafe to rebind.
- Closing promptly destroys the WebView. Clearing browsing data targets every live controller.
- Restoration does not persist page pixels/content, WebView bundles, manifests, configuration,
  assets, form state or pending native operations.

## File-by-file plan

### New: `app/src/main/java/app/webora/browser/browser/BrowserSession.kt`

Pure tab/session domain model, maximum, total reducers and addressed state update.

### New: `app/src/main/java/app/webora/browser/browser/BrowserSessionSnapshot.kt`

Versioned primitive encoding/decoding and safe regular-mode restoration.

### New: `app/src/main/java/app/webora/browser/browser/TabSwitcher.kt`

Browser-owned accessible switcher and bounded summary projection.

### New tests

`BrowserSessionTest`, `BrowserSessionSnapshotTest`, `TabSwitcherModelTest`, and focused Compose
instrumentation for switcher semantics and live renderer identity/recreation.

### Modified: `BrowserScreen.kt`

Replace the singleton state/controller with the saved session and identity-keyed tab runtimes;
address all observations/discovery/publication by tab id; wire switcher actions; clear transient
state on tab selection; clear all live controllers.

### Modified: `BrowserChrome.kt`, resources

Add the real Tabs entry to the current dock/menus without pre-empting `UX-011`; add browser-authored
labels, descriptions, limit copy and test tags.

### Modified: `HardenedWebView.kt`, `BrowserWebViewController.kt`

Support explicit visible/hidden tab hosting, lifecycle-safe attach/detach and prompt destroy on
close without navigating on selection.

### Modified: `BrowsingDataCleaner.kt` and tests

Accept a browser-owned clear-all-renderers adapter while retaining failure aggregation.

### Modified: workflow/product docs

Record task results, review, QA, validation, the final session contract in `CLAUDE.md`, and mark the
roadmap item complete only after validation.

## Tests

- Pure negative controls: remove the cap, copy active state during selection, serialize integrated
  mode, accept an unsupported scheme, choose the wrong close neighbour, or accept duplicate ids;
  each must make a named test fail.
- `./gradlew :app:testDebugUnitTest` after each code task.
- Focused `compileDebugAndroidTestKotlin` for Android-facing work; device execution only when a
  connected device exists.
- `./gradlew detekt` and `bash scripts/pre-commit-check.sh` before every task commit.

## Rollout / versioning

The snapshot starts at version 1 and is deliberately restorable only by this app version. Unknown
future versions fall back safely. No protocol, manifest schema, storage migration or feature flag is
required.

## Open questions

None.
