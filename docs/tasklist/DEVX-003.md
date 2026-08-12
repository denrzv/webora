# DEVX-003: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/DEVX-003.prd.md`
- Research: `docs/research/DEVX-003.md`
- Plan: `docs/plan/DEVX-003.md`

## Tasks

- [x] TASK-1: One expression decides whether the inspector is offered
  - Modified: `app/src/main/java/app/webora/browser/siteskin/SiteSkinChromeModel.kt`
  - New tests in: `app/src/test/java/.../SiteSkinChromeModelTest.kt` (or a sibling if the existing
    file is already large — decided by reading it, not guessed here)
  - Acceptance: `BrowserMenuCommand` gains `INSPECTOR`, and `browserMenuCommands()` returns the
    commands the current variant offers — the closed browser section's members plus `INSPECTOR` only
    when `SITESKIN_INSPECTOR_AVAILABLE`. The list is **built from** the constant, never the full enum
    rendered with a no-op handler: a release build must not draw an entry that does nothing. One
    expression, read by both menus, so the condition cannot drift between them. `main` reading a
    variant constant is the existing mechanism (`inspectorRecorder()` already does it), not a new
    one; `BuildConfig.DEBUG` is **not** acceptable, because `debugRelease` sets it true while
    compiling against the release stub.
  - Tests: `browserMenuCommandsOffersInspectorWhenAvailable` — membership matches
    `SITESKIN_INSPECTOR_AVAILABLE`, asserted by reading the constant rather than hardcoding a
    variant, so the same test is meaningful in whichever variant runs it.
    `browserMenuCommandsAlwaysOffersPageInformationAndSettings` — the closed section keeps
    `PAGE_INFORMATION` and `SETTINGS` in every variant, a floor so the list cannot silently shrink.
  - Negative control: return the full enum unconditionally.
    `browserMenuCommandsOffersInspectorWhenAvailable` must fail while the closed-section test still
    passes, proving the second cannot stand in for the first.
  - Result: 9 tests in `SiteSkinChromeModelTest`, 0 failures. `bash scripts/pre-commit-check.sh` OK.
  - **The first negative control passed, and that was the finding.** With
    `browserMenuCommands()` reading `SITESKIN_INSPECTOR_AVAILABLE` inline, replacing the body with
    `BrowserMenuCommand.entries.toList()` changed nothing any test could see: AGP 9.1 creates only
    `testDebugUnitTest`, where the constant is always `true`, so the correct and the broken
    implementation return the same list. The test was decoration in the only variant that runs it.
  - Fix: `browserMenuCommands(inspectorAvailable: Boolean = SITESKIN_INSPECTOR_AVAILABLE)` — the
    thin-wrapper-over-pure-function shape the repository already uses for Android-touching code, for
    the same reason. Both answers are now reachable, and a separate test asserts the default is wired
    to the constant rather than to a second copy of the decision.
  - Negative control, retried: the unconditional enum now fails
    `a variant without a panel is not offered the inspector` alone (1 of 9), on its `false` case.
  - Deviation: `BrowserMenuCommand.INSPECTOR` forced two changes outside the task's file list in the
    same commit, because the compiler requires them. The `when` in `SiteSkinChrome.kt` is exhaustive,
    so the enum value needs its branch immediately; and that branch needs a label **visible to
    `main`**, which `inspector_open` is not — it lives in `src/debug/res` and `main` must compile in
    every variant. Added `inspector_menu_entry` to `app/src/main/res/values/strings.xml`. `TASK-3`
    inherits the rest of the menu wiring.
  - Deviation: `selection retains trusted item and browser menu remains immutable` was updated, which
    research flagged as a warning sign. It is not a loosening: the test's claim is that the
    **manifest** cannot change the section, and it asserted a hardcoded pair as a proxy. That proxy
    now also asserts which *variant* is running, which is a different question. It compares the
    browser menu across two very different configurations instead — the original intent, stated
    directly rather than by proxy.
  - Gate: `bash scripts/pre-commit-check.sh`

- [x] TASK-2: The host renders the panel and nothing else
  - Modified: `app/src/debug/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt`,
    `app/src/release/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt`
  - Acceptance: `SiteSkinInspectorHost(snapshot, open, onClose)` renders the panel when it has a
    snapshot and `open`, and draws nothing otherwise. The `Box`, the
    `WeboraFloatingActionButton`, the internal `open` state, `INSPECTOR_AFFORDANCE_TAG` and
    `AFFORDANCE_INSET` are gone — **the affordance leaves canonical composition because it is not
    there**, not because anything suppresses it during capture. The release stub's signature changes
    with it and keeps `SITESKIN_INSPECTOR_AVAILABLE = false` declared in that file, because the
    second half of the release gate asserts that file's *presence*, not just the panel's absence.
  - Tests: `bash scripts/pre-commit-check.sh`, which runs `assertInspectorAbsentFromReleaseVariants`
    (both halves) and compiles `debugRelease` against the shared release stub — a signature that
    moves in one file and not the other fails the build rather than shipping.
    `./gradlew :app:assembleDebugRelease` run explicitly, since that variant is the one that shares
    `src/release/java` and is easiest to break unnoticed.
  - Result: `bash scripts/pre-commit-check.sh` OK — `assertInspectorAbsentFromReleaseVariants` green
    on both halves, `BrowserSurfaceConventionsTest` green over all three roots.
    `./gradlew :app:assembleDebugRelease` BUILD SUCCESSFUL.
  - Both hosts lost every import except `Composable`. The debug file is now the constant, a KDoc and
    a three-line composable; the release file is the constant and an empty one. The two signatures
    moved in the same commit, which is the only way `debugRelease` — the variant that compiles
    `src/release/java` against debug's caller — stays buildable.
  - Deviation: `BrowserScreen.kt` changed in this task, outside its file list, because the compiler
    requires it. `SiteSkinInspectorHost` gained two parameters, so its single call site cannot be
    left for `TASK-3`. The minimum that compiles is the hoist itself — `var inspectorVisible by
    remember { mutableStateOf(false) }` passed as `open`, with `onClose` clearing it — so that is
    what was done, rather than a hardcoded `false` that would be a dead branch for one commit.
    `TASK-3` keeps the menu wiring that sets it; after this commit the inspector is composed but not
    yet reachable, which is the honest intermediate state.
  - Checked, not assumed: `WeboraFloatingActionButton` is not now dead code — `SiteSkinQuickActions`
    is its other caller. Removing the affordance took the last *browser-owned* floating button out of
    canonical composition and left the site-driven one, which is exactly the split `CI-003` wants.
  - `INSPECTOR_AFFORDANCE_TAG` had no `androidTest` reference to break; the instrumented journey
    never touched it. Its only readers were this file and the tickets' own prose.
  - Gate: `bash scripts/pre-commit-check.sh` plus `./gradlew :app:assembleDebugRelease`

- [x] TASK-3: Reach the inspector from both menus, in two interactions
  - Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`,
    `app/src/main/java/app/webora/browser/siteskin/SiteSkinChrome.kt`,
    `app/src/main/res/values/strings.xml`
  - Acceptance: `BrowserScreen` hoists `inspectorVisible` and passes it with an `onClose` to the
    host. The integrated menu renders `browserMenuCommands()` and maps `INSPECTOR` to a string
    resource beside `PAGE_INFORMATION` and `SETTINGS`; `onBrowserSelect` sets `inspectorVisible` for
    it. `AddressBar`'s `DropdownMenu` offers the same entry from the same decision. Result: **menu →
    entry, two interactions, in both regular and integrated mode** — the count that ruled out putting
    it in `PrivacySettingsScreen`, which research measured at three because `Settings` is itself two
    deep in each mode. The label comes from `strings.xml`; reuse `inspector_open` only if it reads
    correctly as a menu item, otherwise add one.
  - Tests: `BrowserSurfaceConventionsTest` green over all three source roots — it fails a string
    literal reaching `Text(` or an accessible name, which is the rule governing this entry.
    `SiteSkinChromeModel`'s existing tests unchanged: if an expectation needs **loosening**, that is a
    finding rather than a fix, because the browser section is closed on purpose.
    `./gradlew :app:compileDebugAndroidTestKotlin` explicitly, since `INSPECTOR_AFFORDANCE_TAG` may
    have instrumented references and the gate never compiles that source set.
  - Result: `bash scripts/pre-commit-check.sh` OK, `./gradlew :app:compileDebugAndroidTestKotlin`
    and `:app:assembleDebugRelease` BUILD SUCCESSFUL. `BrowserSurfaceConventionsTest` green over all
    three roots; `SiteSkinChromeModelTest`'s expectations needed no change, which is the outcome the
    plan wanted — a closed section that had to be loosened would have been a finding.
  - Regular mode now renders **the same list**, not a second copy of it. `AddressBar`'s
    `DropdownMenu` iterates `browserMenuCommands()` and dispatches through an exhaustive `when`,
    where `PAGE_INFORMATION` keeps its existing no-op. Before this it hardcoded two
    `DropdownMenuItem`s, so the two modes' browser sections were free to drift; now a command added
    to the enum fails to compile until both dispatchers handle it.
  - `browserMenuLabel(command)` was extracted alongside, for the same reason `browserMenuCommands()`
    is one expression: with the label `when` duplicated, the two menus could offer one command under
    two names. One decision and one label mapping, two renderers.
  - Interaction count, as the criterion requires: regular is `More` (1) → `SiteSkin inspector` (2);
    integrated is the SiteSkin menu (1) → the same entry in the closed browser section (2). An entry
    inside `PrivacySettingsScreen` would have been three in both, because `Settings` is itself
    reached through these very menus.
  - `INSPECTOR` is not reachable in a release variant by two independent mechanisms, not one: it is
    absent from `browserMenuCommands()`, so nothing draws it; and the release `SiteSkinInspectorHost`
    ignores `open` entirely. Neither relies on the other.
  - Gate: `bash scripts/pre-commit-check.sh` plus `./gradlew :app:compileDebugAndroidTestKotlin`

- [x] TASK-4: Run the hosted workflow and confirm the frames
  - Modified: `docs/SCREENSHOTS.md` if the frames' description changes
  - Acceptance: the Android screenshots workflow runs on this branch and a human opens `preview.png`
    to confirm **no inspector affordance in any of the three canonical frames**. The session cannot
    download artifacts (`403 GitHub access is not enabled for this session`), so this is the owner's
    confirmation, recorded as instrumented evidence and never promoted to a gate claim.
  - Also record: `CI-003`'s `rendered-03-siteskin-integrated.txt` still reports a passing fraction
    well clear of the 1% threshold — the page, not the chrome, is what clears it now. The `excluded=`
    line should still name only the quick action, confirming nothing new needed excluding.
  - Tests: hosted run. `bash scripts/pre-commit-check.sh`.
  - Result: run **12** (`31617251038`, `140d206e`) succeeded. `test_status=0`, `png_count=3`,
    `composed tiles=3 against png_count=3`. APKs built in 5m42s *before* the emulator launched, per
    `CI-002`'s ordering; the journey then took 6m13s on the device.
  - What that already proves, without opening anything: **frame 03 passed `CI-003`'s rendered check
    with the overlay gone.** `captureWhenRendered` fails the run when the page region never clears
    `MINIMUM_DIFFERING_FRACTION`, and the inspector's pixels are no longer in that region to help it
    — so a green run is the page itself clearing the bar. If the overlay had been load-bearing, this
    is the run that would have gone red.
  - What still needs the artifact, and is therefore **owner-confirmed instrumented evidence, never a
    gate claim**: the winning fraction in `rendered-03-siteskin-integrated.txt`, the `excluded=` line
    naming only `SITESKIN_QUICK_ACTIONS_TAG`, and the visual confirmation that no inspector
    affordance appears in any of the three frames of `preview.png`. This session cannot download
    artifacts (`403 GitHub access is not enabled for this session`).
  - `webora-screenshots-140d206ebc51761eb4a3efe43dbb5b2706320af5` — check the SHA against the commit
    being judged before reading it, which `DEVX-002` added the naming for after run #5's frames were
    read as current evidence.
  - No change needed to `docs/SCREENSHOTS.md`: the frames' journey and descriptions are unchanged.
    What left the frames was chrome the document never described.
  - Gate: `bash scripts/pre-commit-check.sh`

- [x] TASK-5: Record the decision in the project's own documentation
  - Modified: `CLAUDE.md`, `docs/ROADMAP.md`
  - Acceptance: `CLAUDE.md` gains a `DEVX-003` section stating that the affordance moved into the two
    menus rather than being hidden during capture, and **why a screenshot mode was refused** — a
    frame is evidence because nothing arranged the screen for the camera, the same reason `CI-002`
    declined a dismiss-whatever-is-in-the-way loop. It records the interaction arithmetic that chose
    the menus over settings, that presence comes from the variant constant and never
    `BuildConfig.DEBUG`, and that this closes `CI-003`'s residual hole by removing a full-screen
    sibling overlay rather than by extending an exclusion list. `docs/ROADMAP.md` ticks `DEVX-003`
    and drops the "also closes `CI-003`'s residual hole" forward-reference now that it has happened.
    No source, test, resource or build file changes in this task.
  - Tests: `bash scripts/pre-commit-check.sh`
  - Result: `bash scripts/pre-commit-check.sh` OK. `CLAUDE.md` gains *The inspector lives in the
    menus (DEVX-003)*, leading with the refused screenshot mode, since that is the decision most
    likely to be re-proposed. `docs/ROADMAP.md` ticks the ticket and states the hole as closed rather
    than pending.
  - Also corrected, outside the stated list but in the same file: `CLAUDE.md`'s `CI-003` section
    claimed the inspector overlay **is** still inside the measured region and unexcluded. That was
    true when written and is now false — leaving it would have made the guidance document describe a
    hole that no longer exists, which is worse than silence. Rewritten in past tense with the rule
    that outlives it: anything new composed into that `Box` must be excluded or kept out.
  - The negative-control finding from `TASK-1` is recorded in `CLAUDE.md` too, not only in this
    tasklist. A variant-gated decision read inline is untestable in the only variant AGP builds tests
    for, and the next person adding one will reach for exactly that shape.
  - Gate: `bash scripts/pre-commit-check.sh`

## Review fixes

- [x] TASK-FIX-1: Delete the affordance's orphaned label
  - Modified: `app/src/debug/res/values/strings.xml`
  - Acceptance: `inspector_open` is gone. It lost its only call site when the affordance did, and its
    value is identical to the live `inspector_title` — two identical strings where one is dead is how
    someone later edits the wrong one and sees no change. The menu entry's label stays
    `inspector_menu_entry` in `src/main/res`, which is where it has to live: `main` compiles in every
    variant and cannot see a debug-only resource.
  - Result: `bash scripts/pre-commit-check.sh` OK, `./gradlew :app:assembleDebug` BUILD SUCCESSFUL —
    the variant that compiles the debug resource set and would have failed on a dangling reference.
  - Gate: `bash scripts/pre-commit-check.sh` plus `./gradlew :app:assembleDebug`

- [ ] TASK-FIX-2: A browser command's handler is not optional
  - Modified: `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`,
    `app/src/androidTest/java/app/webora/browser/browser/BrowserSiteSkinLayoutTest.kt`
  - Acceptance: `RegularBrowser` no longer defaults `onSettings` or `onInspector` to `{}`. The
    ticket's own rule is that a variant must not offer a command it cannot service — which is why the
    list is built from the constant rather than rendered-then-no-opped. A defaulted no-op callback
    reintroduces that failure one layer down, and `BrowserSiteSkinLayoutTest` already composes
    `RegularBrowser` with both entries inert. Both call sites pass explicit handlers.
  - Gate: `bash scripts/pre-commit-check.sh` plus `./gradlew :app:compileDebugAndroidTestKotlin`
