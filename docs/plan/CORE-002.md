# Plan: CORE-002 — DTOs and parsing
Status: PLAN_APPROVED

## Flow
`InputStream → bounded read (131,072) → strict UTF-8 decode → JSON element parse → unknown-field path scan → generated DTO decode → Parsed(dto, warnings)`.

Size rejection short-circuits every JSON operation. Syntax/UTF-8/typed-decode failure becomes `Rejected(SS-E-PARSE)`. The result hierarchy is total and carries no trusted domain configuration.

## Trust boundary and origin implications
The parser accepts bytes from any origin and deliberately does not accept a `SiteOrigin`; therefore it cannot bind `homeUrl`, `logoUrl`, or action URLs. All DTO strings remain website-controlled. Browser-controlled size, parsing disposition, and diagnostic identity are fixed in code. Domain/TLS chrome and every capability decision remain absent from the DTO model and cannot be configured by it.

## API and data
- Public `@Serializable` DTO data classes mirror the published property names and use nullable/default-empty members so parsing does not masquerade as schema validation.
- Public sealed `ManifestParseResult` has `Parsed(manifest, warnings)` and `Rejected(error)`. `ManifestDiagnostic` exposes stable code/path; warnings use `SS-W-FIELD-UNKNOWN` and rejections use the two in-scope error codes.
- Public `ManifestParser.parse(InputStream)` owns and enforces the byte cap but does not close the caller-owned stream.
- Unknown-key walking uses an internal field-shape table and JSON Pointer-like paths. Array indices identify individual occurrences. Unknown subtrees generate one warning at their root, because descendants are not meaningful without a known enclosing contract.

## Security and failure behavior
- Read chunks are sized so at most the limit plus one byte is consumed, proving early termination.
- Strict UTF-8 decoding rejects malformed/unmappable bytes.
- Parser exceptions are contained at the untrusted boundary and mapped to a stable result; no manifest bytes appear in diagnostics or logs.
- DTOs are explicitly named `Dto`, remain ordinary untrusted data, and cannot construct the future trusted configuration.

## File-by-file
- New `siteskin-core/src/main/kotlin/dev/siteskin/core/manifest/SiteSkinManifestDto.kt`: schema-shaped DTO hierarchy.
- New `siteskin-core/src/main/kotlin/dev/siteskin/core/manifest/ManifestParser.kt`: result/diagnostic types, bounded read, strict decode, unknown-field scan, DTO decode.
- New `siteskin-core/src/test/kotlin/dev/siteskin/core/manifest/SiteSkinManifestDtoTest.kt`: complete field mapping and inert-type API checks.
- New `siteskin-core/src/test/kotlin/dev/siteskin/core/manifest/ManifestParserTest.kt`: valid corpus, malformed/oversized/UTF-8, warning paths, early-read and optional-field behavior.
- Update `CLAUDE.md`: record strict UTF-8, bounded stream, and unknown-field warning convention if confirmed by implementation.

## Tests
- Decode the full Bloom fixture and every valid parsing fixture.
- Parse minimal/missing optional fields and an unknown future field while preserving known siblings.
- Assert nested and array unknown paths and one warning per occurrence.
- Assert malformed, empty, truncated, wrong-shaped, and invalid UTF-8 rejection without exceptions.
- Use a counting/generating stream to prove an oversized body reads exactly 131,073 bytes and cannot reach decoding.
- Run core tests, core check, Detekt, and the complete pre-commit gate.
