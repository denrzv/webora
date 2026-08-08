# Review: CORE-002
Date: 2026-08-08
Status: RESOLVED

## Summary
The DTO hierarchy mirrors the complete v1 schema without introducing a trusted configuration type. The parser enforces the byte ceiling before JSON work, uses strict UTF-8, reports unknown fields, and preserves the pure-JVM boundary. One medium-severity failure-containment gap requires a fix.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — only JDK and existing kotlinx.serialization APIs are used. |
| Trust boundary | PASS — result names and types remain explicitly DTO/untrusted; no origin or activation decision occurs. |
| Validation order | PASS WITH NOTE — size precedes parse; schema/security remain out of scope. |
| Public API | PASS — documented sealed result prevents exception-based normal control flow for decoded content. |

## Security

| Property | Assessment |
|---|---|
| Byte limit | PASS — reads at most limit + 1 and negative control proves the guard is observable. |
| Malformed encoding | PASS — strict decoder rejects replacement-character normalization. |
| Unknown fields | PASS — ignored with stable path-bearing warnings, including array occurrences. |
| Failure containment | FINDING — stream read failures can escape the total parser boundary. |
| Sensitive logging | PASS — no body or URL logging exists. |

## Findings

### FINDING-1 · Medium · graceful-failure boundary
**File:** `siteskin-core/src/main/kotlin/dev/siteskin/core/manifest/ManifestParser.kt`

Current: `parse` calls `readBounded(input)` outside its exception conversion. An untrusted/network-backed stream may throw `IOException`, which escapes instead of returning regular-mode-compatible rejection. This conflicts with PRD criterion 6 and ADR-010's total-boundary rule.

Fix: catch `IOException` only around the bounded read and map it to `SS-E-PARSE` (the only in-scope non-size failure diagnostic), with a throwing-stream test. Do not catch programming exceptions or wrap imports in try/catch.

## Not findings
- Nullable required DTO fields are intentional: schema validity belongs to CORE-003, and making deserialization itself enforce presence would collapse parse and schema layers.
- `ignoreUnknownKeys = true` is not silent here; the pre-decode shape scan emits the protocol warning first.
- The parser does not close its input because stream ownership stays with the caller; this is asserted.
- Unknown subtrees produce one warning at the unknown root rather than recursively warning about fields whose contract is unknowable.
- The DTO is a data class with `copy()`, unlike the future trusted configuration. DTO mutation does not bypass trust because a DTO grants none.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `SiteSkinManifestDtoTest.kt` | 3 | complete field map, missing values, no trusted-type exposure |
| `ManifestParserTest.kt` | 8 | limits, malformed input, UTF-8, ownership, valid corpus, unknown paths |

## Verdict
RESOLVED — TASK-FIX-1 contains `IOException` at the bounded-read boundary and its regression test passed. The full pre-commit gate is green.
