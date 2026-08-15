# BROWSE-006: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/BROWSE-006.prd.md`
- Research: `docs/research/BROWSE-006.md`
- Plan: `docs/plan/BROWSE-006.md`

## Tasks

- [x] TASK-1: Add the bounded browser session and restoration domain
  - New: `BrowserSession.kt`, `BrowserSessionSnapshot.kt`, `BrowserSessionTest.kt`, and
    `BrowserSessionSnapshotTest.kt`.
  - Acceptance: one-to-eight non-empty tabs; unique opaque ids; deterministic create/select/close;
    addressed updates cannot touch another tab; primitive versioned restoration retains order and
    selection, rejects malformed/unsafe metadata, and always downgrades restored pages to regular
    mode.
  - Tests: focused session/snapshot JVM tests and negative controls; `:app:testDebugUnitTest`;
    `bash scripts/pre-commit-check.sh`.
  - Result: `BrowserSession` now makes the one-to-eight invariant, unique ids, addressed updates,
    deterministic neighbour selection and fresh-final-tab replacement explicit. Its versioned
    snapshot retains only Home/page identity and safe committed HTTP(S) URLs, drops malformed and
    duplicate entries, bounds restored input, and reconstructs every page in regular mode. The
    focused tests fail if the cap, addressed isolation, close rule, scheme/length validation, or
    integrated-mode downgrade is removed.

- [x] TASK-2: Give every tab an independent live renderer and async boundary
  - Modified: `BrowserScreen.kt`, `HardenedWebView.kt`, `BrowserWebViewController.kt`,
    `BrowsingDataCleaner.kt`, related tests; add focused Android renderer test.
  - Acceptance: identity-keyed controllers/WebViews retain independent live history and observed
    state; selection does not navigate; close destroys its renderer; discovery, consent, branding
    and pending native actions cannot publish under another tab; recreation uses the safe snapshot;
    clear browsing data reaches all live renderers.
  - Tests: focused JVM tests, compile Android tests, negative controls for stale tab callbacks and
    incomplete clearing; `:app:testDebugUnitTest`; `bash scripts/pre-commit-check.sh`.
  - Result: `BrowserScreen` now restores a bounded session, addresses renderer observations and
    SiteSkin activation to an owning tab id/generation, and allocates one retained controller per
    tab. `HardenedWebView` reattaches that tab's existing hardened renderer without reloading it,
    while browser-data clearing fans out over all live controllers. Switching therefore cannot
    rewrite another tab's state, and restored integrated pages deliberately re-enter regular
    discovery. Android tests compile; runtime execution is unavailable without a connected device.

- [x] TASK-3: Add the accessible browser-owned tab switcher
  - New/modified: `TabSwitcher.kt`, `BrowserChrome.kt`, strings/resources, model/JVM tests and
    Compose Android tests.
  - Acceptance: the current chrome opens a real switcher; rows expose bounded browser summaries,
    selection/count and 48 dp select/close targets; new/select/close follow the session reducer;
    eight-tab limit is visible and creation disabled; manifest/editable text cannot author labels.
  - Tests: focused model and source-contract JVM tests, compile Compose instrumentation, negative
    controls for manifest text and enabled-at-limit; `:app:testDebugUnitTest`;
    `bash scripts/pre-commit-check.sh`.
  - Result: regular and integrated browser menus now expose the same Tabs command and an accessible
    switcher with ordered selection/close actions, stable tags, browser-observed domain summaries,
    and an explicit disabled eight-tab limit. Create, select and close delegate to the session;
    close destroys the owned renderer immediately. Model tests prove manifest branding and editable
    address text cannot author tab labels, and the Compose limit/selection test compiles.

- [x] TASK-4: Complete review, QA, documentation and validation
  - Modified: `reports/review/BROWSE-006.md`, `reports/qa/BROWSE-006.md`, `CLAUDE.md`, roadmap and
    workflow artifacts.
  - Acceptance: `/review` findings are resolved through separate `TASK-FIX-*` commits if required;
    QA records all acceptance evidence and environment limits; normative docs describe the tab
    trust/restoration boundary; `/validate` reports no drift and marks the ticket complete.
  - Tests: documentation consistency checks and final `bash scripts/pre-commit-check.sh`; connected
    instrumentation/screenshot evidence only if a device is available.
  - Result: review findings were resolved in three provenance-linked commits; QA passes every
    runnable scenario and records device-only instrumentation/screenshots as unavailable; `CLAUDE.md`
    now pins the tab identity, renderer and restoration trust boundaries and the roadmap is updated.

- [x] TASK-FIX-1: Address retained renderer callbacks to their owning tab
  - Source: `/review finding 1`
  - Acceptance: a callback captured by tab A updates only A after tab B becomes selected; selection,
    URL, mode and navigation state of B remain unchanged.
  - Tests: focused JVM negative control; `:app:testDebugUnitTest`; pre-commit gate.
  - Result: renderer observations now capture and update their composing tab id rather than the
    mutable active selection; the negative control selects B, publishes a late observation from A,
    and proves B is byte-for-byte unchanged while A receives the update.

- [x] TASK-FIX-2: Keep the tab switcher reachable from Home
  - Source: `/review finding 2`
  - Acceptance: Home exposes a browser-authored 48 dp Tabs action that opens the same switcher used
    by regular and integrated menus without introducing the `UX-011` shell redesign.
  - Tests: Home Compose test compiles; relevant unit tests; pre-commit gate.
  - Result: Home now presents a full-width browser-authored Tabs action and composes the same
    switcher as regular/integrated mode. Shared switcher wiring keeps create/select/close behaviour
    identical while leaving the persistent navigation shell to `UX-011`.

- [x] TASK-FIX-3: Destroy all retained renderers when BrowserScreen is disposed
  - Source: `/review finding 3`
  - Acceptance: screen disposal destroys every retained controller exactly once; tab switching
    continues to detach/reattach without destruction.
  - Tests: controller lifecycle source/JVM seam where possible; Android tests compile; pre-commit gate.
  - Result: a screen-scoped disposal effect destroys every controller still in the retained map and
    clears the map. Closed tabs are removed before this sweep, so each owned renderer is destroyed
    once; ordinary tab selection still uses the non-destructive detach/reattach path.
