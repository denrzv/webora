# SPEC-003: Implementation plan
Status: PLAN_APPROVED

References: `docs/prd/SPEC-003.prd.md`, `docs/research/SPEC-003.md`, `docs/BACKLOG.md`,
`docs/DEVELOPMENT_PLAN.md`, `spec/SPEC.md` §§2–4 and 10–14, and `docs/adr/README.md`
ADR-002/ADR-003/ADR-004/ADR-010.

## Overview

First add one core-owned total validation pipeline that preserves the raw JSON through normative
layer ordering and returns either a trusted configuration or rejection plus one diagnostic stream.
Then replace the CLI placeholder with a testable command runner and an OkHttp discovery source that
enforces HTTPS, exact-origin redirects, hop/time/size bounds, and feeds response streams directly to
that pipeline. Finally drive the command over the shared corpus and package it with Gradle's
application distribution.

## Flow

```text
user argument
  → parse/canonicalize exact HTTPS SiteOrigin (CLI-owned input contract)
  → GET {origin}/.well-known/siteskin.json
  → manually follow ≤2 redirects only when canonical origin is unchanged
  → stream response to core
      → bounded bytes + strict UTF-8 + raw JsonElement
      → version → schema (reject short-circuits)
      → DTO decode/unknown-field diagnostics
      → security normalization against requested serving origin
      → trusted SiteSkinConfiguration or rejection + ordered diagnostics
  → render stable codes/messages
  → exit 0 iff trusted configuration exists; otherwise non-zero
```

## Origin boundary and website-controlled contract

- **Website-controlled surface:** response bytes may request only declarative, bounded SiteSkin
  branding/navigation/action values. Redirect locations, URLs, action types, colors, identifiers,
  and unknown fields remain hostile until core constructs the trusted configuration.
- **Tool/browser-owned contract:** the caller chooses one HTTPS origin; the tool fixes the discovery
  path, timeouts, redirect limit, content handling, output, and exit semantics. Core fixes validation
  order, dispositions, allow-lists, normalization, and trusted construction.
- **Exact origin:** canonical scheme, host, and effective port must match on every redirect. A
  subdomain, parent, sibling, cleartext downgrade, userinfo authority, or port change aborts.
- **Binding:** security validation always receives the canonical originally requested origin, not a
  response-provided value. Same-origin path redirects do not create a new authority.
- **HTTPS:** public argument parsing accepts HTTPS only. Tests use injected sources/runners for
  loopback HTTP and cannot relax the public contract.
- **Failure:** transport/usage failures remain CLI errors rather than fabricated protocol
  diagnostics. Untrusted body text and exception internals are never printed.

## Data and result contract

Introduce a core validation result sealed by trust: an accepted branch carries the only
validator-constructed `SiteSkinConfiguration` and all warnings/drop-item diagnostics; a rejected
branch carries ordered diagnostics and no configuration. Core reads each caller-owned stream once
and does not close it.

Parsing must distinguish malformed JSON/UTF-8 (`SS-E-PARSE`) from well-formed structurally invalid
JSON (`SS-E-SCHEMA-INVALID`). Raw `JsonElement` therefore survives until schema validation. DTO
decoding occurs only after the schema stage and unknown-field scanning remains parse-layer output.
Existing public parser APIs remain source-compatible where practical, but the new pipeline—not a
CLI assembly of individual validators—is the integration seam for browser and tool.

The process exits 0 when this result carries a trusted configuration, even if diagnostics dropped
unsafe items or corrected/truncated values. It exits non-zero only when no trusted configuration is
produced or an operational error occurs. This follows registry dispositions and canonical corpus
results rather than the `valid/` versus `invalid/` directory name.

## File-by-file plan

### Core pipeline

- **Modify `siteskin-core/src/main/kotlin/dev/siteskin/core/manifest/ManifestParser.kt`:** factor the
  bounded strict JSON read so the integrated validator can retain raw JSON, scan unknown fields,
  and decode DTOs after structural validation without parsing twice.
- **New `siteskin-core/src/main/kotlin/dev/siteskin/core/SiteSkinValidator.kt`:** public total
  pipeline and accepted/rejected result hierarchy; map parser diagnostics into the stable core
  diagnostic model, execute stages in normative order, merge diagnostics, and expose trusted
  configuration only on success.
- **Modify `siteskin-core/src/main/kotlin/dev/siteskin/core/ManifestValidation.kt` only if needed:**
  centralize diagnostic conversion/ordering without changing wire codes or validator policy.
- **New `siteskin-core/src/test/kotlin/dev/siteskin/core/SiteSkinValidatorTest.kt`:** focused
  short-circuit, malformed/structural distinction, origin, warning/drop, stream ownership, and
  trusted-result tests.
- **New or modify a core corpus test under `.../spec/`:** execute every fixture through the total
  production path and compare activation, diagnostic code/pointer order, and canonical outcome.

### CLI command and transport

- **Replace `siteskin-lint/src/main/kotlin/dev/siteskin/lint/Main.kt`:** keep `main` thin; add an
  injectable command runner for args/output/source, deterministic usage and operational statuses,
  stable diagnostic rendering, and no stack traces in ordinary failures.
- **New `siteskin-lint/src/main/kotlin/dev/siteskin/lint/ManifestDiscovery.kt`:** OkHttp fetcher with
  explicit timeouts and automatic redirects disabled; canonical origin validation and at most two
  exact-origin redirects; response body remains streamed and caller-scoped.
- **Modify `siteskin-lint/build.gradle.kts`:** align JVM target with the dex-compatible core target,
  declare corpus inputs for tests, and retain the application distribution named `siteskin-lint`.
- **New `siteskin-lint/src/test/kotlin/dev/siteskin/lint/CommandTest.kt`:** argument/usage,
  rendering, accepted-with-warning, rejection, and operational failure tests without process exit.
- **New `siteskin-lint/src/test/kotlin/dev/siteskin/lint/ManifestDiscoveryTest.kt`:** MockWebServer
  coverage for the well-known path, statuses, same-origin redirects, origin escape, hop cap,
  timeout, and response lifetime/size behavior.
- **New `siteskin-lint/src/test/kotlin/dev/siteskin/lint/CorpusCliTest.kt`:** serve every fixture
  locally through an injected source and assert status plus expected codes, including the Bloom
  Flowers positive control and warning/drop-item activations.

### Workflow and shared documentation

- **Modify `docs/tasklist/SPEC-003.md`:** record each checkpoint, commands, negative controls, and
  deviations.
- **Modify `reports/review/SPEC-003.md`, `reports/qa/SPEC-003.md`, and relevant project docs:** record
  review/QA/validation and the stable CLI/pipeline seam after implementation.
- No schema, diagnostic registry, or fixture semantics change is planned. Any discovered contract
  defect stops implementation and becomes an explicit task deviation rather than silently editing
  expected results.

## Tests and negative controls

- Core focused tests plus all corpus documents through `SiteSkinValidator`.
- Temporarily bypass schema short-circuit/force DTO-first decoding; confirm a structural fixture
  reports the wrong code and the focused/corpus test fails, then restore.
- Temporarily skip exact-origin redirect comparison; confirm cross-origin redirect test fails, then
  restore.
- CLI command tests must prove warning/drop diagnostics still exit 0 and rejection exits non-zero.
- `./gradlew :siteskin-core:test`, `./gradlew :siteskin-core:check`,
  `./gradlew :siteskin-lint:test`, `./gradlew :siteskin-lint:installDist`, and
  `bash scripts/pre-commit-check.sh` at the relevant checkpoints.

## Rollout / compatibility

This implements the already-published SiteSkin 1.x contract and adds no protocol version, storage,
or Android migration. Gradle `installDist`/`distZip` is the initial distribution surface. The app
can adopt the core pipeline in a later networking ticket without depending on OkHttp from core.

## Open questions

None. Operational errors remain outside `SS-*`; HTTPS is public-only with injected test transport;
registry disposition controls activation; and Gradle application scripts are the scoped package.

## Overview
## Flow
- discovery
- validation
- normalization
- UI state
## Data
- trust boundary (DTO vs domain model)
- storage / cache keys
## Security
- origin binding
- allow-lists
- fallback on failure
## File-by-file plan
### New: <path>
### Modified: <path>
## Tests
## Rollout / versioning
## Open questions
