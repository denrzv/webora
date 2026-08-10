# Review: HARDEN-001
Date: 2026-08-10
Status: RESOLVED

## Summary

Reviewed TASK-1 for architecture, trust-boundary placement, failure disposition, parser safety,
transport behavior, corpus portability, test strength, and Detekt complexity. No open findings.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — nesting enforcement and constants stay in pure JVM core; OkHttp loop evidence stays in app. |
| Trust seam | PASS — bytes remain untrusted until the unchanged shared `SiteSkinValidator` accepts them. |
| Protocol ownership | PASS — the portable corpus owns JSON cases; MockWebServer owns network-context redirects. |
| Complexity | PASS — the finite-state scanner is split into bounded helpers and passes Detekt without a baseline entry. |

## Security

| Property | Assessment |
|---|---|
| Resource bounds | PASS — 128-KiB sentinel remains intact and depth 65 rejects before tree construction. |
| URI allow-list | PASS — five separately named hostile-scheme fixtures remain mandatory. |
| Redirect policy | PASS — an exact-origin loop still terminates after the two-hop budget. |
| Collection integrity | PASS — all three limits and duplicate first-wins behavior have canonical fixtures. |
| Fallback | PASS — depth rejection uses the existing parse-layer outcome and cannot construct trusted configuration. |

## Findings

None.

## Not findings

- Reusing `SS-E-PARSE` for excessive nesting is intentional: this is a pre-tree parser-policy
  rejection, not a new security disposition, and the corpus explicitly distinguishes syntactic
  validity from parser acceptance.
- The structural scanner does not implement the whole JSON grammar. It only bounds balanced
  object/array structure outside strings; kotlinx.serialization remains the grammar authority.
- IDN implementation was not changed because existing `SiteOriginTest` already pins Unicode and
  punycode equality, mixed-script presentation, and separation from origin equality.
- Redirect loops are not JSON fixtures because redirect state belongs to manifest transport, not
  the context-free conformance body.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `ManifestParserTest.kt` | depth boundary, strings/escapes, mismatched structure, exact/over byte limit | Pre-tree resource safety |
| `SpecCorpusTest.kt` and conformance suites | fixture integrity, named schemes, canonical results | Portable attack matrix |
| `OkHttpManifestSourceTest.kt` | self-origin loop and request count | Redirect termination |
| `SiteOriginTest.kt` | Unicode/punycode homograph equivalence | Canonical identity and display signal |

## Verdict

RESOLVED — ready for QA; no `TASK-FIX-*` required.
