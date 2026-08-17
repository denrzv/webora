# BROWSE-010 — Research

Status: RESEARCH_READY

## Question

What makes a retained renderer *stale for its tab* at mount time, expressed only in browser-observed
values, such that a Home round trip loads and a tab switch does not?

## Origins involved

None new. This ticket issues a navigation the browser had already decided on — `state.displayedUrl`,
which `AddressResolver` produced and `BROWSE-002` bounded — to a renderer that already exists. No
manifest, no discovery, no asset, no network policy is touched. Manifest discovery continues to
follow the resulting `PageStarted`, exactly as it does for any other navigation.

## Manifest-controlled surface

Empty, and it must stay empty. The staleness signal decides whether the browser re-issues a
navigation and to what URL. A page that could influence it could make the browser re-navigate — so
document content, `document.title`, and every manifest field are excluded by construction, not by
review. The only inputs are the tab's committed target and the browser's own record of what it last
asked the renderer for.

## Browser-owned remainder

Everything: when to load, what to load, whether a switch reloads, and what Back means afterwards.

## The mechanism, precisely

Four facts, and only the fourth is a bug:

1. `BrowserScreen.kt:261` composes `RegularBrowser` **only** in the `else` branch of
   `if (state.mode == BrowserMode.Home)`. Going Home removes the whole subtree, disposing
   `HardenedWebView` and running `DisposableEffect`'s `onDispose(controller::detachFromParent)`.
2. `BrowserWebViewController` keeps `webView` across that — deliberate, and required by
   `BROWSE-006`: selection must not destroy live back/forward history. `destroy()` is called only on
   tab close and on `BrowserScreen` disposal.
3. `onHome` (`BrowserScreen.kt:315-319`) resets the tab to `BrowserState()`; `navigateFromHome(url)`
   (`BrowserState.kt:110`) then sets `mode`, `displayedUrl`, `addressText` and `isLoading = true`.
4. Re-entering the subtree runs `AndroidView`'s `factory` again. `HardenedWebView.kt:66` reads
   `if (existing == null) loadUrl(initialUrl)`. `existing` is the retained `WebView`, so **no
   navigation is issued**. `isLoading` was set to `true` by step 3 and only a renderer callback can
   clear it; none can arrive. The spinner is permanent by construction.

`key(controller.tabId)` (`BrowserScreen.kt:721`) does not help here and is not at fault: the tab id
is unchanged across a Home round trip. `BROWSE-009` added that key for a different failure — one
composition slot serving two tabs.

## Why `existing == null` was the right condition until it wasn't

It answers "is this `WebView` new?" and it was equivalent to "does this renderer need the page?" for
as long as a renderer's life and its host's life coincided. `BROWSE-006` broke that equivalence when
it made renderers outlive their hosts, and nothing re-derived the condition. The question the factory
actually needs to ask is *"is this renderer already on the page this tab wants?"*, and identity of
the `WebView` object is a poor proxy for it.

## Candidate staleness signals

### A. `existing.url != initialUrl` — rejected

The obvious rule, and the one the issue warns about. `WebView.getUrl()` reports the framework's view
of the current document, and for a failed navigation that may be the failed URL, the previously
committed URL, or `about:blank` depending on how far the load got. `TabRendererIsolationTest` drives
exactly that case: tab A navigates to a **closed loopback port**, fails, shows the error page, and is
then switched away from and back to. If `url` does not equal `state.displayedUrl` for that tab, the
rule re-issues the failed navigation on every switch — which is the reload `BROWSE-009`'s acceptance
criterion 2 forbids, introduced by the ticket that was supposed to protect it.

It also depends on framework URL normalization (trailing slash, redirect target, fragment) matching
whatever `AddressResolver` produced, which is a second source of spurious inequality.

### B. "the tab was on Home" as a flag — rejected

A `Boolean` set on the Home transition and consumed at mount. It works for the reported reproduction
and answers the wrong question: it describes *how* the renderer became stale rather than *whether* it
is. Anything else that detaches a host without a Home visit — a future split view, a config change
that drops the subtree, a mode transition added later — is stale in exactly the same way and the flag
would not fire. It is also a second piece of state to keep in sync with the first.

### C. The browser's own record of what the renderer holds — selected

`BrowserWebViewController` records the URL the renderer is known to be on, maintained from two
browser-owned sources and nothing else:

- **what the browser asked for** — `navigate(url)`, and the mount-time load itself;
- **what the renderer reported** — the `url` inside `PageStarted` / `PageChanged` /
  `MainFrameCompleted`, which already flows through `HardenedWebView`'s callbacks.

The second half is what makes in-page navigation safe. A link click never passes through
`controller.navigate`, so a record of requests alone would drift from reality and reload on every
switch back to a tab the user browsed within. `MainFrameFailed` deliberately does **not** update it:
after a failed navigation the browser's last request stands, which is what keeps the error tab from
reloading on switch.

The mount rule is then `hosted == null || hosted != target`:

| Situation | `hosted` | `target` | Loads? |
|---|---|---|---|
| Fresh renderer | `null` | the page | **yes** — replaces `existing == null` |
| Home round trip | previous page | new address | **yes** — the defect |
| Tab switch, page tab | current page (from observations) | same | no |
| Tab switch, in-page browsing | the linked page (from observations) | same | no |
| Tab switch, failed tab | the requested URL (request, not report) | same | no |
| Same-URL Home round trip | that URL | that URL | no — see below |

## The one case the rule gets "wrong", and why it is right

Page X → Home → type X again issues no load, so the user sees X immediately with no spinner. The
address bar, mode and history all already say X, so the screen is correct and the outcome is
indistinguishable from a load that finished instantly. `isLoading` is the only loose end: step 3 sets
it true and nothing clears it.

**This is why the loading flag, not only the load, is part of the fix.** The terminal state has two
causes — no navigation, and a flag only a navigation can clear — and fixing one leaves a narrower
version of the same bug. The mount decision must therefore return *what to do*, including "already
there, settle the tab", rather than a bare `Boolean`.

## Criterion 6: the Back contract after a Home round trip

**Decision: this is a second defect, not this one, and it is not made worse here.**

`loadUrl` appends to the renderer's back stack, so after X → Home → Y the renderer holds `[X, Y]`
while the tab's browser state was reset by `BrowserState()` and knows only Y. `BROWSE-008` orders
Back as *live renderer history → native Home → platform exit*, and the renderer reports
`canGoBack = true`, so Back from Y reaches X — a page the user cleared by going Home. In a
conventional browser, Home is a new-tab page and Back from the first navigation returns to it.

Three reasons it stays out of this ticket:

1. **It is not reachable today and this ticket does not make it worse.** Y never loads, so there is
   no "Back from Y". The fix exposes a pre-existing disagreement between renderer history and
   `BROWSE-008`'s ordering; it does not create it.
2. **The available fixes are device-verifiable only.** `WebView.clearHistory()` is documented to be
   unreliable until the current page has committed, so any implementation needs a
   "clear after the next commit" state machine whose correctness is a framework-timing fact. This
   checkout has no emulator. `NET-004` is the standing warning about shipping exactly that.
3. **It is a `BROWSE-008` decision, not a rendering one.** Whether Home is a history root is a
   navigation-contract question that should be decided where that contract lives, with the
   instrumented Back cases that already exist there.

Recorded as a new backlog entry with this reasoning, so the exposure is written down rather than
discovered later.

## Relevant code

| Path | Why |
|---|---|
| `web/HardenedWebView.kt:36-68` | The factory holding `if (existing == null) loadUrl(initialUrl)` — the defect. |
| `web/BrowserWebViewController.kt:47-72` | Owns the retained `WebView`; the natural home for the hosted-URL record, beside `tabId` and for the same reason. |
| `browser/BrowserScreen.kt:261,315-319,721-731` | The Home branch, the Home reset, and the keyed host. |
| `browser/BrowserState.kt:110-115` | `navigateFromHome` sets `isLoading = true` — the flag with no clearer. |
| `browser/RendererOwnership.kt` | `routeRendererEvent`, the pure router; the shape this ticket's decision should copy. |
| `androidTest/.../TabRendererIsolationTest.kt` | The reload regression detector, and the error-tab case that rejects candidate A. |

## Testability

The decision must leave the `@Composable`, for `BROWSE-009`'s reason: the gate cannot drive Compose,
and a rule that lives inside `factory` is verified by nothing on a developer machine. A pure function
over `(hosted: String?, target: String)` returning a closed outcome is JVM-testable in milliseconds
and is the same thin-wrapper-over-pure-function shape as `publishesBrandAsset`,
`routeRendererEvent` and `candidateVerdict`.

What the gate **cannot** prove: that the framework issues the load, that the page paints, and that
`TabRendererIsolationTest` still passes on a device. Those stay instrumented evidence and are named
as outstanding — never promoted to a gate claim, per `A11Y-001`.

## Risks

1. **A reload on tab switch** — the regression this fix could become. Mitigated by sourcing `hosted`
   from observations as well as requests, and by a JVM case per row of the table above, including the
   failed-tab and in-page-navigation rows that candidate A fails.
2. **A load loop.** Loading sets `hosted = target`, and the resulting observations report the same
   URL, so the rule cannot re-fire. A case asserts the second evaluation is a no-op; without it the
   fix could oscillate on a redirect.
3. **`hosted` going stale on tab close.** The record lives on the controller, which is destroyed and
   removed with its tab, so there is nothing to forget separately.
4. **A comment participating in a source scan.** `BROWSE-009` hit this three times in one ticket. Any
   scan added here reads executable lines only.
