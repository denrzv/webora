# HARDEN-001: Implementation plan
Status: PLAN_APPROVED

## Overview
Complete the M4 adversarial matrix by adding an explicit pre-parse nesting bound, portable fixtures
for the missing collection/depth cases, and focused transport/origin regression tests for cases
that cannot be represented by JSON alone. Reuse the shared validator and existing diagnostics.

## Flow
- Discovery remains concurrent and exact-origin. A self-loop or multi-node same-origin loop consumes
  the browser-owned redirect allowance and returns `Rejected` without publishing bytes.
- Validation reads only the byte sentinel, checks JSON structural nesting with a finite-state scan,
  decodes strict UTF-8, parses JSON, and continues through version/schema/security in the existing
  normative order. Excessive nesting rejects at parse with `SS-E-PARSE`.
- Normalization retains first occurrences of duplicate ids and the first N valid collection items,
  emitting existing `SS-E-DUPLICATE-ID`/`SS-W-LIMIT-TRUNCATED` diagnostics.
- UI state is unchanged. Any rejected discovery/validation result remains regular browser mode.

## Data
- Untrusted bytes and `JsonElement` remain outside the trust boundary. Only
  `SiteSkinValidator.Accepted.configuration` is trusted.
- Add `MAX_JSON_DEPTH = 64` to the shared immutable limits. Count `{`/`[` outside JSON strings and
  their matching `}`/`]`; reject depth 65 or structurally mismatched/unclosed input as parse failure.
  JSON parsing remains authoritative for the rest of the grammar.
- No cache key or persisted data changes. Accepted configurations remain keyed by canonical full
  origin plus trusted schema version.

## Security
- Origin binding continues to compare canonical scheme, complete ASCII host, and port. Unicode and
  punycode spellings cannot split identity; mixed-script is a browser-owned display signal only.
- The closed URI allow-list remains unchanged. The corpus must individually identify all five M4
  denied schemes so a partial deny-list cannot masquerade as compliance.
- Parser, redirect, duplicate, and limit failures remain bounded and deterministic. Rejection never
  blocks the renderer or grants native capability; it falls back to regular browsing.

## File-by-file plan
### New: `spec/fixtures/invalid/deeply-nested.{json,expected.json}`
Pin first-over-limit nesting as a portable parse rejection.

### New: `spec/fixtures/invalid/{quick-actions,menu}-over-limit.{json,expected.json}`
Pin truncation limits, document order, and stable JSON pointers for the remaining collections.

### New: `spec/fixtures/invalid/duplicate-action-id.{json,expected.json}`
Pin that the duplicate-id contract applies across action collections and remains first-wins.

### Modified: `spec/SPEC.md`
Document the JSON nesting ceiling and clarify that it is enforced before tree construction.

### Modified: `siteskin-core/src/main/kotlin/dev/siteskin/core/SiteSkin.kt`
Expose the browser-owned maximum JSON nesting constant.

### Modified: `siteskin-core/src/main/kotlin/dev/siteskin/core/manifest/ManifestParser.kt`
Add a non-recursive string/escape-aware structural scanner before JSON tree parsing.

### Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/manifest/ManifestParserTest.kt`
Pin accepted/rejected depth, bracket-like string content, malformed structure, and exact byte size.

### Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`
Assert the named attack matrix and collection/duplicate survivor semantics.

### Modified: `app/src/test/java/app/webora/browser/siteskin/OkHttpManifestSourceTest.kt`
Add an explicit same-origin redirect-loop case with bounded requests and rejection.

### Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/origin/SiteOriginTest.kt`
Only if necessary after audit, make the Unicode/punycode homograph matrix explicit; do not alter
origin implementation when existing semantics already satisfy it.

### Modified: `docs/tasklist/HARDEN-001.md`, `reports/review/HARDEN-001.md`, `reports/qa/HARDEN-001.md`
Record task evidence, negative controls, review findings, QA, and validation.

## Tests
- Focused parser and corpus JVM tests after each core/spec code or fixture change.
- Focused OkHttp discovery test after app test changes.
- `./gradlew :siteskin-core:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew detekt`, and
  `bash scripts/pre-commit-check.sh`.
- Negative controls: disable/decrement the depth check, increase redirect allowance for the loop,
  reverse duplicate retention, and raise collection limits; confirm the named tests fail, then
  restore protections.

## Rollout / versioning
This is a security narrowing under SPEC §4.3, expressed with the existing `SS-E-PARSE` diagnostic.
The depth ceiling exceeds all v1 schema shapes and is documented for third-party implementations.
No schema version, storage migration, feature flag, or UI rollout is required.

## Open questions
None. Research resolves the missing root roadmap by treating `docs/DEVELOPMENT_PLAN.md` M4 as the
authoritative source and selects 64 as the conservative nesting ceiling.
