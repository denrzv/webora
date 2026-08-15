# BROWSE-007: Implementation plan
Status: PLAN_APPROVED

## Overview

Add a pure bounded browsing-record domain and an Android SharedPreferences adapter, record one visit
from successful main-frame completion, then project recents/favourites into Home and current-page
browser menus. Extend the existing aggregate clear operation with history only. Stored data is
revalidated on every read and no remote service is introduced.

## Flow

1. `onPageStarted` resets a tab-scoped completion guard. A successful `onPageFinished` publishes
   URL/title once for that navigation.
2. `BrowsingRecordStore.recordVisit` canonicalizes an absolute HTTP(S) URL, removes its fragment,
   derives `SiteOrigin`, sanitizes/bounds the page title, stamps the injected clock/sequence, and
   prepends a visit while keeping the newest 200.
3. `recentSites()` sorts deterministically, keeps the newest entry for each canonical URL, and caps
   Home output at ten without rewriting history.
4. Explicit current-page Add/Remove actions canonicalize the observed URL and update a maximum of
   100 favourites keyed by URL. Home opens only the stored canonical destination.
5. Clear browsing data injects `recordStore.clearHistory`; favourites are not passed to the cleaner.

## Trust and origin boundary

Only browser-observed successful main-frame HTTP(S) completions can create history. The canonical
full URL is the navigation authority; `SiteOrigin` is the origin authority. HTML titles are bounded,
sanitized untrusted labels and never identity or commands. SiteSkin manifests influence none of the
record key, destination, timestamp, ordering, favourite decision, controls, or clear semantics.

Persisted app-private data is untrusted input. The decoder is versioned, bounded before collection
construction, and validates every URL/origin/title/time field. Invalid entries are dropped. UI never
navigates raw preference text; it receives only validated domain records.

## Security and privacy

- Exact HTTP(S) allow-list; no `javascript:`, `file:`, `content:`, `intent:`, `data:`, credentials,
  protocol-relative URL, or malformed authority can enter a record.
- Strip fragments and user-info; normalize scheme/host/default port/path while preserving query.
- Sanitize and bound page titles before persistence, not merely at rendering.
- Bounded 200 visits, 100 favourites, 10 recents, URL/title/encoded-size limits prevent local
  storage amplification by remote pages.
- App-private persistence only. No permissions, sync, analytics, background work, favicon fetch, or
  browser-controlled host is added.
- Clear history participates in failure aggregation; confirmation explicitly says favourites stay.

## File-by-file plan

### New `BrowsingRecords.kt` and `BrowsingRecordStore.kt`

Trusted immutable record/model types, canonicalization and title sanitizer, pure repository,
versioned codec, caps, recents projection, favourite operations, and SharedPreferences adapter.

### Modified WebView callback files

Add a dedicated successful main-frame completion callback carrying observed URL/title. Keep it
separate from general observations and reset/deduplicate per tab in `BrowserScreen`.

### Modified `BrowserScreen.kt`, browser menus, and `HomeScreen.kt`

Construct/remember the local store, observe it as Compose state, record completions, render populated
sections, and expose browser-authored Add/Remove favourite actions for the current validated URL.

### Modified `BrowsingDataCleaner.kt` and privacy UI

Inject history clearing, preserve favourites, refresh Home models, and explain the distinction in
confirmation copy.

### Tests and documentation

Pure codec/store/canonicalization negative controls; callback, cleaner and UI model/contract tests;
compiled Compose instrumentation; final review, QA, architecture note, and roadmap status.

## Tests

- `./gradlew :app:testDebugUnitTest` after every code task.
- `./gradlew :app:compileDebugAndroidTestKotlin` for UI/callback integration tasks.
- `./gradlew detekt` after every code task.
- `bash scripts/pre-commit-check.sh` before each task commit.
- Connected instrumentation only if a device is present; do not provision a software emulator.

## Rollout / migration

The new preference file begins with codec version 1. Unknown versions decode as empty while keeping
the raw file available for a future migration. No existing user data changes and no protocol or
manifest schema changes.

## Open questions

None. Clear browsing data deletes history and preserves favourites; the confirmation makes that
choice explicit.
