# BROWSE-008: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/BROWSE-008.prd.md`
- Research: `docs/research/BROWSE-008.md`
- Plan: `docs/plan/BROWSE-008.md`

## Tasks

- [x] TASK-1: Define and integrate the shared browser Back contract
  - Add a pure policy/executor, then wire regular chrome, integrated chrome, and system Back to one
    active-tab action with live WebView history precedence and native Home fallback.
  - Acceptance: first-page Back is available; history wins; Home delegates; fallback resets only
    the active state and removes integrated presentation.
  - Tests: focused JVM negative controls, app unit suite, detekt, pre-commit gate.
  - Result: a pure browser-owned Back policy now makes every non-Home tab consumable, consults the
    active controller's live history first, and falls back to resetting only the active tab to
    native Home. Regular chrome, fixed integrated chrome, and system/predictive Back share that
    availability and callback; JVM negative controls prove history precedence and Home delegation.

- [x] TASK-2: Prove UI, tab, and Android integration behavior
  - Extend Compose/session coverage for regular and integrated enabled state, active-tab isolation,
    and the user-visible first-page fallback contract.
  - Acceptance: compiled instrumentation covers visible controls; another tab remains unchanged;
    no screenshot-only hook or fake navigation state is introduced.
  - Tests: app unit tests, compiled Android tests, detekt, pre-commit gate.
  - Result: regular and integrated Compose coverage now renders a first page with observed WebView
    history unavailable while browser Back remains enabled and dispatches the shared callback. A
    session negative control resets the selected tab to a blank Home state and proves the inactive
    tab is byte-for-byte unchanged; Android instrumentation compiles without test-only navigation.

- [x] TASK-3: Complete review, QA, documentation, and validation
  - Produce review and QA reports, resolve findings through `TASK-FIX-*` if needed, update normative
    architecture/ticket tracking, and validate all artifacts and gates.
  - Acceptance: review is resolved, QA passes all runnable scenarios, environment limitations are
    explicit, and final validation finds no task/artifact drift.
  - Tests: final pre-commit gate and workflow/status checks.
  - Result: review resolved with no findings; QA passed every runnable scenario and records the
    unavailable device, predictive-gesture, screenshot, and hosted-run evidence explicitly.
    Normative architecture now pins history → Home → platform precedence, while roadmap/backlog
    tracking closes this product fix without claiming CI-007's downstream hosted acceptance.
