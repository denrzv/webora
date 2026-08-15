# Review: BROWSE-006
Date: 2026-08-14
Status: RESOLVED

## Summary

The pure session/restoration model is small, total and well covered, and the switcher keeps its
labels and lifecycle actions browser-owned. Review found two functional boundary gaps before QA:
an inactive retained renderer can still report through an active-tab update callback, and the
switcher has no entry point while the selected tab is Home. Renderer cleanup at screen disposal
also needs to be made explicit now that as many as eight WebViews can be retained.

## Architecture

| Concern | Assessment |
|---|---|
| Session authority | PASS — ordering, cap, selection and close live in one pure reducer. |
| Restoration | PASS — versioned bounded projection stores no trusted manifest/runtime object. |
| Renderer identity | PASS WITH FIX — controller-per-id retains history, but callback addressing is incomplete. |
| UI ownership | PASS WITH FIX — summaries/actions are browser-owned; Home lacks reachability. |

## Security

| Property | Assessment |
|---|---|
| Exact origin | PASS — restored pages downgrade to regular `SiteOrigin` observation. |
| Cross-tab publication | OPEN — a background WebView callback currently updates `activeId`. |
| Manifest influence | PASS — neither manifest branding nor editable input authors a tab label/action. |
| Persistence privacy | PASS — no page pixels, content, form state, manifest or configuration is encoded. |

## Findings

### FINDING-1 · High · cross-tab state isolation
**File:** `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`

Current: `RegularBrowser.onObservation` calls `session.updateActive`. A retained inactive WebView may
still navigate (timer, redirect, script) through the callback captured when it was visible, causing
its URL/mode/history capability to overwrite the newly active tab.

Fix: capture the renderer's tab id at composition and call `session.update(tabId)`. Add a negative
control that proves addressed background observations cannot alter selection or the other tab.

### FINDING-2 · High · create/switch reachability
**File:** `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`

Current: Home returns before regular or integrated menus compose. Since every new tab starts Home,
the Tabs command disappears precisely when users need it to return to another tab.

Fix: add a browser-owned Tabs action to Home without attempting the `UX-011` persistent-shell
redesign. Compile and exercise its semantics.

### FINDING-3 · Medium · renderer lifecycle
**File:** `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`

Current: close destroys one controller, but disposing `BrowserScreen` abandons the retained map
without explicitly destroying up to eight WebViews.

Fix: dispose every retained controller when the screen leaves composition, while preserving normal
detach/reattach behaviour during tab selection.

## Not findings

- WebViews sharing cookies/storage is not a tab-isolation failure: this ticket promises independent
  renderer history and chrome, not incognito profiles. `BROWSE-007` owns local browsing records.
- Restoring a page as regular rather than integrated is intentional. Serializing trusted manifest
  configuration would make stale remote input trusted after recreation.
- The eight-tab restore truncation is not silent eviction of a live session; restored saved state is
  untrusted bounded input, and the cap is applied before any renderer exists.
- Browser-authored `Home` and `Page` model fallbacks are not manifest-controlled strings. They are
  deliberately excluded from the snapshot and address field.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `BrowserSessionTest` | 8 JVM tests | create/select/close/cap/addressed isolation |
| `BrowserSessionSnapshotTest` | 6 JVM tests | safe round-trip, downgrade, malformed/duplicate/bound input |
| `TabSwitcherModelTest` | 3 JVM tests | ordering, fallback and remote/editable-text negative controls |
| `TabSwitcherTest` | compiled instrumentation | selected semantics and visible disabled cap |

## Verdict

RESOLVED — `TASK-FIX-1` addresses callbacks by owner id, `TASK-FIX-2` makes the shared switcher
reachable from Home, and `TASK-FIX-3` destroys every retained renderer at screen disposal. Each fix
passed the full pre-commit gate in its own commit; no open findings remain.
