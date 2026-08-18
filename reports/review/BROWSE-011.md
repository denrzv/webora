# Review: BROWSE-011
Date: 2026-08-18
Status: RESOLVED

Issue: [#116](https://github.com/denrzv/webora/issues/116)
Reviewed at `e532391` on `claude/bloom-flowers-reference-y5ybs7`, based on `origin/main` `68ede98`.

## Summary

A browser-owned Refresh for protected integrated mode, placed on a new control row inside the
expressive header rather than in the brand row the issue sketched, because the brand row cannot
hold it. One pure `refreshAction(BrowserState)` owns what refreshing means for both chromes. Five
commits, 21 files, 1,396 insertions.

The structural claim worth checking is the negative one: no renderer, event-routing, discovery,
consent, capture or protocol code was edited, so every cross-tab and lifecycle criterion in the
issue is met by *not* adding a second path. The diff bears that out — `RendererOwnership.kt`,
`BrowserWebViewController.kt`, `HardenedWebView.kt`, `ManifestDiscoveryCoordinator.kt`,
`ScreenEvidenceGuard.kt` and `spec/` are untouched.

## Architecture

| Concern | Assessment |
|---|---|
| Placement | Decided by measurement, not taste, and the measurement is in the source where the next reader will hit it. The three rejected candidates each fail a *number* — 108 dp for a chip wanting 121, 46.7 dp against a 48 dp target — rather than a preference. |
| One owner | `refreshAction` is the only constructor of the decision, asserted by mechanism (constructing a `RefreshAction`) rather than by spelling. Both docks reach it. |
| Layering | The decision is pure and gate-drivable; the dispatch is a lambda over values already resolved for the selected tab. Same shape as `routeRendererEvent` and `rendererMountAction`, for the same reason. |
| Shared tile | Back and Refresh now share one browser-owned sub-surface declaration instead of two copies — the direction `UX-021` argues for. |
| Blast radius | `SiteSkinTopBar`'s signature change reached three test call sites and no other production code. |

## Security

| Property | Assessment |
|---|---|
| Manifest cannot reach the command | Presence is unconditional composition; icon and label are compiled resources; ground is a Webora token; enabled state reads only browser-observed values; callback is a controller method. Asserted by source scan (`SiteSkinTopBarContractTest`) and by a runtime parity case (`PageRefreshTest`). |
| Manifest cannot reach the decision | `refreshAction` takes no `BrowserMode` and therefore no `SiteSkinConfiguration`. The negative control — making it read `mode` — fails exactly the parity case. |
| The site's own `refresh` action | Untouched at `BrowserScreen.kt:299`, still dispatched through `ActionResolver`. Correct: it is the site's item. Collapsing the two would let a manifest reach browser chrome. |
| Origin binding | No new origin decision. `Retry`'s URL is `observeFailure`'s exact observed HTTP(S) round trip. Off-origin redirect tears integrated chrome down through the unchanged `forObservedOrigin` path. |
| Cross-tab | The dispatcher may not name `activeId`, reach the controller map, or write session state; both controls fail. Consequences return through the four framework callbacks under the id fixed when the renderer was built. |
| Capture policy | Untouched, so `CI-005`'s "may only ever make the harness refuse more" holds vacuously. |

## Findings

### FINDING-1 · Medium · a superseded acceptance criterion still stands (fixed, `TASK-FIX-1`)

**File:** `docs/prd/BROWSE-011.prd.md:96` (criterion 3)

Current:

> It creates no tab, replaces no renderer, and issues no `loadUrl` — the retained `WebView` reloads
> in place and `hostedUrl` is not rewritten by the act of refreshing.

The criterion was written before the plan decided the failure path, and never reconciled with it.
`RefreshAction.Retry` dispatches `controller.navigate(url)`, which *is* a `loadUrl`. So the shipped
implementation contradicts its own acceptance criterion on the path that criterion is least about.

This is not an implementation defect — the issue's actual requirements are that the URL not change
except by redirect, and that history not be duplicated by *browser state bookkeeping*, both of which
hold. A failed navigation typically commits no history entry, and `BrowserErrorPage`'s Retry has
issued exactly this `loadUrl` since `BROWSE-004`. But an acceptance criterion that the shipped code
violates is worse than no criterion: it trains the next reader to skip them.

Two things are wrong and both need fixing. The criterion must say what it means, scoped to the path
it is about. And the invariant it was actually reaching for — that **`reload()` does not rewrite
`hostedUrl`** — is real, load-bearing and untested: `BROWSE-010`'s `rendererMountAction` reads that
value on every mount to decide whether to re-issue a navigation, so a `reload()` that wrote
`webView.url` into it would reintroduce exactly the reload-on-switch defect `BROWSE-010` removed.
Nothing currently asserts it.

Fix: restate criterion 3 at the mechanism, and pin the invariant with a JVM case.
`BrowserWebViewController` is already constructed in `RendererMountActionTest`, so the case is cheap.

### FINDING-2 · Low · the Home dock answers a question it could ask (fixed, `TASK-FIX-2`)

**File:** `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt:278`

Current:

```kotlin
BrowserNavigationShell(
    canGoBack = false,
    canGoForward = false,
    canReload = false,
    …
```

The regular dock and the integrated header both now derive their reload state from `refreshAction`.
Home still states the answer as a literal, and there are two consequences.

The smaller one is drift: the single-owner claim is true of two call sites out of three, and the
third is free to disagree with the decision without anything failing.

The larger one is that `RefreshAction.None` is **unreachable in production**. Home is the only state
with a blank `displayedUrl`, and Home never asks. A branch nothing reaches is a branch nothing
exercises outside its own unit test — the shape `NET-004` records for a guard justified by reasoning
and blessed by a run that never touched it. It also means the ticket's answer to the issue's
requirement 7 ("safely unavailable … for example a pristine Home/new-tab state") is a coincidence
between two independently written `false`s rather than one decision.

Fix: `canReload = refreshAction(state) != RefreshAction.None`. It evaluates to the same `false`
today — that is the point — and makes the agreement structural. Add the assertion that the Home
call site reaches the decision, so a future literal fails.

## Not findings

- **`refreshAction` is evaluated twice per recomposition** (once for `canRefresh`, once inside the
  dispatcher on click). It reads two fields and allocates at most one small object; the alternative
  is hoisting a value that must then be kept in step with the lambda's captured `state`. Not worth it.
- **`assertFalse("BrowserControlRow(" in brand)` looks vacuous** — `declaration()` ends the slice at
  the next `private fun`, which *is* `BrowserControlRow`, so the string is absent by construction.
  It is not vacuous: the negative control inserts a call inside `BrandRow`'s body, before that
  boundary, and the case fails. Checked by running it.
- **The disabled-state instrumented case drives a state integrated mode cannot produce.** It asserts
  the *component* honours its flag, which is the component's contract regardless of which caller can
  currently produce it — and FINDING-2 makes the state reachable at a real call site.
- **Two "Reload" accessible names on one screen.** Checked: `browserMenuCommands()` offers
  `PAGE_INFORMATION`, `TABS`, `SETTINGS` and debug-only `INSPECTOR`; no dock or menu in integrated
  mode carries a reload. A site may publish its own `refresh` action, which appears in the hub as a
  separate surface with a manifest-supplied label.
- **`BrowserControlTile(tag, content = { content() })` reads oddly.** `Box` wants
  `@Composable BoxScope.() -> Unit` and the parameter is `@Composable () -> Unit`; the wrapper is
  required, not decorative.
- **The header is ~48 dp taller.** Accepted and recorded, with the arithmetic for why the
  alternative is worse. `UX-023` owns making that row responsive and may later move controls here.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `PageRefreshTest` | 6 | every decision row, manifest parity, and closure of the hierarchy. Two controls, failing 2/6 and 1/6. |
| `SiteSkinTopBarContractTest` | +4 (11 total) | no manifest value in the control, shared browser tile, applied tag, no competition with the brand row. Three controls, failing 3/11, 1/11, 2/11. |
| `BrowserChromeContractTest` | +1 (8 total) | one owner for the decision, both chromes reach it. Two controls, each failing 1/8. |
| `RendererHostContractTest` | +1 (5 total) | the dispatcher names no `activeId`, no controller map, no session write. Two controls, each failing 1/5. |
| `SiteSkinNavigationContractTest` | 1 reworked | Back's ownership, following the shared tile rather than matching one spelling. Its own control now fails on merit. |
| `SiteSkinTopBarTest` (instrumented) | +3 | displayed/enabled/48 dp/dispatch, disabled state, and the chip's 140 dp floor surviving at 320 dp × 200 %. |
| `TabRendererIsolationTest` (instrumented) | +1 | refresh in a failed tab A does not touch tab B, including a switch mid-flight. |
| Gap, closed by TASK-FIX-1 | — | nothing asserts `reload()` leaves `hostedUrl` alone, which `BROWSE-010`'s mount rule depends on. |

## Verdict

**Sound, with two fixes.** The trust boundary holds and is evidenced from both directions; the
placement decision is measured rather than argued; no capture, protocol or renderer code moved.
FINDING-1 corrects a criterion the code contradicts and closes a real untested invariant; FINDING-2
completes the single-owner claim and makes a currently-unreachable branch reachable.

Hosted acceptance for `CI-009` must be re-taken: the integrated frames photograph a header that is
now one row taller. No frame is added or removed and no capture policy changed.
