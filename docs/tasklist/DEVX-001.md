# DEVX-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/DEVX-001.prd.md`
- Research: `docs/research/DEVX-001.md`
- Plan: `docs/plan/DEVX-001.md`

## Tasks

- [x] TASK-1: Trace model, sink, bounded recorder, and the untrusted-value bound
  - New: `app/src/main/java/app/webora/browser/inspector/SiteSkinTrace.kt`,
    `.../inspector/SiteSkinTraceSink.kt`, `.../inspector/SiteSkinTraceRecorder.kt`,
    `.../inspector/InspectorText.kt`
  - New: `app/src/test/java/app/webora/browser/inspector/SiteSkinTraceRecorderTest.kt`,
    `app/src/test/java/app/webora/browser/inspector/InspectorTextTest.kt`
  - Acceptance: the record is closed data with no constructor over detekt's seven parameters and no
    manifest bytes; `SiteSkinTraceSink.None` discards; the recorder keys by full canonical origin,
    keeps the latest record per origin, is bounded at eight origins with insertion-order eviction,
    and can be cleared; `inspectorValue` collapses every whitespace and control character to a
    single space and truncates at `SiteSkinLimits.MAX_SUBTITLE_LENGTH` read from core, not a
    local copy of the number.
  - Tests: `SiteSkinTraceRecorderTest` — latest-wins, eviction order, `clear()`, and that a record
    exposes no `ByteArray`. `InspectorTextTest` — a `SS-W-FIELD-UNKNOWN` pointer forged from a
    manifest key containing `"\nHTTP status: 200"` renders as one line, and an over-long value is
    truncated.
  - Negative control: replaced `inspectorValue`'s body with `return raw.orEmpty()`. All seven
    `InspectorTextTest` cases failed, including the forged-pointer one. Restored, all seven pass.
  - Deviation: eviction is by least-recently-recorded rather than the plan's first-seen insertion
    order — re-recording an origin refreshes its position. A developer moving between a handful of
    origins wants the ones they just visited, and the ordering is pinned by a test rather than left
    to `LinkedHashMap`'s default.
  - Deviation: `inspectorValue` also strips Unicode format characters, which are neither whitespace
    nor control characters. `U+202E RIGHT-TO-LEFT OVERRIDE` reverses the rendering of everything
    after it, so it can make a value read like a browser-authored label without containing a
    newline at all. Covered by its own case.
  - Deviation: the recorder exposes a plain `version` counter instead of holding Compose state.
    Keeping it free of `androidx.compose.runtime` means the JVM gate drives it directly; TASK-5
    keys recomposition on the counter.

- [ ] TASK-2: Transport detail — HTTP status, redirect count, and a reason for refusal
  - Modified: `app/src/main/java/app/webora/browser/siteskin/OkHttpManifestSource.kt`
  - Modified: `app/src/test/java/app/webora/browser/siteskin/OkHttpManifestSourceTest.kt`
  - Acceptance: `ManifestFetchResult.Fetched` and `NotModified` carry the final HTTP status and the
    number of redirects followed; `Rejected` becomes a data class carrying a nullable status and a
    closed `FetchRejection` reason covering non-HTTPS origin, HTTP error status, redirect limit,
    cross-origin redirect, oversized body, and malformed URL; `Unavailable` stays a data object,
    because an `IOException` or timeout has no status and "no answer" must stay distinguishable
    from "answer refused". No fetch decision changes — the same responses are accepted and refused
    as before.
  - Tests: extend `OkHttpManifestSourceTest` with 404 and 500 reporting their status, a
    cross-origin redirect reporting `CROSS_ORIGIN_REDIRECT`, a third redirect reporting the limit,
    an oversized body reporting oversize, and a success reporting its redirect count. The existing
    cancellation and limit tests must still pass unchanged in behaviour.

- [ ] TASK-3: Record discovery, preserve the diagnostics the pipeline drops today
  - Modified: `app/src/main/java/app/webora/browser/siteskin/ManifestDiscoveryCoordinator.kt`
  - New: `app/src/test/java/app/webora/browser/inspector/ManifestDiscoveryTraceTest.kt`,
    `app/src/test/java/app/webora/browser/inspector/SiteSkinTraceNeutralityTest.kt`
  - Acceptance: the coordinator takes a `SiteSkinTraceSink` defaulting to `None` and emits exactly
    one record per `onPageStarted`; rejecting diagnostics from `SiteSkinValidationOutcome.Rejected`
    and warning diagnostics from `Accepted` both reach the record with their JSON pointers; each
    `TraceCacheState` in the plan's table is produced by its own path; a navigation superseded
    before `ensureActive()` records nothing; detekt complexity limits hold without a new suppression.
  - Tests: `ManifestDiscoveryTraceTest` covers one record per call, rejection diagnostics, accepted
    warnings, every cache state, and the cancelled-navigation case.
    `SiteSkinTraceNeutralityTest` is the ticket's central invariant: over the same matrix of inputs,
    `ManifestDiscoveryOutcome` and the resulting `CandidateDisposition` are identical with a
    recording sink installed and with `SiteSkinTraceSink.None`.
  - Negative control: make one coordinator branch behave differently when a sink is installed; the
    neutrality test must fail. Restore and record the result here.

- [ ] TASK-4: Snapshot assembly — theme, chrome, consent, truncation
  - New: `app/src/main/java/app/webora/browser/inspector/InspectorSnapshot.kt`
  - New: `app/src/test/java/app/webora/browser/inspector/InspectorSnapshotTest.kt`
  - Acceptance: assembly is a pure function over the record, the trusted configuration, the observed
    page URL, the consent decision, the global SiteSkin preference, the brand-asset kind and the
    dark-theme flag; it reports declared-versus-rendered counts for navigation, quick actions and
    menu so `take(5/5/20)` truncation is visible; it reports the active navigation id or its
    explicit absence; it reports the applied — that is, contrast-guarded — colour roles beside the
    values the manifest requested; the dark/light selection comes from the passed flag and no
    manifest field can influence it.
  - Tests: `InspectorSnapshotTest` — a six-item navigation shows declared 6 / rendered 5; a page
    matching no pattern yields no active id rather than the first item; a manifest colour that fails
    the guard is reported as corrected; passing `darkTheme = true` selects the dark projection.

- [ ] TASK-5: The debug-only panel and its variant seam
  - New: `app/src/debug/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt`,
    `app/src/debug/java/app/webora/browser/inspector/SiteSkinInspectorPanel.kt`,
    `app/src/debug/res/values/strings.xml`,
    `app/src/release/java/app/webora/browser/inspector/SiteSkinInspectorHost.kt`
  - Modified: `app/build.gradle.kts` (share `src/release/java` into the `debugRelease` source set),
    `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`,
    `app/src/main/java/app/webora/browser/privacy/BrowsingDataCleaner.kt`,
    `app/src/test/java/app/webora/browser/privacy/BrowsingDataCleanerTest.kt`
  - Acceptance: `SITESKIN_INSPECTOR_AVAILABLE` is declared once per variant beside the host, and is
    what gates the recorder — never `BuildConfig.DEBUG`, which is true in `debugRelease` and would
    enable collection in a variant with no panel; the panel renders every field in acceptance
    criterion 1 of the PRD, with browser-authored labels from resources kept in separate `Text`
    nodes from untrusted values; the panel offers no re-validate, override, or consent control;
    `debugRelease` compiles against the shared release stub; `BrowsingDataCleaner` clears the trace
    with the manifest cache and consent.
  - Tests: `BrowsingDataCleanerTest` gains the trace-clearing case, including the existing
    partial-failure path. `:app:assembleDebug` and `:app:assembleDebugRelease` both build.

- [ ] TASK-6: Assert the absence, do not assume it
  - Modified: `app/build.gradle.kts`, `scripts/pre-commit-check.sh`
  - Acceptance: `assertInspectorAbsentFromReleaseVariants` is a `verification`-group task consuming
    the `compileReleaseKotlin` and `compileDebugReleaseKotlin` output directories as
    configuration-cache-safe `Provider`s; it fails if the panel class file appears in either output
    **and** fails if the stub class file is missing from either — without the second half a rename
    makes the check pass vacuously, the failure mode `SpecCorpusTest` guards against by asserting
    its registry in both directions. Wired into `:app:check` like `:siteskin-core`'s
    `assertNoAndroidDependencies`, and invoked unconditionally by `scripts/pre-commit-check.sh` for
    the reason that script already documents about detekt.
  - Tests: this task's test is the Gradle task itself.
  - Negative control: move `SiteSkinInspectorPanel.kt` into `src/main/java`; the task must fail on
    both variants. Then rename the panel class without moving it; the stub-presence half must still
    hold and the absence half must not silently pass. Restore and record both results here.

- [ ] TASK-7: Bring the debug surface inside the accessibility gate, and document
  - Modified: `app/src/test/java/app/webora/browser/browser/BrowserSurfaceConventionsTest.kt`,
    `app/build.gradle.kts`, `CLAUDE.md`, `docs/ROADMAP.md`
  - Acceptance: the conventions scan takes an explicit list of source roots covering
    `src/main/java`, `src/debug/java` and `src/release/java`, each declared as a task input so
    editing a variant composable reruns the scan; the coverage floor rises with the added roots so
    the scan cannot pass vacuously if a root goes missing; the debug panel satisfies the existing
    literal, accessible-name and touch-target rules; `CLAUDE.md` gains a `DEVX-001` section stating
    that availability comes from the variant source set rather than `BuildConfig.DEBUG` and that the
    absence check asserts both directions; `docs/ROADMAP.md` ticks `DEVX-001`.
  - Tests: `BrowserSurfaceConventionsTest`, unchanged rules over widened roots.
  - Negative control: place `Text("debug")` in `SiteSkinInspectorPanel.kt`; the literal rule must
    fail. Then point a root at a non-existent directory; the coverage floor must fail rather than
    the scan passing on fewer files. Restore and record both results here.
