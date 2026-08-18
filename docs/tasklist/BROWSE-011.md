# BROWSE-011: Tasklist
Status: TASKLIST_READY

References:
- Issue: [denrzv/webora#116](https://github.com/denrzv/webora/issues/116)
- PRD: `docs/prd/BROWSE-011.prd.md`
- Research: `docs/research/BROWSE-011.md`
- Plan: `docs/plan/BROWSE-011.md`

Each task is one commit, `bash scripts/pre-commit-check.sh` before every one, and the branch is
pushed before the next task starts. Negative-control *results* are recorded inline as they are run —
a control that fails nothing is a finding, not a formality.

## Tasks

- [x] TASK-1: free the ticket id
  - Modified: `docs/BACKLOG.md`, `docs/ROADMAP.md`, `docs/tasklist/BROWSE-010.md`, `CLAUDE.md`
  - The backlog's reserved `BROWSE-011` (Back after a Home round trip) becomes `BROWSE-012`. Docs
    only, and first, so nothing later in this ticket is written against a colliding id.
  - Acceptance:
    - `BROWSE-012` is the Home/Back defect in every file that cites it, with its `Depends on`,
      `Found by` and cross-references intact.
    - No file cites `BROWSE-011` for the Home/Back defect.
    - `CLAUDE.md`'s `BROWSE-010` note points at `BROWSE-012`.
  - Tests: `grep` both ids across the repo; `bash scripts/pre-commit-check.sh`.

- [x] TASK-2: one owner for what refreshing means
  - New: `app/src/main/java/app/webora/browser/browser/RefreshAction.kt`
  - New: `app/src/test/java/app/webora/browser/browser/PageRefreshTest.kt`
  - `RefreshAction` (`Reload`, `Retry(url)`, `None`) and `refreshAction(BrowserState)`. Pure, closed,
    reading only `displayedUrl` and `loadFailure`. Not wired to anything yet.
  - Acceptance:
    - A committed page yields `Reload`; a failure with a retry URL yields `Retry` with that exact
      URL; a failure without one yields `Reload`; a blank `displayedUrl` and a default
      `BrowserState()` both yield `None`.
    - An `Integrated` state carrying a hostile manifest yields the same action as the otherwise
      identical `Regular` state.
    - The hierarchy is sealed with no URL-carrying case other than `Retry`.
  - Tests: `PageRefreshTest`.
  - Negative controls:
    - Re-point the failure row at `Reload` (delete the `loadFailure` line) → **2 of 6 failed**:
      the retry case and the integrated-parity case, which asserts the retry target too. Both
      name a URL the browser could not otherwise reach.
    - Make `refreshAction` read `state.mode` (`Integrated → None`) → **1 of 6 failed**: the
      parity case, and only it. A control that fails one targeted case is what separates a
      guard from a broken file — `BROWSE-009` records the same discriminator requirement.

- [ ] TASK-3: a browser-owned control row in the integrated header
  - Modified: `app/src/main/java/app/webora/browser/siteskin/SiteSkinTopBar.kt`
  - Modified: `app/src/test/java/app/webora/browser/siteskin/SiteSkinTopBarContractTest.kt`
  - Modified: `app/src/androidTest/java/app/webora/browser/siteskin/SiteSkinTopBarTest.kt`
  - `SiteSkinTopBar` becomes a `Column` of the extracted-verbatim `BrandRow` and a new
    `BrowserControlRow` holding Refresh, trailing-aligned. Back's tile generalises into one shared
    `BrowserControlTile`. New `canRefresh` / `onRefresh` parameters, neither defaulted.
  - Acceptance:
    - The brand row's children, order, tags and colours are byte-equivalent to before the extraction.
    - Refresh uses `R.drawable.ic_reload` and `R.string.reload`; no new drawable, so
      `BrowserIconContractTest`'s budget of 20 is unchanged.
    - Its tile grounds on `MaterialTheme.colorScheme.surfaceContainer`, as Back's does.
    - The control row declares no `Modifier.weight(1f)`.
    - Detekt is green without a `LongMethod` suppression.
  - Tests:
    - `SiteSkinTopBarContractTest`: no `presentation`/`colors.*`/`model.*` value inside the refresh
      control's declaration; `testTag(SITESKIN_REFRESH_TAG)` applied rather than merely declared; the
      five existing chip assertions unchanged and green.
    - `SiteSkinTopBarTest`: Refresh displayed, enabled, ≥48 dp, dispatches exactly once; disabled
      when `canRefresh` is false; description distinct from the chip's; at 320 dp × 200 % font scale
      both the control and the chip stay inside the host and the chip keeps its 140 dp floor.
  - Negative controls:
    - Ground the tile on `presentation.colors.secondary` → the isolation case must fail.
      Result: _to record_.
    - Drop `testTag(SITESKIN_REFRESH_TAG)` from the node while keeping the constant → the applied-tag
      case must fail. Result: _to record_.

- [ ] TASK-4: give both modes the one decision
  - Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`
  - Modified: `app/src/test/java/app/webora/browser/browser/BrowserChromeContractTest.kt`
  - `dispatchRefresh` beside `navigateBack`; `RegularBrowser` passes `canRefresh`/`onRefresh` to
    `SiteSkinTopBar`; the regular arm's `canReload`/`onReload` copy is replaced by the shared
    decision. `ResolvedAction.Refresh` untouched. Home's shell keeps its literal `false`.
  - Acceptance:
    - Both modes' enabled state and dispatch come from `refreshAction`.
    - `BrowserNavigationShell`'s signature is unchanged and regular-mode Reload still reloads a
      committed page.
    - The site action at `BrowserScreen.kt:299` still dispatches through `ActionResolver`.
  - Tests:
    - `BrowserChromeContractTest`: exactly one file declares the reload decision, and the regular arm
      no longer names `controller::reload`.
    - `:app:compileDebugAndroidTestKotlin` — the gate does not compile `androidTest`, and `CI-003`
      records a compile error surviving a green `scripts/pre-commit-check.sh`.
  - Negative controls:
    - Reintroduce `onReload = controller::reload` in the regular arm → the single-owner case must
      fail. Result: _to record_.

- [ ] TASK-5: prove a refresh cannot cross a tab
  - Modified: `app/src/androidTest/java/app/webora/browser/browser/TabRendererIsolationTest.kt`
  - Refreshing tab A leaves tab B's displayed URL, loading flag, history capability and `BrowserMode`
    untouched, including when the user switches to B while A's reload is in flight.
  - Acceptance:
    - The new case drives two live tabs and asserts on B's observed state, not on A's.
    - No production code changes in this task; if one turns out to be needed, it is a finding and
      gets its own task.
  - Tests: `TabRendererIsolationTest`; instrumented, so recorded as evidence and never as a gate
    claim.
  - Negative controls:
    - Resolve the refresh target through `session.activeId` at delivery time → the switched-tab case
      must fail. Result: _to record_.

- [ ] TASK-6: write down what this decided
  - Modified: `CLAUDE.md`, `docs/ROADMAP.md`
  - The architecture note: why the brand row could not hold it (with the arithmetic), why the failure
    path is `Retry` and not `reload()`, and that `CI-009`'s hosted acceptance is re-taken rather than
    inherited.
  - Acceptance:
    - The note states the measurement, not just the conclusion.
    - It records that the site's own `refresh` action deliberately survives.
  - Tests: `bash scripts/pre-commit-check.sh`.
