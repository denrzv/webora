# BROWSE-010 plan — A retained renderer loads when it is not already on its tab's page

Status: PLAN_APPROVED

## Overview

Replace `HardenedWebView`'s `if (existing == null) loadUrl(initialUrl)` with a pure mount-time
decision over browser-observed values. `BrowserWebViewController` gains one record — the URL the
renderer is known to be on — maintained from browser requests and from the renderer's own reported
URL. The composable becomes the thin wrapper; the decision is JVM-testable.

The decision returns a closed outcome rather than a `Boolean`, because "already on the page" still
has to settle a tab whose `isLoading` was set by `navigateFromHome` and which no callback will ever
clear.

## Flow

1. A tab returns to Home. `RegularBrowser` leaves composition, `detachFromParent()` runs, the
   controller keeps its `WebView` and keeps its hosted-URL record.
2. `navigateFromHome(url)` sets `displayedUrl`, `mode` and `isLoading = true`.
3. `RegularBrowser` re-enters; `AndroidView`'s factory runs and asks
   `rendererMountAction(hosted = controller.hostedUrl, target = initialUrl)`.
4. `Load` → `controller.navigate(target)`, which issues `loadUrl` **and** records `target` as hosted.
   The renderer's own `PageStarted`/`PageChanged`/`MainFrameCompleted` then keep the record current,
   including for in-page navigation the browser never requested.
5. `Settle` → no navigation; the host reports a synthetic completion for the page already on screen
   so the tab's `isLoading` terminates through the existing `WebViewEvent` path.
6. `Ready` → nothing. This is the tab-switch row, and it is the row that must stay silent.

## Origin and trust boundary

No website-controlled value participates. `target` is the tab's committed `displayedUrl`, produced by
`AddressResolver` under `BROWSE-002`. `hosted` is written from two browser-owned sources only —
`controller.navigate`, and the `url` field of the renderer observations `HardenedWebView` already
receives. Page content, `document.title`, and every manifest field are structurally absent from both
the function's signature and the controller's record.

`MainFrameFailed` deliberately does **not** update `hosted`: after a failure the browser's last
request stands, which is what stops the error tab reloading on every switch.

## Security and accessibility

- `BROWSE-001`'s hardening is untouched. `applyWebViewHardening` still runs once per renderer, on
  creation, before any load — the new decision changes only whether a URL is passed to an already
  hardened renderer.
- `BROWSE-006`'s retention is untouched. Nothing here destroys or recreates a renderer; `destroy()`
  keeps its two existing callers.
- `BROWSE-009`'s routing is untouched. The synthetic completion in step 5 is a `WebViewEvent` naming
  the same owner id the factory already captured, so it travels the same pure router and cannot
  address the selected tab.
- Terminating `isLoading` is the accessibility half: `browserAnnouncement` derives a polite
  completion announcement from state, and a flag that never clears makes that announcement a lie.

## The Back contract after a Home round trip — decided, not deferred silently

`loadUrl` appends, so after X → Home → Y the renderer holds `[X, Y]` while the tab's state knows only
Y, and `BROWSE-008` orders Back as renderer history first — so Back from Y reaches X.

**This ticket does not change that, and the reasoning is recorded rather than assumed:** the path is
unreachable today (Y never loads), so this fix *exposes* a pre-existing disagreement rather than
creating one; every available remedy depends on `WebView.clearHistory()`'s post-commit timing, which
is a framework fact no JVM gate can settle and this checkout has no device to settle either; and
whether Home is a history root belongs to `BROWSE-008`'s contract, beside the instrumented Back cases
that already exist there. A backlog entry carries the exposure forward.

## File-by-file plan

### Modified: `app/src/main/java/app/webora/browser/web/BrowserWebViewController.kt`

- Add `hostedUrl: String?`, private set, beside `tabId` and for the same reason: the renderer's owner
  is where facts about the renderer belong.
- `navigate(url)` sets it before `loadUrl`.
- Add `observed(url: String)` for the renderer's reported URL, called from the existing callbacks.
- `destroy()` clears it with the `WebView`.
- Add the closed mount outcome and the pure decision:

```kotlin
internal sealed interface RendererMountAction {
    data class Load(val url: String) : RendererMountAction
    data object Settle : RendererMountAction
    data object Ready : RendererMountAction
}

internal fun rendererMountAction(hosted: String?, target: String, isLoading: Boolean): RendererMountAction
```

`Ready` when `hosted == target` and the tab is not waiting; `Settle` when they match and it is;
`Load` otherwise. A blank `target` is `Ready` — a tab with no committed URL has nothing to request,
and `loadUrl("")` is not a navigation worth issuing.

### Modified: `app/src/main/java/app/webora/browser/web/HardenedWebView.kt`

- Take the tab's `isLoading` so the factory can ask the full question.
- Replace `if (existing == null) loadUrl(initialUrl)` with a `when` over `rendererMountAction`.
- Call `controller.observed(url)` from `onPageStarted`, `onPageChanged` and `onMainFrameCompleted`,
  beside the existing `currentObserver.value(...)` calls — not from `onMainFrameFailed`.
- `Settle` emits `WebViewEvent.PageChanged(owner, …isLoading = false)` for the page already on
  screen, using the same `toObservation` helper, so no new event type and no new state path.

### Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`

- Pass `state.isLoading` into `HardenedWebView`. Nothing else changes; the `key(controller.tabId)`
  placement inside `BROWSER_CONTENT_TAG` is `CI-003`'s measurement rectangle and does not move.

### Added: `app/src/test/java/app/webora/browser/web/RendererMountActionTest.kt`

One case per row of the research table, plus the loop guard:

1. a fresh renderer loads — the `existing == null` behaviour, preserved;
2. a Home round trip to a different URL loads — the defect;
3. a tab switch to the same URL is `Ready` — no reload;
4. an in-page navigation reported by the renderer leaves the switch silent;
5. a failed tab's switch is silent, because failure does not move `hosted`;
6. a same-URL Home round trip is `Settle`, not `Load` and not `Ready`;
7. evaluating again after `Load` is silent — the oscillation guard;
8. a blank target is `Ready`.

### Added: `app/src/test/java/app/webora/browser/web/RendererHostContractTest.kt`

Source structure, in `BrowserChromeContractTest`'s idiom, reading **executable lines only**
(`BROWSE-009`'s trap, hit three times in one ticket):

- the factory contains no `existing == null` load condition;
- `onMainFrameFailed`'s block does not call `observed(`;
- `HardenedWebView` reads no page title or manifest type.

Each with its intentionally-broken counter-example.

### Modified: `docs/BACKLOG.md`

New entry for the Home-round-trip Back exposure, with the reasoning above and its dependency on
`BROWSE-008`.

### Unmodified, deliberately

`BrowserSession`, `BrowserState`, `RendererOwnership.kt`, `BrowserBack.kt`, `applyWebViewHardening`,
`TabRendererIsolationTest`, and every `destroy()` call site.

## Verification

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:compileDebugAndroidTestKotlin` — explicitly; the gate never compiles `androidTest`
- `bash scripts/pre-commit-check.sh`
- Negative controls, run and recorded in the tasklist:
  1. restore `if (existing == null)` → the Home round-trip case must fail;
  2. drop `observed()` from `onPageChanged` → the in-page-navigation case must fail, proving the
     request-only record reloads on switch;
  3. update `hosted` on `MainFrameFailed` too → the failed-tab case must fail.

## Out of scope

Destroying renderers on Home, renderer pooling, `clearHistory()`, changing `BROWSE-008`'s Back
ordering, `AddressResolver`, and the tab limit.
