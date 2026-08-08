# CORE-003: Research
Status: RESEARCH_READY

## Question

How can core execute the v1.0 version and structural contract now, without pulling CORE-002 parsing
or CORE-004 security decisions into this ticket and without putting a general JSON Schema engine on
the production classpath?

## Current state and prerequisite gap

`:siteskin-core` currently exposes constants, limits, and `ManifestSource`; it has no DTO, parser,
validator, diagnostic model, or trusted configuration. The shared corpus harness validates fixtures
with `io.github.optimumcode` only in tests. `CORE-002` remains unimplemented even though the backlog
orders it before this ticket. The narrow seam available today is `kotlinx.serialization.json.JsonElement`,
already a production dependency: CORE-003 can validate parsed JSON while leaving byte acquisition,
size guarding, parse errors, and DTO mapping to CORE-002.

## Origins involved

- The serving origin is **not an input** to schema validation. Schema rules are document-local.
- Asset/action origins are present as inert strings only; this layer checks their JSON type and
  required presence where applicable, never their destination, scheme, or provenance.
- No origin-bound trusted result is produced. Passing this layer says only that the document has the
  v1 structural shape; CORE-004 must still bind it to the serving origin.

## Manifest-controlled surface

The manifest controls every JSON value inspected here, including nesting depth, collection entries,
version spelling, types, and strings. The validator may accept or reject the whole document with one
of two stable codes. It may not execute values, resolve URLs, load assets, dispatch actions, or
construct browser chrome.

## Browser-owned remainder

- Supported majors remain the `SiteSkinSchema.SUPPORTED_MAJOR` allow-list decision.
- Validation ordering and diagnostic classification come from SPEC §§4.1 and 11, not from manifest
  fields.
- Unknown action/icon values and properties remain acceptable structurally. Their later
  drop/substitute/warn behavior is browser-owned and belongs to CORE-002/CORE-004.
- Security chrome, origin binding, graceful browser fallback, and trusted configuration construction
  remain outside this API.

## Relevant files and findings

| Path | Finding |
|---|---|
| `spec/siteskin-1.0.schema.json` | Small fixed vocabulary: object/array/string, required, minLength, pattern, refs, and action-dependent requirements; no enums, max lengths/items, or `additionalProperties: false` |
| `spec/versions.json` | Machine-readable boundary includes strings, a number, and absence; must drive production validator tests unchanged |
| `spec/diagnostics.json` | Only version/schema codes are reachable in this ticket; both are reject dispositions |
| `spec/fixtures/invalid/version-major-2-alien.json` | Requires version inspection before shape traversal and exactly one version diagnostic |
| `spec/fixtures/invalid/*` | Most “invalid” fixtures are intentionally schema-valid security cases and therefore positive controls for this layer |
| `siteskin-core/build.gradle.kts` | JSON Schema library is intentionally test-only; kotlinx JSON is already production-visible; Android leak check applies |
| `.../spec/SpecCorpus.kt` | Already exposes fixture reachability and version-table rows; can be reused to compare production outcomes |
| `.../spec/SpecCorpusTest.kt` | Published-schema oracle remains useful independently; production tests must not replace it |

## Structural rules to implement

1. If `schemaVersion` is a JSON string matching canonical `MAJOR.MINOR`, inspect its major first.
   Unsupported major rejects immediately with `SS-E-VERSION-UNSUPPORTED`.
2. Otherwise validate the entire v1 structure and collapse any structural failures into the stable
   public code `SS-E-SCHEMA-INVALID`.
3. Validate required top-level/site/navigation/action fields, declared JSON types, non-empty strings,
   identifier/color/icon/action/match patterns, and conditional action payload requirements.
4. Ignore unknown properties and do not impose limits or semantic allow-lists absent from the schema.
5. Return no partially trusted DTO or normalized value.

## Risks and negative controls

- **Ordering regression:** remove the unsupported-major short circuit; alien `2.0` must then fail the
  exact-code corpus test.
- **Security-rule leakage:** turn action type or icon into an enum; unknown-action/icon fixtures must
  fail, proving the positive controls detect the mistake.
- **Grammar drift:** loosen canonical version matching; `spec/versions.json` rows for leading zero,
  whitespace, newline, and extra components must fail.
- **Incomplete recursion:** focused tests should place invalid navigation/action structures inside
  each of the three collections rather than relying on one path.

## Conclusion

Implement a small production `SchemaValidator` over `JsonElement`, with a public diagnostic/result
model and a private structural walker mirroring the published v1 schema. Keep the existing external
schema library test-only as the independent oracle. CORE-002 later supplies parsed values; CORE-004
consumes successful validation and performs the trust-establishing work.

## Question
What the plan needs decided before it can commit to a trust boundary and a file list.

## Origins involved
- serving origin(s)
- asset origin(s), and why they are same-origin

## Manifest-controlled surface
What a website can influence if this ships as scoped.

## Browser-owned remainder
What must stay browser-controlled, and the affordance that enforces it.

## Relevant code
| Path | Why it matters |
|---|---|

## Prior art
ADRs, spec sections, fixtures and tickets that already decided part of this.

## Risks
- risk → the plan's obligation in response

## Open questions
Carried into `/plan` as explicit unknowns, not silently resolved here.
