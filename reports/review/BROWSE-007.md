# Review: BROWSE-007
Date: 2026-08-15
Status: RESOLVED

## Summary

The implementation respects the browser/core boundary and keeps all new data app-private, bounded,
and revalidated. The URL identity and title-presentation separation is strong. One correctness gap
remains: Android may deliver `onPageFinished` after a main-frame error, and the current callback
would record that failed navigation despite the PRD's successful-completion requirement.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — Android persistence/callbacks stay in `:app`; `:siteskin-core` is unchanged. |
| State ownership | PASS — store and favourite decisions are browser-owned; tabs only address completion guards. |
| Persistence | PASS — versioned, bounded codec revalidates each entry independently. |
| Clear integration | PASS — history joins aggregate failure isolation while favourites are explicitly excluded. |

## Security

| Property | Assessment |
|---|---|
| URL allow-list | PASS — canonical absolute HTTP(S), no credentials, fragment removed. |
| Untrusted title | PASS — sanitized/bounded before persistence and never used as identity/destination. |
| Manifest isolation | PASS — integrated and regular menus use browser-authored actions outside manifest models. |
| Privacy/network | PASS — app-private preferences only; no network/permission/client change. |
| Failed-load exclusion | PASS — the matching failed URL cannot emit successful completion. |

## Findings

### FINDING-1 · Medium · PRD acceptance 1
**File:** `app/src/main/java/app/webora/browser/web/HardenedWebViewClient.kt`

Resolved in `TASK-FIX-1`: `onReceivedError` records the failed main-frame URL and the matching
`onPageFinished` no longer publishes `onMainFrameCompleted`; a subsequent `onPageStarted` resets the
guard.

Fix: track the failed main-frame URL inside the client, reset on `onPageStarted`, and suppress the
matching completion. Add a negative control that drives start → error → finish and proves no
completion, while the next successful navigation still completes.

## Not findings

- SharedPreferences writes use `apply()`. The UI updates from the in-memory operation immediately,
  while persistence durability follows Android's normal preferences contract; blocking UI on
  `commit()` would not improve the store's trust validation.
- Favourites survive Clear browsing data. This is deliberate product policy, stated before the user
  confirms, and tested independently rather than an omitted adapter.
- HTML titles appear on Home. They are untrusted bounded presentation only; canonical URL remains
  both identity and destination, and browser-authored action labels wrap the title.
- Recents deduplicate by full canonical URL rather than origin. This preserves distinct useful pages
  and matches the specified canonical-URL record identity; the underlying visit list is not erased.
- No network-capture library was added. The absence claim is structural: this ticket adds only an
  app-private preference adapter and no transport dependency/call site.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `BrowsingRecordStoreTest.kt` | 8 JVM tests | schemes, canonicalization, hostile titles, corruption, bounds, ordering, persistence, clear semantics |
| `HardenedWebViewClientTest.kt` | callback tests plus existing policy tests | successful completion URL/title and failed-finish negative control |
| `BrowsingDataCleanerTest.kt` | 4 coroutine tests | ordering, failure continuation, history adapter, trace |
| `HomeScreenTest.kt` | 3 compiled Compose tests | empty/populated sections, exact URL, explicit remove |
| `BrowserChromeTest.kt` | compiled Compose test | dynamic browser-owned favourite command |

## Verdict

RESOLVED — FINDING-1 is fixed by `TASK-FIX-1`; focused tests, detekt, and the complete pre-commit
gate pass. Ready for QA.
