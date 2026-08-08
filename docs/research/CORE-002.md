# Research: CORE-002 — DTOs and parsing
Status: RESEARCH_READY

## Existing contract and code
- `spec/siteskin-1.0.schema.json` is the structural source of truth: top-level version/site plus optional branding, toolbar, three item collections, nested action, and match arrays.
- `spec/SPEC.md` fixes the 131,072-byte limit and the order `transport → parse → version → schema → security`; this ticket implements the size and parse boundary only.
- `spec/fixtures/valid/` supplies parsing acceptance data. The oversized and malformed fixtures pin the two rejecting diagnostics.
- `:siteskin-core` already depends on kotlinx.serialization and is a pure JVM module. No new dependency is needed.
- Core currently has only origin-domain trusted values; no manifest DTO or parser exists.

## Origins and trust map
The serving origin is not an input to parsing. Any origin may supply these bytes; parsing cannot establish that URLs in them belong to that origin. URL strings remain raw and untrusted for `CORE-004`.

### Manifest-controlled after later validation
Site identity strings, home/logo URLs, colours, toolbar copy, item labels/icons, action values, and match patterns are represented exactly as received.

### Browser-controlled
The byte ceiling, diagnostic codes, validation order, domain/TLS chrome, origin binding, allow-lists, defaults, and activation remain browser-owned. DTO presence is never an activation decision.

## Findings and risks
- kotlinx.serialization's `ignoreUnknownKeys` silently drops unknown fields, so warning collection needs a deliberate pre-decode JSON-tree walk against an explicit shape. Decode still uses generated serializers; the walker reports paths but does not mutate data.
- Reading all bytes before checking size violates the transport rule. A bounded stream loop must stop on byte 131,073 and never call JSON decoding for that result.
- UTF-8 decoding must be strict. `String(bytes, UTF_8)` replaces malformed sequences, potentially accepting changed input; a reporting decoder must reject it as `SS-E-PARSE`.
- Missing required schema fields should parse into nullable DTO properties so `CORE-003`, not this layer, emits `SS-E-SCHEMA-INVALID`.
- Type mismatches cannot populate a typed DTO and therefore produce a parse failure in this implementation. `CORE-003` may introduce a JSON-element schema stage if exact structural diagnostics require preserving wrong-shaped documents; no schema decision is made here.
- Duplicate JSON object keys are left to kotlinx.serialization's JSON parser semantics; the published schema has no duplicate-key rule.

## Files likely affected
- New `dev/siteskin/core/manifest/*Dto.kt` DTOs.
- New parser outcome, diagnostic, and bounded parser in the same package.
- New DTO and parser JVM tests, including fixture and counting-stream coverage.
- `CLAUDE.md` only if implementation establishes a durable parsing convention.
