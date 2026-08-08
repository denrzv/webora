# CORE-003: Implementation plan
Status: PLAN_APPROVED

## Flow

```
parsed JsonElement
  → inspect canonical schemaVersion if possible
  → unsupported major? reject SS-E-VERSION-UNSUPPORTED (stop)
  → validate v1 structural shape
  → any violation? reject SS-E-SCHEMA-INVALID
  → otherwise accept with empty errors/warnings
```

The API consumes an already parsed JSON tree because CORE-002 owns bytes, size limiting, parse
diagnostics, unknown-field discovery, and DTO mapping. The result is validation evidence only; it
does not expose a trusted or normalized manifest.

## Trust and origin boundary

All JSON remains website-controlled untrusted input before and after this validator. No URL is
resolved and no origin is accepted as an argument, so schema success cannot be confused with origin
binding. The browser continues to own supported-version policy, security allow-lists, security
chrome, normalization, and graceful fallback. Unknown fields, action types, and icon names are
structurally allowed because later browser-owned policy decides how they degrade. The validator
returns only diagnostics—not capabilities or executable actions.

## API design

- `DiagnosticCode` is a constrained public value type with constants for the two codes this layer
  can emit, while remaining extensible for later registry codes.
- `ManifestDiagnostic` carries a code. Pointer/detail are intentionally deferred until consumers
  require them; stable acceptance is defined in terms of codes.
- `ManifestValidationResult` carries immutable `errors` and `warnings` plus `isValid`.
- `SchemaValidator.validate(JsonElement)` is stateless and deterministic.

Warnings are empty for CORE-003. `SS-W-FIELD-UNKNOWN` is registered at the security layer and its
discovery depends on CORE-002 preserving unknown DTO fields. The shape leaves room for later layers
to return warnings without changing the result contract.

## Structural implementation

Mirror the current published schema directly with small helpers for object/array/string checks,
required fields, anchored Kotlin regexes, navigation collections, and conditional action members.
Do not read the schema file at runtime or add the test JSON Schema engine to production. Centralize
failure to one schema-invalid diagnostic; callers must not depend on engine-specific error text.

The version check recognizes only the exact canonical grammar before extracting a major. A malformed,
missing, or non-string version deliberately falls through to structural validation and becomes
`SS-E-SCHEMA-INVALID`. A canonical unsupported major returns immediately before any other field is
read, satisfying the alien-document case.

## File-by-file changes

| Path | Change |
|---|---|
| `siteskin-core/src/main/kotlin/dev/siteskin/core/ManifestValidation.kt` | Public diagnostic/result contract and `SchemaValidator` structural implementation |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/SchemaValidatorTest.kt` | Focused API, ordering, nested-shape, and boundary tests |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/ProductionSchemaConformanceTest.kt` | Execute version table and reachable fixture expectations against production code |
| `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpus.kt` | Expose fixture JSON parsing/test labels needed by production conformance tests |
| `docs/tasklist/CORE-003.md` | Record task results and mandatory negative controls |
| `docs/BACKLOG.md`, `docs/DEVELOPMENT_PLAN.md`, `CLAUDE.md` | Close ticket and document the new validator seam after implementation/review/QA |

## Tests

- All `spec/versions.json` decisions match the production validator exactly.
- Every parsable corpus document produces exactly the expected subset of version/schema diagnostics;
  pre-parse/transport fixtures are excluded because this API cannot receive them meaningfully.
- Structurally valid security fixtures pass, including unknown action, icon, and properties.
- Focused tests cover top-level/site types and requirements, patterns, all navigation collections,
  navigation/action requirements, conditional payload requirements, and forward minor acceptance.
- Negative controls prove unsupported-major short-circuiting and non-enumeration of action types.
- `:siteskin-core:check`, Detekt, and the full pre-commit gate enforce module and project constraints.

## Security and failure behavior

No exceptions from hostile shapes should escape normal validation. The input is already materialized,
so transport-size protection is a prerequisite owned by CORE-002; this validator neither promises nor
simulates it. Invalid results contain one stable rejection code and allow the caller to fall back to
regular browser mode without partial application.

## Rollout / versioning

This is the first production schema-validation API. There is no migration or persisted state.

## Open questions

None. Research resolved the runtime-library and CORE-002 boundary choices.
