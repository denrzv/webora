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
  - Note: the file is named for its type, not the ticket — detekt's `MatchingDeclarationName`
    requires a file with one top-level type to carry that type's name.
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

- [x] TASK-3: a browser-owned control row in the integrated header
  - Modified: `app/src/main/java/app/webora/browser/siteskin/SiteSkinTopBar.kt`
  - Modified: `app/src/test/java/app/webora/browser/siteskin/SiteSkinTopBarContractTest.kt`
  - Modified: `app/src/androidTest/java/app/webora/browser/siteskin/SiteSkinTopBarTest.kt`
  - Modified: `app/src/test/java/app/webora/browser/siteskin/SiteSkinNavigationContractTest.kt`,
    `app/src/androidTest/java/app/webora/browser/browser/BrowserSiteSkinLayoutTest.kt`
  - Finding, in the task: `browserOwnedBack` required `MaterialTheme.colorScheme.surfaceContainer`
    *inside* `BrowserBack`, which was the same thing as the rule only while Back was the header's
    one browser control. The shared tile made Back's guarantee true one declaration away and the
    scan went red on code that had not weakened. Fixed by following the indirection, not by
    dropping the clause — `BROWSE-009`: forbid the mechanism, not a spelling. Its own negative
    control was also passing for the wrong reason (the fragment lacked the `BrandLogo` marker the
    slice ended at, so it returned `false` before evaluating anything); the slice now ends at
    end-of-input and the control fails on its merits.
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
    - Ground the tile on `presentation.colors.secondary` → **3 of 11 failed**: the ownership,
      shared-tile and no-weight cases. The first attempt at this control did not compile, which is
      not a passing control — it exercises nothing. Threading the colour through the call sites is
      what made it a real violation, and the difference is invisible in a grep for `FAILED`.
    - Move the control into the brand row → **1 of 11 failed**, and only the placement case. One
      targeted failure is what separates a guard from a broken file.
    - Point the refresh tile at `SITESKIN_BACK_TAG` while keeping the constant declared → **2 of 11
      failed**: the applied-tag case and the shared-tile case. `UX-020`'s lesson holds — the
      constant's declaration alone would have satisfied a bare `contains`.

- [x] TASK-4: give both modes the one decision
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
    - Reintroduce `onReload = controller::reload` and the inline `canReload` rule in the regular
      arm → **1 of 8 failed**, the single-owner case.
    - Construct a second `RefreshAction` in `SiteSkinTopBar.kt` → **1 of 8 failed**, the same case.
    - Finding, in the task: the first `decidesReload` predicate keyed on `loadFailure` +
      `navigate(` + `displayedUrl` and reported `BrowserScreen` as a second owner — all three occur
      there for unrelated reasons, `BrowserErrorPage`'s own Retry among them. Co-occurrence across a
      whole file is not a mechanism; it is now keyed on *constructing* a `RefreshAction`, which a
      `when` branch does not do. The call-site half was also rewritten from two negatives on old
      spellings into positive assertions that both chromes name the shared values — a negative on
      one spelling is satisfied by writing the rule a second way, which is `BROWSE-009`'s
      `update(activeTabId)` lesson.

- [x] TASK-5: prove a refresh cannot cross a tab
  - Modified: `app/src/androidTest/java/app/webora/browser/browser/TabRendererIsolationTest.kt`
  - Modified: `app/src/test/java/app/webora/browser/browser/RendererHostContractTest.kt`
  - Scope change, deliberate: the plan gave this task instrumented coverage only, which no local
    gate runs and which therefore has no runnable negative control. A gate-level half was added
    beside it — the dispatcher may not name `activeId`, may not reach the controller map, and may
    not write session state, because a refresh's consequences are observations and observations
    have one route. `BROWSE-009`'s own lesson: the guard that reached `main` was the one the gate
    could not drive.
  - Refreshing tab A leaves tab B's displayed URL, loading flag, history capability and `BrowserMode`
    untouched, including when the user switches to B while A's reload is in flight.
  - Acceptance:
    - The new case drives two live tabs and asserts on B's observed state, not on A's.
    - No production code changes in this task; if one turns out to be needed, it is a finding and
      gets its own task.
  - Tests: `TabRendererIsolationTest`; instrumented, so recorded as evidence and never as a gate
    claim.
  - Negative controls:
    - Resolve the controller through `controllers.getValue(session.activeId)` in the dispatcher →
      **1 of 5 failed** in `RendererHostContractTest`, the ownership case.
    - Have the dispatcher write session state (`session = session.updateActive { … }`) → **1 of 5
      failed**, the same case. Both run in the JVM gate.
    - The instrumented case cannot be run here (no device) and is published as hosted evidence,
      never as a gate claim — the standing rule from `CI-002` through `CI-005`. It compiles under
      `:app:compileDebugAndroidTestKotlin`, which is checked explicitly because
      `scripts/pre-commit-check.sh` does not compile `androidTest`.

- [ ] TASK-6: write down what this decided
  - Modified: `CLAUDE.md`, `docs/ROADMAP.md`
  - The architecture note: why the brand row could not hold it (with the arithmetic), why the failure
    path is `Retry` and not `reload()`, and that `CI-009`'s hosted acceptance is re-taken rather than
    inherited.
  - Acceptance:
    - The note states the measurement, not just the conclusion.
    - It records that the site's own `refresh` action deliberately survives.
  - Tests: `bash scripts/pre-commit-check.sh`.
