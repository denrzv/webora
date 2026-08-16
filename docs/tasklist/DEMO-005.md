# DEMO-005: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/DEMO-005.prd.md`
- Research: `docs/research/DEMO-005.md`
- Plan: `docs/plan/DEMO-005.md`

## Tasks

- [x] TASK-1: align the executable live journey with the expressive Bloom story
  - Modified: `app/src/androidTest/java/app/webora/browser/visual/LiveSiteScreenshotTest.kt`
  - Modified: `app/build.gradle.kts`, `gradle/libs.versions.toml`
  - New: `app/src/test/java/app/webora/browser/evidence/ExpressiveBloomJourneyContractTest.kt`
  - Modified: `docs/WALKTHROUGH.md`
  - Replace the retired persistent-bottom-navigation wait with expressive header/dock checks; open
    Happy Days through normal web accessibility, exercise Back/Forward, open the hub and assert the
    manifest route/action vocabulary, then require complete teardown at the regular origin.
  - Acceptance: source negative control rejects the legacy wait; production journey contains all
    M9 checkpoints; focused tests, instrumentation compilation, and pre-commit gate pass.
  - Tests: `./gradlew :app:testDebugUnitTest --tests '*ExpressiveBloomJourneyContractTest'`,
    `./gradlew :app:compileDebugAndroidTestKotlin`, live lint when reachable, and pre-commit.
  - Runtime: execute/capture only with a connected device; otherwise record managed-cloud limitation.
  - Result: the journey now uses an instrumentation-only accessibility selector to open the real
    Happy Days page, proves integrated Back/Forward and the trusted hub vocabulary, and requires
    expressive/hub teardown at the regular origin. The JVM negative contract passes and Android
    instrumentation compiles; no device or `/dev/kvm` is available for runtime capture.

- [x] TASK-2: record review, QA, architecture, and roadmap completion
  - New: `reports/review/DEMO-005.md`
  - New: `reports/qa/DEMO-005.md`
  - Modified: `CLAUDE.md`
  - Modified: `docs/ROADMAP.md`
  - Run `/review`; append any findings as `TASK-FIX-*` tasks and complete them before `/qa`.
  - Map every PRD criterion to real source/compile/runtime evidence, explicitly retain CI-009 as the
    two-run visual-acceptance owner, update shared guidance/roadmap, and run `/validate`.
  - Acceptance: review `RESOLVED`, QA `QA_PASSED`, upstream issue state recorded, roadmap complete,
    pre-commit green, and final workflow validation has no drift.
  - Tests: documentation/report task, so no focused code test under the user's instruction; the
    mandatory pre-commit workflow gate still runs before commit.
  - Result: review resolved one cross-repository drift through TASK-FIX-1; QA maps all criteria to
    public-source, JVM, compiled instrumentation, or full-gate evidence and records the device/live
    proxy limitations. Shared architecture and roadmap now identify the journey contract and leave
    final two-run visual acceptance to CI-009.

## Review fixes

- [x] TASK-FIX-1: reconcile the canonical manifest with the completed Bloom deployment
  - Source: `/review finding 1`
  - Modified: `spec/fixtures/valid/bloom-flowers.json`,
    `spec/fixtures/valid/bloom-flowers.expected.json`, `spec/SPEC.md`,
    `siteskin-core/src/test/kotlin/dev/siteskin/core/nav/ReferenceIntegrationNavTest.kt`,
    `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`,
    `app/src/androidTest/java/app/webora/browser/visual/LiveSiteScreenshotTest.kt`,
    `docs/WALKTHROUGH.md`, `docs/INSTALL.md`, `reports/review/DEMO-005.md`
  - Adopt the completed BLOOM-001/002 manifest vocabulary—Catalog/grid_view and Profile/profile—
    across the canonical fixture, normalized result, spec example, conformance tests, live journey,
    and reviewer documentation.
  - Acceptance: the checked-in fixture is byte-identical to the public Bloom source manifest;
    corpus/navigation/live-journey tests and pre-commit pass; review becomes `RESOLVED`.
  - Negative control: the existing byte-copy corpus assertion and explicit expected vocabulary fail
    on either repository drifting back to Flowers/account independently.
  - Result: Webora's fixture is byte-identical to the completed public Bloom manifest; Catalog,
    grid_view, and Profile are reflected in the normalized corpus, specification, route semantics,
    live journey, and user guidance. Focused core/app tests and instrumentation compilation pass.
