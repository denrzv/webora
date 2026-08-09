# SPEC-003: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/SPEC-003.prd.md`
- Plan: `docs/plan/SPEC-003.md`

## Tasks

- [x] TASK-1: Add the core-owned total validation pipeline
  - Modify: `siteskin-core/src/main/kotlin/dev/siteskin/core/manifest/ManifestParser.kt`
  - New: `siteskin-core/src/main/kotlin/dev/siteskin/core/SiteSkinValidator.kt`
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/SiteSkinValidatorTest.kt`
  - Modify/new: production conformance coverage under
    `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/`
  - Acceptance: one public core entry point consumes a caller-owned stream once, executes bounded
    parse then version/schema then security in normative order, and returns either a trusted
    configuration plus diagnostics or rejection plus diagnostics. Well-formed wrong-shaped JSON is
    schema-invalid rather than parse-invalid. Every corpus fixture has the expected activation and
    diagnostic outcome, and existing parser APIs/tests remain compatible.
  - Tests: `SiteSkinValidatorTest`, production corpus test, `:siteskin-core:test`,
    `:siteskin-core:check`, and `bash scripts/pre-commit-check.sh`.
  - Negative control: force DTO decoding ahead of structural validation (or bypass schema
    short-circuit), confirm the structural/layer-order test fails, then restore.
  - Negative control result: temporarily bypassing the schema rejection made
    `SiteSkinValidatorTest.well formed wrong shape is a schema rejection rather than parse
    rejection` fail with `ClassCastException` (exit 1); the short-circuit was restored.
  - Deviation: conformance asserts exact diagnostic code order and activation for every fixture,
    but not every pointer. Existing `SchemaValidator` intentionally emits one document-level
    structural diagnostic without a detailed pointer; pointer-rich schema reporting is not needed
    for the ticket's stable-code CLI contract and was not duplicated in the orchestrator.
  - Status: complete

## Post-review fixes

- [x] TASK-FIX-1: Reject explicitly contradictory response media types
  - Source: `/review finding 1`
  - Modify: `siteskin-lint/src/main/kotlin/dev/siteskin/lint/ManifestDiscovery.kt`
  - Modify: `siteskin-lint/src/test/kotlin/dev/siteskin/lint/ManifestDiscoveryTest.kt`
  - Acceptance: successful responses with no `Content-Type`, `application/json`, or a JSON media
    type with parameters reach core; an explicitly non-JSON media type fails before body validation
    and cannot produce exit 0.
  - Tests: focused discovery tests, `:siteskin-lint:test`, and `bash scripts/pre-commit-check.sh`.
  - Negative control: bypass the media-type check, confirm the contradictory-type test fails, then
    restore.
  - Negative control result: bypassing the media-type guard made the focused discovery test fail
    when it attempted to cast the incorrectly validated `text/html` response to a failure (exit 1);
    the guard was restored.
  - Status: complete

- [x] TASK-2: Implement bounded exact-origin discovery and the CLI command
  - Replace: `siteskin-lint/src/main/kotlin/dev/siteskin/lint/Main.kt`
  - New: `siteskin-lint/src/main/kotlin/dev/siteskin/lint/ManifestDiscovery.kt`
  - New: `siteskin-lint/src/test/kotlin/dev/siteskin/lint/CommandTest.kt`
  - New: `siteskin-lint/src/test/kotlin/dev/siteskin/lint/ManifestDiscoveryTest.kt`
  - Modify: `siteskin-lint/build.gradle.kts`
  - Acceptance: the public command accepts exactly one HTTPS origin, requests the well-known path,
    follows at most two exact-origin redirects, uses bounded timeouts/streaming, calls only the core
    total validator for policy, prints ordered stable diagnostic codes, exits 0 only for a trusted
    result, and returns concise non-zero operational failures without hostile body/stack output.
  - Tests: command argument/result tests, MockWebServer path/status/redirect/timeout tests,
    `:siteskin-lint:test`, `:siteskin-lint:installDist`, and `bash scripts/pre-commit-check.sh`.
  - Negative control: temporarily remove redirect origin comparison, confirm the cross-origin
    redirect test fails, then restore.
  - Negative control result: returning the redirect target without comparing `SiteOrigin` made
    `ManifestDiscoveryTest.refuses redirect to a distinct origin` fail because the second origin
    received a request (exit 1); exact-origin comparison was restored.
  - Deviation: MockWebServer transport tests call the internal loader over loopback HTTP and thus
    correctly receive a core rejection at the HTTPS security boundary; command tests independently
    prove the public argument accepts HTTPS only. This avoids installing a test CA while preserving
    both boundaries.
  - Status: complete

- [x] TASK-3: Prove the packaged CLI against the complete corpus
  - New: `siteskin-lint/src/test/kotlin/dev/siteskin/lint/CorpusCliTest.kt`
  - Modify: `siteskin-lint/build.gradle.kts` if corpus test inputs need wiring
  - Acceptance: every fixture body is served through the CLI runner, expected stable codes are
    printed, fixtures with canonical results exit 0, rejecting fixtures exit non-zero, and Bloom
    Flowers is an explicit positive control. `installDist` produces an executable named
    `siteskin-lint` whose usage and validation behavior are smoke-tested.
  - Tests: `CorpusCliTest`, installed-distribution smoke test, `:siteskin-lint:test`, full Gradle
    tests/detekt, and `bash scripts/pre-commit-check.sh`.
  - Negative control: temporarily map any diagnostic to failure, confirm a warning/drop-item corpus
    case fails its expected-zero assertion, then restore disposition-based activation.
  - Negative control result: making exit 0 require both a trusted configuration and an empty
    diagnostic list made `CorpusCliTest.command result and codes match every conformance fixture`
    fail on an activatable diagnostic fixture (exit 1); trusted-result exit semantics were restored.
  - Distribution smoke: `:siteskin-lint:installDist` generated
    `build/install/siteskin-lint/bin/siteskin-lint`; invoking it without arguments printed the
    documented usage and exited 2.
  - Deviation: corpus bodies are injected at the command's transport seam rather than exposed over
    public loopback HTTP. This lets each body retain the HTTPS serving origin declared by its
    expectation while MockWebServer separately proves the real well-known HTTP request behavior.
  - Status: complete
