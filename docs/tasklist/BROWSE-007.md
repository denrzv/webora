# BROWSE-007: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/BROWSE-007.prd.md`
- Research: `docs/research/BROWSE-007.md`
- Plan: `docs/plan/BROWSE-007.md`

## Tasks

- [x] TASK-1: Add the bounded local browsing-record store
  - New: browsing record/domain, codec, Android preference adapter, and pure tests.
  - Acceptance: canonical HTTP(S)-only full-URL identity; bounded sanitized titles; 200 visits,
    ten deduplicated recents and 100 URL-keyed favourites; corrupt input fails closed; deterministic
    ordering and persistence round trips are covered by negative controls.
  - Tests: focused JVM tests; `:app:testDebugUnitTest`; `detekt`; pre-commit gate.
  - Result: the pure store now canonicalizes full HTTP(S) URLs, strips fragments, rejects unsafe
    schemes/credentials, sanitizes and bounds titles, and persists independently bounded history
    and URL-keyed favourites through a versioned total codec. Tests prove corrupt input is dropped,
    recents deduplicate without erasing visits, limits hold, and history clearing retains favourites.

- [x] TASK-2: Record successful main-frame visits and integrate clear semantics
  - Modified: WebView callbacks, `BrowserScreen`, `BrowsingDataCleaner`, privacy copy and tests.
  - Acceptance: one record per successful main-frame completion for ordinary/integrated pages;
    failed/subframe/Home/unsupported pages do not record; clearing deletes history, retains
    favourites, participates in failure aggregation, and is described before confirmation.
  - Tests: callback/store/cleaner negative controls, unit suite, compiled Android tests, detekt,
    pre-commit gate.
  - Result: the hardened main-frame completion seam now carries the browser-observed URL/title;
    `BrowserScreen` deduplicates each tab's completed navigation before recording. The aggregate
    cleaner invokes history clearing with failure isolation while favourites remain outside it, and
    confirmation copy states that retention. Callback, cleaner ordering/failure, unit and compiled
    Android tests cover the new paths.

- [x] TASK-3: Populate Home and add explicit favourite controls
  - Modified: Home, regular/integrated browser menus, strings, models and Compose/source tests.
  - Acceptance: empty states are conditional; populated newest-first recents/favourites open exact
    validated URLs; current page exposes browser-owned Add/Remove; favourite survives recreation;
    rows/actions retain accessible 48 dp semantics and cannot be authored by a manifest/title.
  - Tests: focused model/JVM and compiled Compose instrumentation, detekt, pre-commit gate.
  - Result: Home now conditionally renders real newest-first recent/favourite cards with exact stored
    destinations and explicit accessible removal actions. Regular and integrated browser-owned menus
    expose the same dynamic Add/Remove favourite action; persistence keys remain canonical URLs and
    labels reuse only bounded observed history titles. Populated/empty Compose tests compile and the
    store recreation negative control proves favourite durability.

- [x] TASK-4: Complete review, QA, documentation and validation
  - Modified: review/QA reports, `CLAUDE.md`, roadmap/backlog and task results.
  - Acceptance: review findings become provenance-linked `TASK-FIX-*` commits; runnable QA is green;
    device/network-capture limitations are explicit; normative docs pin the local-data boundary;
    validate reports no artifact or task drift.
  - Tests: documentation checks and final pre-commit gate.
  - Result: review found one failed-load completion gap and resolved it in `TASK-FIX-1`; QA passes
    every runnable scenario and records device instrumentation, packet capture and screenshot limits.
    `CLAUDE.md` pins identity, completion, persistence, privacy and clear boundaries; roadmap/backlog
    and workflow artifacts are validated.

- [x] TASK-FIX-1: Do not record failed main-frame navigations
  - Source: `/review finding 1`
  - Acceptance: start → main-frame failure → finish emits no successful completion; a subsequent
    successful navigation still emits exactly one completion.
  - Tests: focused WebView-client negative control, unit suite, detekt, pre-commit gate.
  - Result: the hardened client now resets failure state at main-frame start, records the failed URL
    on a main-frame error, and suppresses its matching finish callback. The negative control proves
    the error document does not complete while the immediately following successful navigation does.
