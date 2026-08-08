# Review: CORE-003
Date: 2026-08-08
Status: RESOLVED

## Summary

The implementation preserves the intended `version → schema → security` boundary and corpus-driven
behavior. One API-evolution issue must be resolved before QA: the public diagnostic enum exposes only
the two CORE-003 codes even though the public result already promises errors and warnings from the
manifest validation pipeline.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — production uses only kotlinx JSON and remains Android-free |
| Trust boundary | PASS — success returns no DTO, normalization, origin, or trusted configuration |
| Layer ordering | PASS — canonical unsupported majors short-circuit before v1 structure |
| API evolution | FINDING-1 — public code type cannot represent already-published later-layer codes |

## Security

| Property | Assessment |
|---|---|
| Unknown-major rejection | PASS, including alien-shape negative control |
| Forward-compatible fields/actions/icons | PASS; no security allow-list leaked into schema |
| Stable diagnostics | PASS for current layer; FINDING-1 affects future-layer composition |
| Hostile shape handling | PASS — type checks return one stable schema diagnostic without exceptions |

## Findings

### FINDING-1 · Medium · public diagnostic contract is closed too early
**File:** `siteskin-core/src/main/kotlin/dev/siteskin/core/ManifestValidation.kt:9`

`DiagnosticCode` is a public enum containing only `VERSION_UNSUPPORTED` and `SCHEMA_INVALID`.
`ManifestValidationResult` already has both errors and warnings and is intended to cross validation
layers, but CORE-004 cannot represent `SS-E-ORIGIN-MISMATCH`, `SS-W-CONTRAST-CORRECTED`, or any other
already-published registry code without adding enum entries and forcing consumers to revisit
exhaustive `when` expressions. The plan also calls this type extensible, which a two-entry enum is not.

Fix: include every currently registered diagnostic code now, while retaining the stable wire value.
Add a test that pins uniqueness and completeness against `spec/diagnostics.json` so the public API and
contract cannot drift.

## Not findings

- The production validator mirrors schema rules instead of loading JSON Schema at runtime. This is
  deliberate: the general engine remains a test-only independent oracle and avoids runtime/dex cost.
- `SchemaValidator` accepts `JsonElement` rather than a DTO. CORE-002 is a missing prerequisite and
  owns byte parsing/DTO mapping; the seam prevents this ticket from absorbing that scope.
- Schema validation returns no unknown-field warning. The registry places that diagnostic in the
  security layer, and unknown properties must remain structurally accepted for minor compatibility.
- Regexes use Kotlin `matches`, not JSON Schema `pattern` find semantics. Every current schema pattern
  is explicitly start/end anchored, and production version decisions are tested against all boundary
  spellings, including the newline landmine.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `SchemaValidatorTest.kt` | 5 | ordering, malformed versions, forward compatibility, collections, conditional actions |
| `ProductionSchemaConformanceTest.kt` | 2 | every version-table row and every parsable document fixture |
| `SpecCorpusTest.kt` | existing suite | independent published-schema and corpus integrity oracle |

## Verdict

RESOLVED — TASK-FIX-1 makes the public code enum registry-complete and pins it to the registry in
both directions.
