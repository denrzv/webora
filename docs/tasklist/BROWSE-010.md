# BROWSE-010 tasklist — Retained renderer loads its tab's page

Status: TASKLIST_READY

References:
- Issue: [#106](https://github.com/denrzv/webora/issues/106)
- PRD: `docs/prd/BROWSE-010.prd.md`
- Research: `docs/research/BROWSE-010.md`
- Plan: `docs/plan/BROWSE-010.md`

## Tasks

- [x] TASK-1: decide the mount action from browser-observed values
  - Add `hostedUrl` to `BrowserWebViewController`, written by `navigate()` and by a new `observed()`
    called from the three reporting callbacks — never from `onMainFrameFailed`.
  - Add the closed `RendererMountAction` and the pure `rendererMountAction(hosted, target, isLoading)`.
  - Replace `if (existing == null) loadUrl(initialUrl)` with a `when` over that decision; `Settle`
    emits a completed `PageChanged` for the page already on screen so `isLoading` terminates.
  - Pass `state.isLoading` from `BrowserScreen`; leave the `key(...)` placement inside
    `BROWSER_CONTENT_TAG` untouched.
  - Acceptance: PRD criteria 1–5, 7, 8.
  - Tests: `RendererMountActionTest` (8 cases), `RendererHostContractTest` (3 assertions, each with a
    counter-example, executable lines only), `TabRendererIsolationTest` unedited,
    `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`,
    `bash scripts/pre-commit-check.sh`.
  - Negative controls to run and record:
    1. restore `if (existing == null)` → the Home round-trip case must fail;
    2. drop `observed()` from `onPageChanged` → the in-page-navigation case must fail;
    3. update `hosted` on `MainFrameFailed` → the failed-tab case must fail.
  - Result: `rendererMountAction(hosted, target, isLoading)` replaces `if (existing == null)`;
    `BrowserWebViewController.hostedUrl` is written by `navigate()` and by `observed()` from the three
    reporting callbacks. `RendererMountActionTest` 9/9, `RendererHostContractTest` 4/4,
    `TabRendererIsolationTest` unedited, `:app:compileDebugAndroidTestKotlin` compiles, full gate green.

    Negative controls — all three run, all restored:
    1. restored `if (existing == null)` → **2** failures: the structural assertion *and*
       `BROWSE-009`'s `a renderer event names its owner`, because the `Settle` emission disappears
       with it.
    2. dropped `observed()` from `onPageChanged` → the source contract failed. **The plan predicted
       the in-page-navigation case in `RendererMountActionTest` would fail too, and it did not.**
       That case drives the controller directly, so it proves the *decision* handles an in-page URL;
       only the source scan can see that the wiring producing it still exists. Neither layer covers
       this row alone — recorded rather than presented as a met prediction.
    3. recorded `hosted` on `MainFrameFailed` → the failed-navigation assertion failed.

    Two things the work turned up beyond the plan. `BROWSE-009`'s `EMITTED_EVENTS = 4` inventory
    caught the new `Settle` emission and was raised to 5 with the reason written down; the count is
    what makes it an inventory, since an event emitted *without* `owner` still leaves it short.
    And detekt's `LongMethod` forced the client construction out into `reportingClient`, which is
    where the never-inside-`onMainFrameFailed` rule is now documented.

- [x] TASK-2: record the Back exposure as its own backlog entry
  - PRD criterion 6: the Home-round-trip Back contract is decided as a second defect, not changed
    here, with the reasoning and the `BROWSE-008` dependency written down.
  - Result: added `BROWSE-011` to `docs/BACKLOG.md` — Home is or is not a history root, decided
    beside `BROWSE-008`'s Back ordering rather than in the renderer host, with the three reasons this
    ticket did not change it and the `clearHistory()` post-commit timing that makes every remedy
    device-verifiable only.

- [x] TASK-FIX-0: repair `main`'s red `ExpressiveBloomJourneyContractTest`
  - Not this ticket's defect and not in its plan. `origin/main` at `8e83ac7` fails on a pristine
    checkout: `CI-009`'s #110 moved `SITESKIN_ACTION_TAG_PREFIX` into `BloomReferenceContract`
    `.actionTag()`, and `SHOWCASE_MARKERS` still required the literal in the showcase source.
    Confirmed pre-existing by running the test in a clean worktree of `origin/main`; carried here on
    the user's explicit decision, since PRD criterion 8 is unreachable on a red base.
  - Result: the two stale markers now follow the mechanism — `BloomReferenceContract.actionTag(` and
    `BloomReferenceContract.PROFILE_ACTION_ID`. Negative control: renaming the helper call in
    `LiveSiteScreenshotTest` fails the assertion, so the repaired marker is load-bearing rather than
    a spelling that happens to match. The other six markers were verified still present and untouched.

- [ ] TASK-3: review and QA
  - `/review` findings become `TASK-FIX-N`; `/qa` then `/validate`.
  - Result: _pending_
