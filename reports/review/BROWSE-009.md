# Review: BROWSE-009
Date: 2026-08-17
Status: RESOLVED

## Summary

Three layers, three commits, and each one is provable on its own — that split is the change's main
strength. The routing layer (`routeRendererEvent`) is pure, sits in the JVM gate, and is where the
security property actually lives; the hosting layer (`key(controller.tabId)` + `detachFromParent`)
is instrumented with a JVM structural backstop; the TLS layer is a three-argument pure function with
a total test.

The defect being fixed was real and had three independent causes, any one of which was sufficient.
That matters for judging the change: fixing only the visible one — the un-keyed `AndroidView` — would
have removed the symptom and left the cross-tab write reachable the moment anything reshaped the
composition. The commit order (routing first) is deliberate and correct.

Three findings below. Two are in the new code and are fixed as `TASK-FIX-1` and `TASK-FIX-2`; the
third is a **pre-existing** navigation defect this review found while reasoning about renderer reuse,
and it is deferred with a written argument rather than fixed blind, because the fix changes *when a
reload happens* and this checkout cannot run the instrumented suite that would confirm it.

The most valuable thing the ticket produced is not in the diff: `TASK-3`'s second negative control
found that `BROWSE-004`'s `isForMainFrame` filter — four tickets old, named in this PRD's criterion 9
— **had no test at all**. Deleting the guard left the whole suite green. That is the negative control
doing exactly its job, and the coverage is now written.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | Untouched. `:siteskin-core` is not edited; `RendererOwnership.kt` imports `WebViewEvent` from `web` and nothing Android. |
| Where the decision lives | Pure and in `src/main`, driven by `./gradlew test`. This is the `NET-004` shape (`publishesBrandAsset`) rather than the `BrandAssetCoordinator` shape (a tested class with no callers while the composable reimplemented it inline). The lambda in `BrowserScreen` performs effects and decides nothing. |
| Effect model | Closed: `DiscoverManifest` and `RecordVisit`, both naming their owner. No generic "run this lambda" case, which would put arbitrary work back behind an id nobody checks. |
| State shape | `RendererPageBook` is immutable, so the router is genuinely pure — a mutable book would be a hidden second output. It also fixes a leak: `completedPages` had no removal and its entries outlived the tab. |
| Reducer untouched | `BrowserSession` is not edited and `BrowserSessionTest` passed **unedited**. If the fix had needed the reducer, it would have been in the wrong layer. |
| Snapshot | `BrowserSessionSnapshot` unchanged; the book is deliberately unpersisted, so a restored tab still re-traverses discovery, consent and activation (`BROWSE-006`). |
| Measurement region | `key(...)` is inside the `BROWSER_CONTENT_TAG` box, so `CI-003`'s page rectangle is byte-identical. Asserted, with a negative control that puts the key outside. |
| Complexity | `BrowserScreen` keeps its existing three suppressions; detekt is green. The net line count falls — two callbacks and one local mapping function left the file. |

## Security

| Property | Assessment |
|---|---|
| The property restored | A page's only lever is *when* its callbacks fire. That lever used to rewrite another tab's `displayedUrl`, `mode` and therefore its `SecurityPresentation` — one origin's identity presented over another origin's page, which is `HARDEN-002`'s impersonation surface reached without a manifest. |
| Ownership key | `BrowserTab.id`, a `Long` from `BrowserSession`'s private `nextId`. Unreadable and unchoosable from a page; `nextAvailableId` never reissues a live id. |
| New remote input | None. `WebViewEvent` gains one browser-issued number. |
| Origin binding | `candidateDisposition` and `isCurrent` are unedited and still compare full canonical origin **and** generation. What changed is that the generation is now written by the tab that started the page. |
| Consent | `pendingConsent` is cleared only by its own tab's page start (it used to be cleared by any tab's), and Allow rechecks the *asking* tab's origin and generation rather than the selection's. |
| TLS | `handler.cancel()` first and unconditional; `handler.proceed(` absent from all of `src/main/java`, asserted over code lines only. Publication requires the browser's own observed main-frame URL — `BROWSE-004`'s refusal is narrowed by the browser's own evidence, not bypassed. |
| Subresources | `isForMainFrame` retained on `onReceivedError`, and now actually tested. The SSL path applies the same principle through a different mechanism, since that callback carries no frame flag. |
| Fail direction | Every ambiguous TLS case publishes nothing. A missed error page is a worse UX than a spinner is; a *wrong* error page over a working origin is worse than both. |

## Findings

### FINDING-1 · Low · dead local after the book replaced the maps
**File:** `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt:129`

`generation` had two readers before this ticket; both now read the book by owner id, so the local is
unused. Kotlin does not error on an unused local `val` and detekt does not flag one inside a
composable, so nothing caught it.

Current:
```kotlin
var book by remember { mutableStateOf(RendererPageBook()) }
val generation = book.generation(activeTabId)
```

Fix: delete the line. A live-looking accessor for "the selected tab's generation" sitting unused in a
file whose whole point is *stop reading the selected tab* is worse than dead code; it is an invitation.

### FINDING-2 · Medium · the contract assertion is satisfied by the thing it means to forbid
**File:** `app/src/test/java/app/webora/browser/browser/RendererHostContractTest.kt:60`

Current:
```kotlin
assertFalse(
    "no renderer state may be addressed to whichever tab is selected",
    screen.contains("update(activeTabId)"),
)
```

`BrowserSession.updateActive(t)` **is** `update(activeId, t)`. A regression written as
`session.updateActive { it.observe(rendererObservation) }` restores the exact defect and passes this
assertion. And `updateActive` cannot simply be banned from the file: four legitimate user-action call
sites use it (Home reset, address edit, SiteSkin deactivation on the privacy toggle and on clear-data).

This is the same class of defect `UX-020` recorded twice — an assertion that reads stronger than it
is — and the same class as `TASK-3`'s own `handler.proceed(` scan matching its own KDoc.

Fix: assert the *renderer path* rather than a spelling. The router's source may not contain
`updateActive` or `activeId` at all, and the `applyRendererEvent` block in `BrowserScreen` must
delegate to `routeRendererEvent` and contain no session mutation of its own. Both halves need a
negative control.

### FINDING-3 · Medium · **pre-existing** · Home and back leaves the old page under a permanent spinner
**File:** `app/src/main/java/app/webora/browser/web/HardenedWebView.kt:57`

```kotlin
if (existing == null) loadUrl(initialUrl)
```

`BrowserScreen` composes `RegularBrowser` only when the tab is not on Home, so returning a tab to
Home disposes its `AndroidView` while `BrowserWebViewController` deliberately retains the `WebView`
(`BROWSE-006`). Navigating that tab out of Home again remounts the host, the factory finds
`existing != null`, and therefore **never loads the new URL**: the renderer keeps showing the previous
page while `displayedUrl` says the new one and `isLoading` stays `true` with no callback ever coming.

Reached by: load a page → Home → type any address. Present on `main` before this ticket; the tab id
does not change across that round trip, so `key(controller.tabId)` neither causes nor cures it.

**Deferred, deliberately.** The obvious fix — load when the retained renderer's URL differs from the
tab's committed target — changes *when a reload happens*, and "reattach without reloading" is PRD
criterion 2 of this very ticket. Getting that wrong turns a fix into a regression of live back/forward
history, and the only thing that could confirm it is the instrumented suite, which this checkout
cannot run for want of a device. Fixing it blind inside a review is how the `NET-004` retry would have
been justified by a run that never exercised it. Filed as `BROWSE-010` in `docs/BACKLOG.md` with this
analysis and the reproduction.

## Not findings

- **`onAddressEdited` uses `updateActive`.** Deliberate and correct: a keystroke in the address field
  belongs to the tab the user is looking at. The split into two callbacks is what makes the
  distinction visible — the old single `onObservation` carried both a user edit and a renderer
  observation through one active-addressed path, which is part of why the defect was invisible.
- **`rememberUpdatedState(onEvent)` still re-points the observer.** That is now safe rather than
  wrong: what it delivers names its own tab, so the newest handler addresses the owner. Removing it
  would freeze the handler against the first composition's captured state, which is a different bug.
- **A background tab's page start cancels the foreground tab's in-flight manifest discovery.** One
  `ManifestDiscoveryCoordinator` is shared, and `onPageStarted` cancels the previous job. This
  predates the ticket and is *improved* by it — the discovery is at least attributed to the tab that
  asked for it now, and `candidateDisposition` still refuses to activate anything whose origin or
  generation does not match. The outcome is a missed activation, never a cross-tab one. A per-tab
  coordinator is a separate design question with its own cancellation and cleanup contract.
- **`destroy()` now detaches first.** Not a behaviour change smuggled into a lifecycle fix: the
  framework requires a `WebView` to leave the hierarchy before `destroy()`, and the previous code
  destroyed an attached one. It strictly narrows the window in which a destroyed renderer can be
  drawn.
- **`WebViewEvent.toBrowserObservation()` widened from `private` to `internal`.** It moved file, not
  visibility class — it is still module-internal and has one caller.
- **The book is not persisted.** Intentional. A generation carried across process death would let a
  restored tab skip the re-validation `BROWSE-006` requires.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `RendererOwnershipTest` (new, JVM) | 8 | background page change, background failure, closed-tab event, generation isolation, per-tab visit suppression, interleaved A/B sequence, `forget`, plus the selected-tab discriminator that must survive the control |
| `RendererHostContractTest` (new, JVM) | 3 | host keyed by tab inside the measured region; owner read once and carried by all four events; detach removes from parent and destroy detaches first — each with a negative control |
| `HardenedWebViewClientTest` (extended, JVM) | 10 | main-frame TLS settles once; sub-main-frame handshake cancels without replacing; the rule is total; `handler.proceed(` absent from all of `src/main/java`; **new** subresource `onReceivedError` case |
| `BrowserSessionTest`, `BrowserStateTest`, `BrowserFailureStateTest` | unedited | the reducer half was already correct and stays untouched |
| `TabRendererIsolationTest` (new, instrumented) | 3 | own renderer and own failure across switches; exactly one attached renderer per switch; closing the failed tab. **Compiled, not run** — no device in this checkout |

Negative controls run: 4 (router → `activeId`; `removeView` removed; `key` removed; TLS rule
unconditional), plus the `isForMainFrame` control that found the missing test. Each failed only its
own assertions, and all were restored.

## Verdict

**RESOLVED.** `TASK-FIX-1` and `TASK-FIX-2` landed in commit `336eb2f`. The architecture, the
security property and the layer split are sound; both findings were one-line-class defects in the new
code, one of them in a test that overclaimed. `FINDING-3` is out of this ticket's scope and is
recorded as `BROWSE-010` rather than carried.

Fixing `FINDING-2` produced the third instance of one trap in this ticket: the router's KDoc names
`activeId` while forbidding it, so the first scan failed on its own documentation — after
`handler.proceed(` matched its own KDoc in `TASK-3`, and after `UX-002`'s wrapper exemption exempted
the file describing it. All three scans read executable lines only now, and that recurrence is worth a
convention note rather than a third fix in isolation.
