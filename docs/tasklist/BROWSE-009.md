# BROWSE-009 tasklist — Isolate WebView ownership per tab

Status: TASKLIST_READY

References:
- PRD: `docs/prd/BROWSE-009.prd.md`
- Research: `docs/research/BROWSE-009.md`
- Plan: `docs/plan/BROWSE-009.md`

## Tasks

- [x] TASK-1: route every renderer event by its owner tab id
  - New: `app/src/main/java/app/webora/browser/browser/RendererOwnership.kt` — `RendererPageBook`,
    the closed `RendererEffect` model, `RendererRouting`, and the pure `routeRendererEvent`.
  - Modified: `web/BrowserWebViewController.kt` (`WebViewEvent.tabId`, `BrowserWebViewController(tabId)`),
    `web/HardenedWebView.kt` (owner id fixed in the factory), `browser/BrowserScreen.kt`
    (`onRendererEvent`, book replaces the two maps, effects gated on the owner id, `forget` on close).
  - Acceptance: PRD criteria 3, 4, 5, 7, 8, 12. No renderer callback reaches `updateActive`.
  - Tests: new `app/src/test/.../browser/RendererOwnershipTest.kt`, assertions 1–6 from the plan.
    `BrowserSessionTest` must pass **unedited** — if it needs changing, the fix is in the wrong layer.
    `./gradlew :app:testDebugUnitTest`, then `bash scripts/pre-commit-check.sh`.
  - Negative control to run and record: re-point `routeRendererEvent` at `session.activeId`.
    Assertions 1, 2 and 6 must fail and the rest must still pass — a control that breaks everything
    proves the tests run, not that they test addressing.
  - Result: `WebViewEvent` now carries `tabId`, fixed in `HardenedWebView`'s factory from
    `BrowserWebViewController(tabId)` and never re-read; `routeRendererEvent` is the one caller of
    `BrowserSession.update` for renderer state, and `BrowserScreen` performs only the two closed
    effects it names. `RendererPageBook` replaces the `generations`/`completedPages` maps, and the
    tab-close path now `forget`s it — `completedPages` had no removal and its entries outlived the
    tab. Two further active-tab reads became owner-addressed while the id was in hand: a background
    page start no longer dismisses the foreground tab's consent prompt or closes its open menu, and
    consent Allow rechecks the *asking* tab's origin and generation. `RendererOwnershipTest` is 8/8,
    `BrowserSessionTest` passed unedited, `bash scripts/pre-commit-check.sh` green, and
    `:app:compileDebugAndroidTestKotlin` compiles after updating `BrowserSiteSkinLayoutTest`'s two
    `RegularBrowser` call sites for the split `onAddressEdited`/`onRendererEvent` seam.

    Negative control run and restored — and **the plan's prediction was wrong**, which is the useful
    part. It expected assertions 1, 2 and 6 to fail; re-pointing the router at `session.activeId`
    fails **6 of 8**, because every routing assertion in the file delivers an event from a
    non-selected tab. That made the control indistinguishable from a control that simply broke the
    file, so a discriminator was added before re-running: *an event from the selected tab still
    updates the selected tab*. Under the control exactly two tests pass — that one and the
    book-only `forget` case — which is what says the control changed the addressing and nothing
    else. The closed-tab assertion also fails under the control (it was expected to pass): with
    `activeId` the id always resolves to a live tab, so the "no such tab" branch becomes
    unreachable.

- [x] TASK-2: give the Compose host a per-tab identity
  - Modified: `browser/BrowserScreen.kt` (`key(controller.tabId)` inside the existing
    `Box(BROWSER_CONTENT_TAG)`), `web/BrowserWebViewController.kt` (`detachFromParent()` replaces the
    no-op `detach`; `destroy()` detaches first), `web/HardenedWebView.kt` (drop the body-local
    `var attachedWebView`; dispose through the controller).
  - Acceptance: PRD criteria 1, 2, 6. The measured `BROWSER_CONTENT_TAG` rectangle is unchanged —
    `CI-003`'s region must not move, so the `key` sits inside the `Box`, not around it.
  - Tests: new instrumented `app/src/androidTest/.../browser/TabRendererIsolationTest.kt`,
    assertions 11–14 from the plan. `./gradlew :app:compileDebugAndroidTestKotlin` explicitly — the
    gate does not compile `androidTest`. Instrumented results are evidence, never a gate claim.
  - Negative control to run and record: remove `detachFromParent()`'s `removeView` and switch
    A → B → A; the reattach must throw `IllegalStateException: The specified child already has a
    parent` (or the isolation assertion must fail) rather than passing quietly.
  - Deviation: the plan gave this task instrumented coverage only, which in this checkout means
    **no coverage at all** — there is no emulator, so `TabRendererIsolationTest` compiles and never
    runs, and its negative control cannot be executed either. A JVM `RendererHostContractTest` was
    added so the three structural facts the instrumented behaviour depends on are held by the gate:
    the host is keyed by `controller.tabId` *inside* the `BROWSER_CONTENT_TAG` box, every emitted
    event carries the owner read once into a local, `BrowserScreen` contains no
    `update(activeTabId)`, `detachFromParent` actually calls `removeView`, and `destroy` detaches
    first. Same shape as `BrowserChromeContractTest`, and the same reason: runtime behaviour and
    source structure fail under different regressions.
  - Result: `key(controller.tabId)` wraps `HardenedWebView` inside the existing tagged `Box`, so
    `CI-003`'s measured rectangle is unchanged. `detach(webView)` — which compared the view and
    returned either way, called with a `var` every recomposition reset to `null` — is replaced by
    `detachFromParent()`, the one owner of parent removal, and `destroy()` now detaches before
    `WebView.destroy()` as the framework requires. `RendererHostContractTest` is 3/3 and
    `bash scripts/pre-commit-check.sh` is green; `TabRendererIsolationTest` (3 cases: own renderer
    and own failure across switches, exactly one attached renderer per switch, closing the failed
    tab) compiles under `:app:compileDebugAndroidTestKotlin` and is **not run** here.

    Negative controls, both run against the JVM contract test and both restored, each failing only
    its own assertion: (a) `removeView(view)` replaced by a `check` → *detaching removes the
    renderer from its parent* failed; (b) `key(controller.tabId)` replaced by `run` → *the renderer
    host is keyed by tab* failed. The instrumented control the plan named — observing the actual
    `IllegalStateException` on reattach — remains unrun for want of a device, and is recorded as
    owed evidence rather than as a passed check.

- [x] TASK-3: make a cancelled TLS handshake terminal for its own tab
  - Modified: `web/HardenedWebViewClient.kt` — track the observed main-frame URL, add the pure
    `mainFrameTlsFailure(errorUrl, mainFrameUrl, alreadyFailed)`, publish through it after
    `handler.cancel()`.
  - Acceptance: PRD criteria 9, 10, 11. `handler.proceed(` appears nowhere under `app/src/main`.
  - Tests: extend `app/src/test/.../web/HardenedWebViewClientTest.kt` with assertions 7–10 plus the
    source assertion. Existing cases pass unedited.
  - Negative controls to run and record: (a) `mainFrameTlsFailure` returns `errorUrl`
    unconditionally → the subframe/subresource assertion must fail; (b) drop the `isForMainFrame`
    filter in `onReceivedError` → the existing subresource assertion must fail.
  - Result: `onReceivedSslError` still calls `handler.cancel()` first and unconditionally, then
    publishes `LoadErrorKind.TLS` only through the pure `mainFrameTlsFailure`, which requires a
    non-blank URL equal to the URL the browser observed the main frame starting and not already
    published for this navigation. `failedMainFrameUrl` stays the one suppression mechanism, so the
    following `onPageFinished` is still not a completion. `HardenedWebViewClientTest` is 10/10 and
    `bash scripts/pre-commit-check.sh` is green.

    Negative control (a) run and restored: returning `errorUrl` unconditionally failed three
    assertions — the subresource case, the once-only case and the totality case — and nothing else.

    **Negative control (b) found a real gap rather than confirming a guard.** Replacing
    `if (request.isForMainFrame)` with an unconditional block left the entire suite green:
    `BROWSE-004`'s main-frame filter, four tickets old and named in PRD criterion 9, had no test at
    all. `a subresource error cannot replace the page` was written to close it — a non-main-frame
    `onReceivedError` must publish no failure and must not suppress the page's completion — and the
    control was then re-run against it and failed exactly that one assertion. This is the negative
    control doing the job it exists for: the guard was correct, and the evidence for it did not
    exist.

    One test defect found by running rather than by reading, and worth the note because it is
    `UX-002`'s trap in a new place: the first `handler.proceed(` scan read the whole file and failed
    on **its own KDoc**, which says `handler.proceed()` appears nowhere in this tree. The scan now
    strips comment lines, covers all of `src/main/java` rather than one file, and carries its own
    counter-example proving it still sees a `proceed` in code.

- [x] TASK-FIX-1: delete the dead selected-tab generation local
  - Source: `/review` FINDING-1.
  - `BrowserScreen.kt:129`'s `val generation` lost both readers when the book replaced the maps.
  - Acceptance: no unused accessor for "the selected tab's generation" survives in the file whose
    point is to stop reading the selected tab.
  - Result: deleted. Both readers had moved to `book.generation(ownerId)`.

- [x] TASK-FIX-2: make the renderer-path contract assert the path, not a spelling
  - Source: `/review` FINDING-2.
  - `assertFalse(screen.contains("update(activeTabId)"))` is satisfied by `updateActive`, which is
    literally `update(activeId, …)` — a regression written that way passes. `updateActive` cannot be
    banned outright: four legitimate user-action call sites use it.
  - Acceptance: the router's source contains neither `updateActive` nor `activeId`, and
    `applyRendererEvent` delegates to `routeRendererEvent` with no session mutation of its own. Both
    halves carry a negative control.
  - Result: `the renderer path cannot address the selected tab` replaces the spelling check with two
    rules and four controls. The decisive one is the regression the old assertion missed, run against
    the real source: adding `session = session.updateActive { it.observe(event.toBrowserObservation()) }`
    inside `applyRendererEvent` now fails, where it previously passed.

    Writing it produced **the third instance of one trap in this ticket**: the router's own KDoc says
    nothing in it may consult `BrowserSession.activeId`, so the first version of the scan failed on
    that sentence — after `handler.proceed(` matched its own KDoc in TASK-3, and after `UX-002`'s
    wrapper exemption exempted the file describing it. All three scans now read executable lines
    only. The negative control for the delegation rule deliberately keeps both required parts, so it
    can only fail on the mutation itself rather than on a missing routing call.

- [ ] TASK-4: review, QA and validate
  - `/review` findings become `TASK-FIX-N` with a `- Source:` line; then `/qa`, then `/validate`.
  - Record the CLAUDE.md convention section for renderer ownership as part of the docs step.
