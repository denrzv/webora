# BROWSE-009: Implementation plan

Status: PLAN_APPROVED

Input: `docs/research/BROWSE-009.md` (`Status: RESEARCH_READY`), `docs/prd/BROWSE-009.prd.md`.

## Overview

Three defects, three independent layers, fixed in that order so each is provable on its own:

| Layer | Defect | Fix | Provable by |
|---|---|---|---|
| Routing | renderer callbacks resolve `activeTabId` at delivery time | events carry an immutable owner id; one pure router addresses `BrowserSession.update(ownerId)` | JVM gate |
| Hosting | one un-keyed `AndroidView` slot serves every tab | `key(tabId)` around the host; the controller owns its id and its parent removal | instrumented |
| Termination | a cancelled TLS handshake publishes nothing | a pure main-frame test decides whether to settle the owner tab | JVM gate |

The routing layer goes first deliberately. It is the layer the gate can drive, it is defense in depth
that survives any future re-shaping of the Compose host, and it makes the hosting fix observable —
with routing correct, a leaked renderer can only mis-*render*, never mis-*write*.

## Flow

```
WebView (owned by tab T)
  └─ HardenedWebViewClient           ── browser-observed page facts
       └─ WebViewEvent(tabId = T)    ── owner id fixed at renderer construction, never re-read
            └─ routeRendererEvent(session, book, event)      ── PURE
                 ├─ session.update(T) { it.observe(…) }      ── never updateActive
                 ├─ book  : per-tab generation + last completed page
                 └─ effects: DiscoverManifest(T, url, gen) | RecordVisit(T, url, title)
                      └─ BrowserScreen executes, each gated on T
```

Discovery, validation and normalization are untouched. `candidateDisposition` and `isCurrent` keep
their exact-origin + generation checks; what changes is that the generation they compare against is
written by the tab that started the page rather than by whichever tab is selected when the callback
lands.

## Data

**Trust boundary.** No new remote input, no DTO, no new field reaching a website. The ownership key
is `BrowserTab.id` — a `Long` issued by `BrowserSession`'s private `nextId`, unreadable and
unchoosable from a page. It is carried inside `WebViewEvent`, which is `internal` and constructed
only by `HardenedWebView` from the controller it was built for.

**`RendererPageBook`** replaces the two bare `mutableMap`s (`generations`, `completedPages`) with one
immutable browser-owned value:

```kotlin
internal data class RendererPageBook(
    private val generations: Map<Long, Long> = emptyMap(),
    private val completed: Map<Long, String> = emptyMap(),
)
```

Immutable because the router must be pure — a mutable book passed into a pure function is a pure
function with a hidden output. It carries only two `Long`/`String` values per tab id and is dropped
per tab by `forget(tabId)` when that tab closes. No persistence: it is page-scoped state, and
`BrowserSessionSnapshot` is explicitly unchanged (`BROWSE-006` — a restored tab re-traverses
discovery, consent and activation).

**Storage / cache keys.** None added. `ManifestCache`'s key stays origin + trusted schema version;
per-tab state never becomes a cache key, because two tabs on one origin must share the cache.

## Security

**Origin binding.** Unchanged and re-affirmed: `candidateDisposition` still compares
`available.origin` to the *owner tab's* observed origin and to `configuration.origin`, and
`SiteSkinCandidate.isCurrent` still compares origin and generation before Allow applies. The change
is that `discoveryOwner`, the generation counter and the pending consent entry are now written and
cleared by owner id, so a background tab's page start cannot renumber the foreground tab's
generation or dismiss its consent prompt.

**The security claim being restored.** A page's only lever here is *when* its callbacks fire.
Today that lever rewrites another tab's `displayedUrl`, `addressText`, `mode` and therefore its
`SecurityPresentation` — a page on origin A causing Webora to present origin A's identity while
displaying origin B's page. That is `HARDEN-002`'s impersonation surface reached without a manifest,
and it is the reason this is a security ticket and not a tidiness one.

**Allow-lists.** Untouched. `shouldOverrideNavigation`, `classifyWebViewError`,
`applyWebViewHardening`, `TransferPolicy` and `AddressResolver` are not edited.

**TLS.** `handler.cancel()` stays unconditional and first. `handler.proceed()` must appear nowhere;
a source assertion pins that. The new publication is gated by a pure main-frame test — the SSL
error's URL must equal the URL the client last saw in `onPageStarted` — because `onReceivedSslError`
does not identify the main frame and `BROWSE-004` refused to let it replace a page for that reason.
This does not overturn that decision; it narrows it to the one case where the browser's *own*
observation says the failing resource is the main frame. When the comparison does not hold, nothing
is published — fail closed toward silence, never toward a claimed failure or a proceed.

**Fallback on failure.** Every path still ends in a rendered browser-owned surface: a per-tab
`BrowserErrorPage` with a retry URL bounded by `resolveAddressInput`, or regular browsing. An
unresolvable address never leaves a tab loading. No new path can leave `isLoading = true` with no
`loadFailure`.

## File-by-file plan

### New: `app/src/main/java/app/webora/browser/browser/RendererOwnership.kt`

The pure layer. Everything the gate needs to prove ownership lives here.

- `RendererPageBook` (above) with `generation(tabId)`, `startedPage(tabId)` returning the book with
  that tab's generation incremented, `completedPage(tabId)`, `recordedVisit(tabId, url)`,
  `forget(tabId)`.
- `sealed interface RendererEffect`: `DiscoverManifest(tabId, url, generation)` and
  `RecordVisit(tabId, url, title)`. Closed on purpose — an effect model with a generic "run this
  lambda" case would put arbitrary work back behind an id nobody checks.
- `data class RendererRouting(session, book, effects)`.
- `fun routeRendererEvent(session, book, event): RendererRouting` — the whole decision:
  - every observation applies through `session.update(event.tabId)`, never `updateActive`;
  - `PageStarted` bumps that tab's generation and emits `DiscoverManifest`;
  - `MainFrameCompleted` emits `RecordVisit` only when the canonical URL differs from that tab's
    last recorded one (the `completedPages` suppression, moved here and now id-addressed);
  - an event for a tab that no longer exists changes nothing and emits nothing — `update` already
    ignores an unknown id, and the effects must agree with it rather than firing for a closed tab.

### Modified: `app/src/main/java/app/webora/browser/web/BrowserWebViewController.kt`

- `WebViewEvent` variants gain `val tabId: Long` as their first component. The issue's own phrasing
  is `WebViewEvent(tabId, …)`; carrying it in the event makes ownership visible at every call site
  and lets the router be tested with no controller and no Android type.
- `BrowserWebViewController` gains a constructor `val tabId: Long`. The map key stops being the only
  place the id and the renderer meet.
- `detach(webView)` — a no-op that compares and returns either way — is deleted and replaced by
  `detachFromParent()`, which removes the retained `WebView` from its `ViewGroup` parent and does
  nothing else. This is the one owner of parent removal; leaving it to call sites is how
  `IllegalStateException: The specified child already has a parent` gets reintroduced.
- `destroy()` detaches from its parent before `WebView.destroy()`, which the framework requires and
  the current code skips.

### Modified: `app/src/main/java/app/webora/browser/web/HardenedWebView.kt`

- Take the owner id from `controller.tabId` **inside the factory** and close over it as a `val`. It
  is fixed for the renderer's lifetime; `rememberUpdatedState(onEvent)` may keep swinging the
  *handler*, which is now harmless because the handler routes by `event.tabId`.
- Delete the body-local `var attachedWebView`, which is reset to `null` on every recomposition and
  makes the existing `DisposableEffect` detach nothing. `onDispose` calls
  `controller.detachFromParent()` — the controller already holds the reference.

### Modified: `app/src/main/java/app/webora/browser/web/HardenedWebViewClient.kt`

- Track `mainFrameUrl` from `onPageStarted` alongside the existing `failedMainFrameUrl`.
- `onReceivedSslError`: `handler.cancel()` first, unchanged, then publish
  `onMainFrameFailed(url, LoadErrorKind.TLS)` only if `mainFrameTlsFailure(error.url, mainFrameUrl,
  failedMainFrameUrl)` returns a URL, and set `failedMainFrameUrl` to it so the following
  `onPageFinished` is still suppressed by the one existing mechanism.
- New pure `internal fun mainFrameTlsFailure(errorUrl: String?, mainFrameUrl: String?, alreadyFailed:
  String?): String?` — returns the URL to publish when it is non-blank, equals the observed main-frame
  URL, and has not already been published for this navigation; `null` otherwise. Idempotence is in
  the signature because the research's open question is real: `onReceivedError` may *also* fire with
  `ERROR_FAILED_SSL_HANDSHAKE` for the same navigation, and two publications must not produce two
  different failures.

### Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`

- `controllers.getOrPut(activeTabId) { BrowserWebViewController(activeTabId) }`.
- `generations` and `completedPages` are replaced by one `var book by remember { mutableStateOf(
  RendererPageBook()) }`.
- `onObservation` splits in two. The renderer path becomes `onRendererEvent: (WebViewEvent) -> Unit`,
  which calls `routeRendererEvent` and applies its `session`, `book` and effects. Address editing
  stays a user action on the selected tab and keeps `updateActive`.
- Effect execution, each gated on the owner id:
  - `DiscoverManifest` → `discoveryOwner = tabId`; clear `pendingConsent` **only if it belongs to
    that tab**; collapse the site-actions/menu surfaces **only if that tab is selected**; then
    `manifestDiscovery.onPageStarted(url, generation)` when `siteSkinEnabled`. Today all three are
    cleared unconditionally, so a background page start dismisses the foreground tab's consent
    prompt and closes its open menu.
  - `RecordVisit` → `recordStore.recordVisit(url, title)` and `recordVersion += 1`.
- The manifest-discovery outcome handler reads the owner's generation from the book
  (`book.generation(discoveryOwner)`) instead of `generations[discoveryOwner]`.
- `RegularBrowser` wraps its host in `key(controller.tabId) { HardenedWebView(…) }`. The `key` sits
  inside the existing `Box(BROWSER_CONTENT_TAG)` so the screenshot harness's measured rectangle is
  byte-identical — `CI-003`'s region must not move.
- `BrowserTabSwitcher`'s close path calls `book = book.forget(id)` beside the existing
  `controllers.remove(id)?.destroy()`. A per-tab map without a removal is a leak that outlives the
  tab, and `completedPages` is that leak today.

### Modified: tests

- `app/src/test/.../web/HardenedWebViewClientTest.kt` — TLS cases.
- `app/src/test/.../browser/BrowserSessionTest.kt` — **not** edited. Its
  `late background observation updates its owner not the selected tab` already proves the reducer
  half; if this ticket needed to change it, the fix would be in the wrong layer.

## Tests

**JVM gate (`RendererOwnershipTest`, new).** Each ownership assertion checks the owner tab changed
**and** the non-owner tab is `assertEquals`-identical to its pre-event value. Asserting only the
owner passes under the broken implementation whenever the owner happens to be selected, which is the
research's recorded trap.

1. a `PageChanged` for a background tab updates that tab and leaves the selected tab identical;
2. a `MainFrameFailed` for a background tab sets only that tab's `loadFailure` and clears only its
   loading state;
3. an event for a **closed** tab id changes nothing and emits no effect;
4. `PageStarted` bumps only its own tab's generation, and the emitted `DiscoverManifest` carries the
   owner id and the bumped value;
5. `MainFrameCompleted` emits `RecordVisit` once per distinct URL per tab, and two tabs completing
   the same URL each get their own record;
6. an interleaved sequence — A starts, B is selected, A fails, B completes — leaves each tab holding
   exactly its own state.

**Negative control (recorded in the tasklist):** re-point the router at `session.activeId`. Tests 1,
2 and 6 must fail; the rest must still pass, so the control is proving the addressing and not
breaking everything.

**JVM gate (`HardenedWebViewClientTest`, extended).**

7. an SSL error whose URL equals the observed main-frame URL publishes exactly one
   `LoadErrorKind.TLS` failure for that URL;
8. an SSL error for a different URL (a subframe or subresource) publishes nothing;
9. a second SSL error, or a following `onReceivedError`, for the same navigation publishes nothing
   more;
10. `onPageFinished` after a TLS failure is not a completion — the existing suppression still holds.

**Negative control:** make `mainFrameTlsFailure` return `errorUrl` unconditionally; test 8 must fail.

**Source assertion.** `handler.proceed(` appears nowhere under `app/src/main`. Cheap, and the one
regression in this file that would be catastrophic and silent.

**Instrumented evidence (`TabRendererIsolationTest`, new — evidence, never a gate claim, per
`A11Y-001`).**

11. two tabs, each loaded from `loadDataWithBaseURL`; A → B → A preserves each tab's own rendered
    content and its own address/identity chrome;
12. tab A navigates to a closed loopback port and fails; switching to B before and after the failure
    leaves B's page and chrome unchanged, and returning to A shows A's own error page with a working
    Retry;
13. closing the failed tab selects the expected remaining tab and leaves it unchanged;
14. exactly one `WebView` is attached to the window at a time, and reattaching after a switch throws
    no parent conflict.

`scripts/pre-commit-check.sh` runs unchanged (gitleaks, shellcheck, `:siteskin-core:test` with no
Android SDK, `test`, detekt). `:app:compileDebugAndroidTestKotlin` is run explicitly, because the
gate does not compile `androidTest` — the hole `CI-003` recorded.

## Rollout / versioning

No protocol, schema, fixture, diagnostic, manifest or spec change. `spec/` is untouched, so no
corpus fixture and no `diagnostics.json` entry. `BrowserSessionSnapshot`'s version is unchanged
because its stored shape is unchanged; a session saved by the previous build restores identically.
No migration, no flag, no staged rollout — the behaviour it removes is a defect with no dependent.

## Open questions

Both of the research note's questions are resolved here:

- **TLS double-delivery** — resolved by making the settlement idempotent in the pure function's
  signature (`alreadyFailed`) rather than by assuming a delivery order. If `onReceivedError` also
  fires, the second publication is dropped and the first stands.
- **Where the owner id lives** — resolved in favour of `WebViewEvent.tabId`, matching the issue's
  own phrasing and letting the router be tested with no controller, no `WebView` and no mock.

One constraint carried forward rather than closed: a main-frame TLS failure whose SSL error URL
differs from the URL last seen at `onPageStarted` (a redirect chain failing after the observed start)
publishes nothing and can still leave a spinner. That is the fail-closed direction and it is strictly
better than today, where *no* TLS failure publishes. Narrowing it further needs a browser-observed
main-frame URL that survives redirects, which is a `BROWSE-004` change and its own ticket.
