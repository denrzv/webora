# Review: BROWSE-010
Date: 2026-08-17
Status: CLOSED

## Summary

The mount-time load condition moves from `existing == null` — "is this `WebView` new?" — to a pure
decision over the tab's committed target and the URL the controller knows its renderer is on. That
part is right, and the reason it is right is that the two questions stopped being the same when
`BROWSE-006` made renderers outlive their hosts.

One finding, and it is in the half of the fix that was least verifiable from here: the `Settle`
branch writes Compose state from inside `AndroidView`'s `factory`, and what it writes is a completion
the browser has not observed. Both halves of that are avoidable, and removing them makes the change
smaller.

## Architecture

| Concern | Assessment |
|---|---|
| Decision location | Correct. A rule inside `factory` is verified by nothing on a developer machine — `BROWSE-009`'s reason for `routeRendererEvent`, `NET-004`'s for `publishesBrandAsset`. `rendererMountAction` is drivable by the JVM gate. |
| Record ownership | `hostedUrl` sits on `BrowserWebViewController` beside `tabId`, so a closed tab takes it with the renderer and nothing separate has to forget it. Asserted. |
| Two write sources | Requests **and** reports. The report half is what makes in-page navigation silent on switch; without it the record drifts and every browsed-within tab reloads. |
| Failure excluded | `onMainFrameFailed` deliberately does not write. This is the row `TabRendererIsolationTest` drives, and it is what a `WebView.getUrl()` rule gets wrong. |
| Complexity | detekt's `LongMethod` pushed the client out into `reportingClient`; the never-inside-`onMainFrameFailed` rule is documented there, at the seam it constrains. |
| Blast radius | `BrowserSession`, `BrowserState`, `RendererOwnership`, `BrowserBack` and every `destroy()` call site untouched, as planned. |

## Security

| Property | Assessment |
|---|---|
| Inputs to the decision | `hosted` (browser-written) and `target` (`AddressResolver`'s output). No page content, no `document.title`, no manifest field — asserted structurally against `RendererMountAction.kt`. |
| Why it matters here | This decision chooses whether the browser re-issues a navigation **and to where**. A page-influenced input would be a page causing a navigation. |
| Hardening | `applyWebViewHardening` still runs once per renderer, on creation, before any load. Unchanged. |
| Retention | Nothing destroys or recreates a renderer. `BROWSE-006`'s live-history guarantee across tab switches is untouched. |
| Event ownership | The mount emission names the same `owner` captured for every other event, so it travels `routeRendererEvent` and cannot address the selected tab. `BROWSE-009`'s inventory assertion caught it and was raised deliberately. |

## Findings

### FINDING-1 · Medium · `Settle` writes Compose state during composition, and writes something unobserved
**File:** `app/src/main/java/app/webora/browser/web/HardenedWebView.kt:52-59`

```kotlin
RendererMountAction.Settle ->
    currentObserver.value(WebViewEvent.PageChanged(owner, toObservation(initialUrl, false)))
```

Two problems, either sufficient.

**It fabricates an observation.** `PageChanged(isLoading = false)` reaches `observePage`, which sets
`displayedUrl`, `addressText`, `mode`, `canGoBack`, `canGoForward` and clears `isLoading` — a
*completion the browser never observed*. Every other `WebViewEvent` in the app originates in a
framework callback. This one is the renderer host asserting that a page finished because it inferred
the renderer was already on it. That is the same category of move `CI-002` refuses one layer up: the
harness is not allowed to report a state it did not witness.

It is also wrong in a reachable case. Switching away from a tab **mid-load** and back gives
`hosted == target` (the request was recorded) with `isLoading == true` — so `Settle` fires and
reports completion for a page that is still loading.

**It writes Compose state from `factory`.** `factory` runs during composition; `currentObserver.value`
mutates the `session` `MutableState` that the same composition reads. Compose does not guarantee that
is safe, and this checkout has no device to find out.

Fix: make that case a real load. The tab believes it is waiting for a page, so give it one — the
framework then produces genuine `PageStarted`/`PageChanged` observations that clear `isLoading`
honestly. `Settle` disappears, the model returns to two cases, no synthetic event exists, and
`BROWSE-009`'s `EMITTED_EVENTS` goes back to 4.

```kotlin
internal fun rendererMountAction(hosted: String?, target: String, isLoading: Boolean) = when {
    target.isEmpty() -> RendererMountAction.Ready
    hosted != target || isLoading -> RendererMountAction.Load(target)
    else -> RendererMountAction.Ready
}
```

The mid-load switch now reloads rather than lying, which is the right direction: an honest extra
request beats a fabricated completion. The loaded-page switch — the row the acceptance criterion is
actually about — still has `isLoading == false` and stays `Ready`.

### FINDING-2 · Low · two classes now share the name `RendererHostContractTest`
**File:** `app/src/test/java/app/webora/browser/web/RendererHostContractTest.kt:16`

`BROWSE-009` already has `browser/RendererHostContractTest`. Two same-named classes in different
packages compile fine, but `--tests '*Renderer*'` runs both and a failure report names only the
simple class, which cost real time during this ticket's negative controls: control 1's second failure
read as coming from the new file and did not. Rename to `RendererMountContractTest`, which is also
what it actually asserts.

## Not findings

- **`Load` runs inside `factory`.** Unlike the `Settle` emission it writes no Compose state — it calls
  `controller.navigate`, which records a URL and calls `loadUrl`. It also has to be there: the load
  needs the renderer that was just attached.
- **The mid-load switch reloads after FINDING-1's fix.** Accepted, and better than the alternatives. A
  tab whose state says "loading" with no navigation in flight is the exact terminal condition this
  ticket removes, and the browser cannot distinguish that from a live in-flight load without reading
  `WebView.getProgress()` — which is the framework-state dependency the decision exists to avoid.
- **`hostedUrl` is a `var` with a private setter, not Compose state.** Deliberate. It is read once per
  mount inside `factory`; making it observable would invite recomposition on every page change for a
  value no composable renders.
- **The plan's second negative control did not fail the case it predicted.** Not a gap in coverage —
  the pure case proves the decision handles an in-page URL, the source scan proves the wiring that
  produces one still exists, and the row needs both. It is a gap in the *prediction*, recorded as
  such in the tasklist rather than quietly reworded.
- **`RendererMountActionTest` constructs a real `BrowserWebViewController` in a JVM test.** No Android
  method executes: `webView` is null on every path it touches, and `detachFromParent` returns early.
- **`ExpressiveBloomJourneyContractTest` is edited by a ticket that does not own it.** Carried
  deliberately on the user's decision after the failure was confirmed pre-existing on a clean
  worktree of `origin/main`. Its own negative control was run.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `RendererMountActionTest.kt` (new, JVM) | 9 | One per mount situation: fresh, Home round trip, tab switch, in-page navigation, failed tab, same-URL round trip, post-load re-evaluation, blank target, destroy. |
| `RendererHostContractTest.kt` (new, JVM) | 4 | The host asks the decision; failure does not write the record; all three reporting callbacks do; no page-authored type reaches the decision. Executable lines only. |
| `browser/RendererHostContractTest.kt` | unchanged assertions | `EMITTED_EVENTS` inventory raised deliberately; reverts to 4 under FINDING-1's fix. |
| `TabRendererIsolationTest.kt` | unedited | The reload-regression detector. Compiles; **not executed** — no device. |
| Negative controls | 4 | Three planned, all fired; plus one on the repaired `ExpressiveBloomJourneyContractTest` marker. |

## Verdict

**Resolved.** Both fixed in `TASK-FIX-1`.

FINDING-1 made the change *smaller*: `Settle` is gone, the model is two cases, no synthetic event
exists, and `browser/RendererHostContractTest`'s `EMITTED_EVENTS` returns to 4 — which is the shape
that was right before this ticket briefly widened it. The three negative controls were re-run against
the simplified decision rather than carried over from the pre-fix code, and each still fails only its
own assertion.

FINDING-2's rename to `RendererMountContractTest` also describes what the file asserts better than
the name it collided with.

Status: CLOSED
