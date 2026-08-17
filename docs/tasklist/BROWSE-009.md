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

- [ ] TASK-2: give the Compose host a per-tab identity
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

- [ ] TASK-3: make a cancelled TLS handshake terminal for its own tab
  - Modified: `web/HardenedWebViewClient.kt` — track the observed main-frame URL, add the pure
    `mainFrameTlsFailure(errorUrl, mainFrameUrl, alreadyFailed)`, publish through it after
    `handler.cancel()`.
  - Acceptance: PRD criteria 9, 10, 11. `handler.proceed(` appears nowhere under `app/src/main`.
  - Tests: extend `app/src/test/.../web/HardenedWebViewClientTest.kt` with assertions 7–10 plus the
    source assertion. Existing cases pass unedited.
  - Negative controls to run and record: (a) `mainFrameTlsFailure` returns `errorUrl`
    unconditionally → the subframe/subresource assertion must fail; (b) drop the `isForMainFrame`
    filter in `onReceivedError` → the existing subresource assertion must fail.

- [ ] TASK-4: review, QA and validate
  - `/review` findings become `TASK-FIX-N` with a `- Source:` line; then `/qa`, then `/validate`.
  - Record the CLAUDE.md convention section for renderer ownership as part of the docs step.
