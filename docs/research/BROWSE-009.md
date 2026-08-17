# BROWSE-009: Research

Status: RESEARCH_READY

## Question

`BROWSE-006` already decided the policy — *"UI and WebView callbacks must carry a tab id rather than
updating whichever tab happens to be active when they arrive"* — and the session reducer already
implements it. So the plan does not need to decide *what* the boundary is. It needs to decide three
things the current code leaves open:

1. **Where renderer identity is declared**, given that `BrowserWebViewController` today is an
   identity-free box around a nullable `WebView` and the tab id lives only in the map key.
2. **What the JVM gate can actually assert**, given no Robolectric and no emulator in
   `scripts/pre-commit-check.sh`. The wiring that is wrong lives inside a `@Composable`, which the
   gate cannot run.
3. **Whether `onReceivedSslError` may publish a failure at all**, given `BROWSE-004` deliberately
   made it cancel *without* replacing the page because it does not identify the main frame.

## Origins involved

Not an origin ticket in the usual sense — nothing here changes what is fetched or from where. Two
origin facts still constrain it:

- **The origins in play are whichever two tabs happen to hold.** The defect's blast radius is a page
  on origin A rewriting the browser's identity presentation for origin B. `SecurityPresentation` and
  the SiteSkin identity row derive from `BrowserState.mode`'s committed `SiteOrigin`, so a leaked
  observation is a leaked *origin claim* — the same class of harm `HARDEN-002` guards against, minus
  the manifest.
- **SiteSkin activation is exact-origin bound and stays that way.** `candidateDisposition` compares
  `available.origin` to the observed origin and to `configuration.origin`, and `isCurrent` compares
  origin *and* generation. Those checks are correct; what is wrong is that the generation counter
  and `discoveryOwner` are written from `activeTabId` at the moment a renderer callback fires, so a
  background tab's page start can renumber the foreground tab's generation.

Asset origins are untouched: `BrandAssetLoader` already rechecks the trusted configuration's logo
against its own canonical HTTPS origin, and `publishesBrandAsset` already guards publication by
configuration identity.

## Manifest-controlled surface

**Nothing new, and nothing at all if this ships as scoped.** The ownership key is `BrowserTab.id`, a
browser-issued `Long` from `BrowserSession`'s private `nextId` counter. A website cannot read it,
choose it, collide with it, or cause one to be reused — `nextAvailableId` skips ids already in use
and never issues `0` or a negative.

What a website *can* influence today, and what the plan must keep it from converting into
cross-tab effect:

| Website-controlled input | Reaches | Must remain bounded to |
|---|---|---|
| When `onPageFinished` / `onReceivedError` / `doUpdateVisitedHistory` fire, and how late | `BrowserObservation` | the owner tab |
| Redirects and history writes (`pushState`) that fire after a switch | URL, address text, back/forward capability | the owner tab |
| A page that never finishes loading | `isLoading` | the owner tab |
| A certificate the device rejects | TLS failure path | the owner tab's main frame only |
| Subresource failures | nothing — already filtered by `isForMainFrame` | unchanged |
| A manifest at the owner tab's origin | integrated mode, colours, brand asset | the owner tab, at its own generation |

The timing of those callbacks is the only lever a page has, and timing is exactly what the current
wiring converts into a cross-tab write. That is the whole ticket.

## Browser-owned remainder

- **Renderer identity.** One `WebView` ↔ one `BrowserTab.id`, for the renderer's lifetime. Enforced
  by the controller carrying the id rather than the map key carrying it alone.
- **Event addressing.** Every `WebViewEvent` is applied through `BrowserSession.update(ownerId)`.
  `updateActive` remains legal only for genuinely user-initiated actions on the selected tab (Home
  button, address submit, consent Allow) and must not be reachable from a renderer callback.
- **Page-scoped side effects.** `completedPages`, `generations`, `discoveryOwner`, `pendingConsent`,
  and the brand-asset effect are all page-scoped and must be keyed or compared by the owner id, not
  by `activeTabId` at delivery time.
- **Failure terminality.** Every main-frame failure ends in a `BrowserLoadFailure` on the owner tab
  with `isLoading = false`. No path may leave an indefinite spinner.
- **TLS.** `handler.cancel()` unconditionally; `handler.proceed()` nowhere in the tree.
- **Selection ≠ destruction.** Switching away detaches; only `close` destroys. `BROWSE-006` requires
  live history to survive a switch, so a "destroy on switch" fix is forbidden even though it would
  make the leak impossible.

## Relevant code

| Path | Why it matters |
|---|---|
| `app/.../browser/BrowserScreen.kt:119-128` | `controllers.getOrPut(activeTabId, ::BrowserWebViewController)` — the map is the only place the id and the renderer meet, and `controller` is then passed around as an anonymous object |
| `app/.../browser/BrowserScreen.kt:282-284` | `onObservation` closes over `activeTabId`: `session.update(activeTabId) { it.observe(observation) }`. This is the primary leak |
| `app/.../browser/BrowserScreen.kt:308-324` | `onPageStarted` / `onPageCompleted` write `completedPages[activeTabId]`, `generations[activeTabId]`, `discoveryOwner = activeTabId`, and clear `pendingConsent` — all from a renderer callback |
| `app/.../browser/BrowserScreen.kt:181-199` | discovery outcome resolves through `discoveryOwner`; already id-addressed, but the id it reads was written at delivery time by the callback above |
| `app/.../browser/BrowserScreen.kt:202-222` | brand asset effect keyed on `activeTabId` + configuration; `publishesBrandAsset(state.mode, …)` reads the *active* tab's mode |
| `app/.../browser/BrowserScreen.kt:705-723` | `HardenedWebView` composed at one stable call site inside `Box(BROWSER_CONTENT_TAG)`, no `key(…)` |
| `app/.../web/HardenedWebView.kt:27-60` | `AndroidView(factory = …)` runs once per retained slot; `rememberUpdatedState(onEvent)` re-points the observer; `var attachedWebView` is a body-local reset on every recomposition, so the `DisposableEffect` detaches `null` |
| `app/.../web/BrowserWebViewController.kt:23-41` | `attach`/`detach`/`attached`/`destroy`; `detach` compares and returns either way — a no-op shaped like a contract. No tab id, no parent removal |
| `app/.../web/HardenedWebViewClient.kt:46-60` | `onReceivedError` correctly filters `isForMainFrame`; `onReceivedSslError` cancels and publishes nothing |
| `app/.../web/HardenedWebViewClient.kt:20,36-40` | `failedMainFrameUrl` suppresses a completion for a URL that just failed — any new TLS failure event must cooperate with this, not bypass it |
| `app/.../browser/BrowserState.kt:48-108` | `observe` and `observeFailure`; `observeFailure` already sets `isLoading = false` and bounds the retry URL through `resolveAddressInput` |
| `app/.../browser/BrowserSession.kt:45-53` | `update(id)` is the correct addressing primitive; `updateActive` is the one that must stop being reachable from renderer callbacks |
| `app/src/test/.../BrowserSessionTest.kt:53-65` | `late background observation updates its owner not the selected tab` — the reducer half is already proven; the wiring half has no test |
| `app/src/test/.../HardenedWebViewClientTest.kt` | MockK mocks `WebView`, `WebResourceRequest`, `WebResourceError`, `Uri` in the **JVM** suite, so the client is gate-testable. This is the precedent for testing the SSL path |
| `app/src/test/.../BrowserSurfaceConventionsTest.kt` | scans every `@Composable` source; new UI code inherits the string-resource and touch-target rules automatically |
| `app/src/androidTest/.../BrowserRecoveryInstrumentedTest.kt` | the existing single-tab failure/retry journey; the instrumented multi-tab case belongs beside it |
| `app/.../siteskin/SiteSkinRuntime.kt:24-51` | `candidateDisposition` / `isCurrent` — pure, already origin+generation bound, unchanged by this ticket |

## The defect, stated precisely

Three independent mechanisms, each of which alone is sufficient to produce the reported symptom:

1. **Slot reuse.** `AndroidView`'s `factory` runs once per retained composition slot. `RegularBrowser`
   composes `HardenedWebView` at one call site with no `key`, so a switch between two non-Home tabs
   recomposes rather than replaces it. The previous tab's `WebView` stays on screen; the selected
   tab's controller was never attached, so `navigate`/`reload`/`goBack`/`goForward` hit `null` and do
   nothing. (A Home↔page switch *does* replace it, because `state.mode == BrowserMode.Home` selects a
   different branch — which is why the bug is not visible on every switch.)
2. **Observer re-pointing.** `rememberUpdatedState(onEvent)` swings the client's observer to the
   current callback set. Combined with (1), tab A's live renderer now reports into tab B's callbacks.
3. **Active-tab addressing.** Even with (1) and (2) fixed, `onObservation` and the page-scoped
   callbacks resolve `activeTabId` at delivery time. This is the defense-in-depth layer the issue
   asks for, and it is the only one of the three the JVM gate can test directly.

`BrowserWebViewController.detach` deserves separate mention: it is dead code that reads as if it
implements a policy. Whatever the fix is, it must not leave a function whose body proves nothing.

## Prior art

- **`BROWSE-006`** — tabs as isolation boundary, one controller and retained `WebView` per tab,
  detach/reattach without loading, close destroys one renderer, dispose destroys all. The policy is
  already written; this ticket makes the renderer layer obey it.
- **`BROWSE-004`** — main-frame-only error crossing, closed `LoadErrorKind` mapping, retry only for
  the exact observed HTTP(S) URL, and the explicit decision that the legacy SSL callback is
  cancelled but *cannot replace a page* because it does not identify the main frame. Any TLS change
  must argue against this decision, not around it.
- **`BROWSE-002`** — `BrowserMode` hierarchy, Back precedence, address resolution. `AddressResolver`
  is untouched.
- **`UX-012`** — `ChromeHandoff` projects only `BrowserSession.activeTab`; inactive configuration and
  async brand assets cannot decorate the selected tab. That projection is correct *given* correct
  per-tab state, which is what this ticket restores.
- **`SKIN-004` / `HARDEN-002`** — origin + generation recheck at publication is the security control.
  The generation counter's *owner* is what is wrong here, not the check.
- **`NET-004`** — `publishesBrandAsset` as a pure function extracted out of `BrowserScreen`, with the
  trace recorded whether or not it publishes. This is the shape to copy: pull the decision out of the
  composable so the gate can drive it, keep the composable a thin caller.
- **`DEVX-003` / `CI-002`** — thin Android wrapper over a pure function, with the pure half in a
  source set the JVM gate compiles. `browserMenuCommands(available = …)` is the closest precedent:
  the constant is the default, the parameter is what makes it testable.
- **`A11Y-001`** — the gate is JVM-only; instrumented assertions are evidence, never a gate claim.

## Risks

- **The wiring that is wrong is inside a `@Composable`, and the gate cannot run one.** → The plan
  must extract the event-ownership decision into a pure function taking `(session, ownerId, event)`
  and returning the next session, and make `BrowserScreen` a caller with no second copy of the rule.
  A fix that only edits lambdas inside `BrowserScreen` is untestable by `scripts/pre-commit-check.sh`
  and will regress silently — `BrandAssetCoordinator` is the recorded precedent for the inverse
  mistake (a tested class with no callers while the composable reimplemented it inline).
- **A negative control here is easy to fake.** → Asserting only "the owner tab changed" passes under
  both implementations whenever the owner *is* the active tab. Every ownership test must assert the
  non-owner tab is unchanged **and** be run against an active-tab variant that fails.
- **`key(tabId) { AndroidView(…) }` changes disposal timing.** → A `WebView` removed from
  composition while still holding a parent throws `IllegalStateException: The specified child already
  has a parent` on reattach. Parent removal must live in the controller (one owner), not at each call
  site, and must be exercised by the instrumented switch test.
- **Publishing from `onReceivedSslError` can become a page-replacement route for a subframe.** →
  The event does not identify the main frame, so the plan must derive main-frame-ness from
  browser-observed state the client already holds (the URL last seen in `onPageStarted`) and must
  keep `failedMainFrameUrl` as the single suppression mechanism, rather than adding a second one.
  If that comparison cannot be made deterministic, the correct outcome is to settle loading without
  claiming a specific failure URL — never to proceed.
- **Detaching a mid-load renderer still delivers callbacks.** → That is the condition being defended
  against, not a reason to destroy the renderer; destroying it discards the tab's live history and
  breaks `BROWSE-006`.
- **`generations` and `completedPages` are plain `mutableMap`s in a `remember`.** → They are not
  Compose state, so writing them does not recompose. Any new per-tab bookkeeping must either follow
  that pattern deliberately or be real state; a half-observable map is a third place for staleness.
- **Closing a tab must clean up all per-tab bookkeeping.** → `BrowserTabSwitcher` currently removes
  from `controllers` and `generations` but not `completedPages`. A new per-tab map without a removal
  is a leak that survives the tab.

## Open questions

- **Does `WebView` reliably deliver `onReceivedError(ERROR_FAILED_SSL_HANDSHAKE)` after
  `handler.cancel()`?** It does on the versions this project targets in practice, which is why the
  spinner is "ambiguous on some TLS failures" rather than always stuck. The plan must therefore make
  the new TLS settlement **idempotent with** the existing `onReceivedError` path — two failures for
  one navigation must not produce two different `loadFailure` values or resurrect a cleared one.
  Carried into `/plan` as a stated design constraint, not resolved here.
- **Should the owner id be carried inside `WebViewEvent` or closed over by the client's callbacks?**
  Both satisfy the acceptance criteria. Carrying it in the event makes the ownership visible at every
  call site and testable without a controller; closing over it keeps `WebViewEvent` unchanged. `/plan`
  decides, with a bias toward the visible one — the issue's own phrasing is `WebViewEvent(tabId, …)`.
