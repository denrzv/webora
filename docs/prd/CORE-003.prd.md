# CORE-003: Schema validation
Status: PRD_READY

## Context / Problem

The published SiteSkin contract already defines validation order and stable diagnostic codes, but
`:siteskin-core` does not execute that contract. A browser implementation therefore has no runtime
way to distinguish a supported, structurally valid manifest from an unsupported version or malformed
document. Parsing success cannot serve as validation because manifests are untrusted remote input.

`CORE-002` has not yet supplied DTOs or production parsing APIs. This ticket must not silently absorb
that transport/parse scope: it owns the version and structural-schema decisions over an already
parsed JSON value, leaving byte limits, JSON decoding diagnostics, unknown-field warnings, and
security normalization to their designated layers.
## Goals

- Add a pure-JVM schema validator that accepts an already parsed JSON value.
- Return a typed `ManifestValidationResult(errors, warnings)` using the stable diagnostic codes.
- Enforce the SPEC-002 ordering: supported-major policy before structural validation.
- Execute the document corpus and `spec/versions.json` against production validation code.
- Preserve the boundary between structural rejection and later security-layer diagnostics.
## Non-goals

- Byte-size guarding, JSON parsing, DTO definitions, or unknown-field discovery (`CORE-002`).
- Origin binding, scheme/action/icon allow-lists, limits, contrast, or normalization (`CORE-004`).
- Constructing trusted `SiteSkinConfiguration` values.
- Android integration or user-facing diagnostics.
## User stories

- As a browser implementer, I can reject unsupported major versions before interpreting their shape.
- As a site owner, I receive the stable schema/version diagnostic prescribed by the specification.
- As a maintainer, I can prove the runtime validator stays aligned with the shared conformance corpus.
## Acceptance criteria

1. A production `SchemaValidator` in `:siteskin-core` validates already parsed JSON without Android
   or network dependencies and returns `ManifestValidationResult(errors, warnings)`.
2. A present, well-formed version with a major outside the supported allow-list returns exactly
   `SS-E-VERSION-UNSUPPORTED`, without structural diagnostics, including the structurally alien
   `2.0` fixture.
3. An absent, non-string, or malformed `schemaVersion`, missing required field, wrong type, or
   malformed constrained value returns exactly `SS-E-SCHEMA-INVALID`.
4. Every structurally valid `1.x` document passes schema validation, including forward-compatible
   unknown fields; schema validation emits no security-layer warnings or errors.
5. Production-code tests execute every entry in `spec/versions.json` and every parsing document in
   `spec/fixtures/invalid/`, asserting exactly the version/schema codes reachable at this layer and
   no more or fewer.
6. Security-layer fixtures that are structurally valid remain accepted by `SchemaValidator`, proving
   origin, scheme, action, icon, limit, contrast, and unknown-field decisions did not leak into it.
7. `:siteskin-core` remains Android-free and trusted configuration types remain out of scope.
8. `bash scripts/pre-commit-check.sh` passes.
## NFR
- Security/privacy:
  Unsupported formats short-circuit before structural interpretation; schema success never creates
  a trusted domain object.
- Reliability/fallback:
  Validation is deterministic and reports stable codes; callers retain responsibility for graceful
  regular-browser fallback.
- Performance:
  Version inspection is constant work before bounded structural traversal; no network or blocking I/O.
- Accessibility:
  Not engaged; this ticket has no UI surface.
## Risks

- Reimplementing JSON Schema incompletely can drift from the published schema. Mitigation: implement
  only the schema's current structural vocabulary and drive it with every corpus document plus
  focused keyword tests.
- Treating security allow-lists as schema enums would reject forward-compatible documents.
  Mitigation: explicit tests require unknown actions, icons, and fields to pass this layer.
- CORE-002's absence could encourage a parser API here. Mitigation: accept `JsonElement` and record
  byte parsing as a prerequisite/deferred ticket rather than expanding scope.
## Open questions

- Should production depend on the JVM JSON Schema library currently used by tests? **No.** It is a
  test-only contract oracle and would enlarge the dex/runtime surface. The core validator implements
  the small, fixed v1.0 structural contract directly and corpus tests compare its decisions with the
  published artifact.
- Should unknown fields produce `SS-W-FIELD-UNKNOWN` here? **No.** The registry assigns that code to
  the security layer and CORE-002 owns preservation/discovery during DTO parsing. Schema permits
  unknown properties so minor versions remain additive.
