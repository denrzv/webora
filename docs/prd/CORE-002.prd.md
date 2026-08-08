# PRD: CORE-002 — DTOs and parsing
Status: PRD_READY

## Problem
SiteSkin manifests arrive as untrusted bytes. Core needs an inert representation and a bounded parser that distinguishes transport-size failure from malformed JSON without accidentally granting trust.

## Goals
- Model every SiteSkin 1.x schema field as nullable/defaulting kotlinx.serialization DTO data.
- Reject input larger than 131,072 bytes before JSON parsing and stop reading as soon as the limit is crossed.
- Ignore unknown JSON fields while reporting each unknown field occurrence as `SS-W-FIELD-UNKNOWN`.
- Return stable parse-layer outcomes without throwing across the public boundary.

## Non-goals
- Schema, supported-version, origin, action, icon, colour, duplicate-id, or normalization decisions (`CORE-003..005`).
- Network fetching, content-type, redirects, caching, or UI activation.
- Construction of `SiteSkinConfiguration` or any other trusted configuration.

## Acceptance criteria
1. Every parsing `spec/fixtures/valid/*.json` manifest produces an untrusted `SiteSkinManifestDto`.
2. All schema fields, nested objects, navigation items, actions, and match patterns are represented without Android dependencies.
3. Optional and unknown fields do not prevent parsing; every unknown field path produces `SS-W-FIELD-UNKNOWN` while known values remain available.
4. Malformed JSON produces `SS-E-PARSE` and no DTO.
5. Input above 131,072 bytes produces `SS-E-SIZE-EXCEEDED`, no DTO, and the parser reads no more than 131,073 bytes.
6. Empty, truncated, wrong-shaped, and invalid-UTF-8 input fail gracefully rather than throwing.
7. Parser success returns only the inert DTO plus warnings; it cannot produce a trusted `SiteSkinConfiguration`.
8. `bash scripts/pre-commit-check.sh` passes.
