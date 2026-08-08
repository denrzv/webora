# CORE-003: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/CORE-003.prd.md`
- Research: `docs/research/CORE-003.md`
- Plan: `docs/plan/CORE-003.md`

## Tasks

- [x] **TASK-1: production schema validation and conformance coverage**
  - New: `siteskin-core/src/main/kotlin/dev/siteskin/core/ManifestValidation.kt`
  - New: focused and corpus-driven production validator tests under `siteskin-core/src/test/`.
  - Modified: `SpecCorpus.kt` only as needed to expose parsed fixture input.
  - Acceptance: all PRD validation criteria are implemented; version rejection short-circuits
    structural validation; security-layer cases remain structurally accepted; results expose stable
    errors/warnings without a trusted manifest.
  - Tests: `./gradlew :siteskin-core:test`, `./gradlew :siteskin-core:check`, focused version-table
    and fixture conformance tests, and `bash scripts/pre-commit-check.sh`.
  - Negative controls: disable unsupported-major short-circuiting and confirm the alien `2.0`
    fixture fails; restrict action types and confirm the unknown-action fixture fails. Restore both.
  - **Result:** Added the public result/diagnostic API and a direct v1 structural validator over
    `JsonElement`. Added focused nesting/conditional tests plus production execution of all version
    decisions and every parsable document fixture. `:siteskin-core:test` passes. Both negative
    controls failed `productionValidatorMatchesReachableCorpusDiagnosticsExactly` as required and
    were restored: disabling the major short circuit exposes the alien `2.0` shape, while treating
    action types as an enum rejects the forward-compatible unknown-action fixture.

- [x] **TASK-2: closeout documentation**
  - Modified: `docs/BACKLOG.md`, `docs/DEVELOPMENT_PLAN.md`, and `CLAUDE.md`.
  - Acceptance: CORE-003 is marked complete, its delivered API/prerequisite seam is accurately
    documented, and no prose implies schema validation establishes trust or performs parsing.
  - Tests: documentation/status checks and `bash scripts/pre-commit-check.sh`.
  - **Result:** Marked the backlog and milestone row complete and documented the parsed-JSON seam,
    ordering guarantee, untrusted result, and test-only schema-engine boundary in shared architecture
    context. Parsing/DTO and security ownership remain explicit rather than being implied delivered.

## Deferred

- Bytes, size guard, JSON parsing/`SS-E-PARSE`, unknown-field discovery, and DTO mapping remain
  `CORE-002`; this ticket accepts already parsed `JsonElement` because that prerequisite is absent.
- Security diagnostics and trusted normalization remain `CORE-004`.

## Review fixes

- [x] **TASK-FIX-1: make the diagnostic contract registry-complete**
  - Source: `/review` FINDING-1.
  - Modified: `ManifestValidation.kt`, `ProductionSchemaConformanceTest.kt`.
  - Acceptance: `DiagnosticCode` represents every currently registered stable code with a unique
    wire value, and a corpus-driven test enforces completeness in both directions.
  - Tests: focused registry-completeness test and `bash scripts/pre-commit-check.sh`.
  - **Result:** Added all thirteen currently registered codes to the public enum and a two-way,
    uniqueness-checked comparison with `spec/diagnostics.json`. Later validation layers can now use
    the result contract without widening a closed public enum immediately after release.
