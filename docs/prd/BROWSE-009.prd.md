# BROWSE-009 — Isolate WebView ownership per tab and recover cleanly from failed navigation

Status: PRD_READY

## Context / Problem

`BROWSE-006` made tabs a browser-owned isolation boundary and wrote the rule down: *"UI and WebView
callbacks must carry a tab id rather than updating whichever tab happens to be active when they
arrive."* The session layer honours it — `BrowserTab` has a stable browser-owned `Long id`,
`BrowserSession.update(id) { … }` addresses one tab, and `BrowserScreen` keeps one
`BrowserWebViewController` per tab id. The **renderer** layer does not.

Three seams below that boundary still resolve to *whatever tab is active now*:

1. **The Compose host is not keyed by tab.** `RegularBrowser` composes `HardenedWebView` at one
   stable call site with no `key(…)`, and `HardenedWebView` creates its `WebView` inside
   `AndroidView(factory = …)`. A factory runs once per retained composition slot. Switching between
   two non-Home tabs recomposes that slot rather than replacing it, so the **previous tab's**
   `WebView` stays on screen while every piece of Compose state around it belongs to the newly
   selected tab. The newly selected tab's controller is never attached to it, so `navigate`,
   `reload`, `goBack` and `goForward` reach `webView == null` and silently do nothing.

2. **Events are re-pointed at the active tab.** `rememberUpdatedState(onEvent)` deliberately swings
   the observer to the current callback on every recomposition, and `BrowserScreen` closes those
   callbacks over `activeTabId` (`session.update(activeTabId) { it.observe(observation) }`,
   `completedPages[activeTabId]`, `generations[activeTabId]`, `discoveryOwner = activeTabId`). A
   late `onPageFinished`, `onReceivedError`, `doUpdateVisitedHistory` or history update from tab A's
   retained renderer is therefore applied to tab B — B's URL, address text, loading flag, history
   capability, `loadFailure`, browsing-history record, SiteSkin generation and discovery ownership.

3. **`BrowserWebViewController.detach` is a no-op that reads like a contract.** It compares the view
   and then returns either way, and `HardenedWebView`'s `DisposableEffect` passes it a `var`
   declared in the composable body, which is reset to `null` on every recomposition. Nothing
   observable happens on either side.

Failed navigation is what makes this reachable by hand, which is how it was reported: an
unreachable address keeps a tab in a loading/failing state whose terminal callbacks arrive *after*
the user has opened the tab switcher and selected another tab. There is a second, smaller gap on
that path — `HardenedWebViewClient.onReceivedSslError` correctly calls `handler.cancel()` and
publishes nothing, so a cancelled TLS handshake can leave the owning tab with `isLoading = true`
and no `loadFailure`, i.e. an indefinite spinner and no error page.

None of this is a manifest-influence defect: a website cannot choose the tab id, and SiteSkin
activation already rechecks exact origin and generation. It is worse in a different direction — a
*failed* page in one tab can rewrite the identity, address and chrome of a *healthy* page in
another, and integrated mode's configuration, brand asset and consent generation are attached to
the tab that the leaked observation lands on.

## Goals

- Every retained `WebView` has exactly one stable owning `BrowserTab.id`, for its whole lifetime.
- Selecting a tab displays the renderer that tab owns, and reattaches it without reloading, so live
  back/forward history stays with the tab (`BROWSE-006`'s existing contract, now actually enforced).
- Every renderer event carries the id of the tab that produced it, and is applied only to that tab —
  including when it arrives after the active tab has changed.
- Every main-frame failure path is terminal and tab-local: DNS, connection/timeout, network I/O,
  TLS cancellation, and input that never became a navigable URL.
- Non-main-frame failures continue to be ignored for page-level failure UI.

## Non-goals

- Tab sync, thumbnails, page-title redesign, or raising `MAX_TABS`.
- Background execution or pre-rendering of inactive tabs. An inactive tab keeps its renderer; it is
  not given new work.
- Weakening WebView or TLS hardening. `handler.proceed()` remains forbidden.
- Changing `AddressResolver`'s normalization/search policy.
- Changing `BrowserSessionSnapshot`. Restored tabs still start in regular mode and re-traverse
  discovery, consent and exact-origin activation.
- A general renderer pool, process-per-tab, or any new WebView instance beyond one per live tab.

## User stories

- As a user, I type an unreachable address in tab A, switch to tab B while it is still failing, and
  B keeps showing its own page, its own address and its own identity chrome.
- As a user, I return to tab A and find A's own browser error page with working Retry and Home.
- As a user, I switch A → B → A between two loaded pages and each tab keeps its own history, so Back
  in A walks A's history and not B's.
- As a user, I close the failed tab and the remaining tab is selected and unaffected.
- As a user on a site with a broken certificate, the load stops with a TLS error page rather than a
  spinner that never ends.
- As a user, an integrated (SiteSkin) tab and a regular tab keep their own chrome, brand asset and
  colours across switches in either direction.

## Acceptance criteria

1. The Compose WebView host is keyed by the browser-owned tab id, so selecting a different tab
   composes a different host instance rather than reusing the previous tab's `AndroidView` slot.
2. Switching away from a tab detaches its `WebView` from the view hierarchy without destroying it,
   and switching back reattaches the same instance without a reload; no `WebView` is ever attached
   to two parents at once.
3. Renderer callbacks carry the immutable owner tab id, and every state transition they drive is
   addressed by that id — never by `activeTabId`. This holds for loading state, displayed URL and
   address text, `loadFailure`, back/forward capability, browsing-history completion recording,
   SiteSkin discovery generation and ownership, consent candidate publication, and brand/theme
   activation and teardown.
4. A `PageStarted`, `PageChanged`, `MainFrameCompleted` or `MainFrameFailed` event delivered after
   the active tab changed alters only its owner tab's state; a JVM test drives that sequence and a
   negative control (re-pointing the update at the active tab) fails it.
5. A main-frame failure in tab A sets only A's `loadFailure` and clears only A's loading state; a
   healthy loaded page in tab B is byte-identical before and after.
6. Returning to a failed tab renders that tab's own `BrowserErrorPage` with its retry URL and Home
   action, from its own state.
7. Closing a tab destroys only that tab's renderer and controller; the remaining selection follows
   `BrowserSession.close`'s existing rule and the surviving tab's state is unchanged.
8. A tab created after another tab failed starts from a default `BrowserState` with no inherited
   failure, URL or mode.
9. `onReceivedError` continues to ignore requests that are not `isForMainFrame`; a subresource
   failure produces no browser error page. A negative control (dropping the filter) fails the test.
10. A cancelled TLS handshake settles the owning tab into a terminal `LoadErrorKind.TLS` failure
    rather than leaving `isLoading = true`; `handler.cancel()` is still called and `handler.proceed()`
    appears nowhere in the source.
11. Address input that `AddressResolver` cannot resolve to an `http`/`https` URL never leaves a tab
    in a loading state and never touches another tab.
12. Integrated-mode configuration, brand asset, projected colours, consent decision and discovery
    generation cannot cross tabs under any of the scenarios above.
13. `bash scripts/pre-commit-check.sh` passes.

## NFR

- Security/privacy: renderer identity is a browser-owned boundary. A page in one tab must not be
  able to influence another tab's identity chrome, security presentation, or SiteSkin activation by
  timing its callbacks. No new remote input is introduced; the ownership key is a browser-issued
  `Long`, never a URL, origin, title or manifest value.
- Reliability/fallback: every failure path terminates in a browser-owned error surface with the page
  still recoverable. No path may leave an indefinite loading state.
- Performance: at most one live `WebView` per open tab, unchanged from today. Detach/reattach must
  not reload, and inactive tabs get no new background work.
- Accessibility: the status live region and error page keep their existing announcement contract —
  a terminal failure announces assertively, and a tab switch announces the selected tab's state, not
  the previous one's.

## Risks

- **Keying the host by tab id changes when Compose disposes the `AndroidView`.** If a detached
  `WebView` keeps a parent, reattaching throws `IllegalStateException: The specified child already
  has a parent`. Detach must remove it from its parent, and the controller must own that step rather
  than each call site remembering it.
- **The Android layer is untestable in this repo's JVM gate** (no Robolectric, deliberately). The
  ownership *decision* must therefore be a pure function the gate can drive, with the Compose/View
  wrapper kept thin — the same shape `ScreenEvidencePolicy` and `browserMenuCommands` use.
- **A negative control is easy to write and prove nothing here.** A test where A and B hold
  different URLs passes under both the correct and the active-tab implementation if it only asserts
  the owner tab. It must also assert the *other* tab is unchanged, and the control must be the
  active-tab variant failing.
- `onReceivedSslError` does not identify the main frame — that is exactly why `BROWSE-004` cancelled
  without replacing the page. Publishing a failure from it must not become a route for a subframe
  or a stale renderer to replace a good page; the event must be bound to the owner tab and reconciled
  with the existing `failedMainFrameUrl` suppression in `onPageFinished`.
- Detaching a `WebView` that is mid-load can still deliver callbacks. That is the behaviour being
  defended against, not a reason to destroy the renderer on switch — destroying it would silently
  discard the tab's live history, which `BROWSE-006` requires be preserved.

## Open questions

None. Renderer identity is `BrowserTab.id`, which already exists and is already browser-owned.
