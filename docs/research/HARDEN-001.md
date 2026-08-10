# HARDEN-001: Research
Status: RESEARCH_READY

## Question
Where each M4 roadmap attack reaches the existing trust pipeline, which promised cases already
exist, and which gaps require portable fixtures versus implementation-specific tests.

## Origins involved
- The manifest serving origin is the browser-observed full canonical `(scheme, host, port)` used by
  `SiteSkinValidator`, discovery redirects, and accepted configuration. HTTPS is required for
  activation; no sibling, parent, child, port, or scheme inherits trust.
- Hostile IDN input has two spellings: Unicode before canonicalization and ASCII punycode after it.
  Both must resolve to the same origin tuple and retain the same mixed-script presentation signal;
  the signal never participates in equality or grants trust.
- Redirect-loop coverage involves one manifest origin only. A loop may remain same-origin, so exact
  origin checking alone cannot stop it; the browser-owned redirect budget must.
- No brand asset behavior needs changing. Asset redirects and size bounds already have their own
  NET-003 tests; HARDEN-001 concerns manifest validation and discovery.

## Manifest-controlled surface
A website controls at most 128 KiB of manifest bytes, including JSON shape and nesting, strings,
collection order, ids, action types/values, and same-origin redirect responses. After acceptance it
can influence only the existing trusted branding/navigation model. Hostile entries may be dropped
and over-limit values truncated, but cannot expand the closed action model.

## Browser-owned remainder
The byte and nesting budgets, validation order, exact serving origin, redirect count, URI/action
allow-lists, diagnostic disposition, duplicate first-wins rule, collection limits, canonical origin
comparison, registrable-domain/mixed-script signal, TLS/domain chrome, native dispatch, and regular
mode fallback remain browser-owned. The shared validator is the only bytes-to-trusted-model seam;
OkHttp discovery owns network policy and supplies bytes rather than trust.

## Relevant code
| Path | Why it matters |
|---|---|
| `docs/DEVELOPMENT_PLAN.md` | M4 roadmap source; names the exact HARDEN-001 adversarial matrix. |
| `spec/SPEC.md` §§2, 7, 8, 10–13 | Normative redirect, allow-list, limit, disposition, normalization, and corpus contracts. |
| `spec/diagnostics.json` | Stable diagnostic/layer registry; existing `SS-E-PARSE` can represent excessive nesting without inventing a new disposition. |
| `spec/fixtures/invalid/scheme-*.json` | Already covers all five explicitly promised hostile schemes as separate portable cases. |
| `spec/fixtures/invalid/oversized.json` | Already commits a full over-128-KiB body and expects pre-parse `SS-E-SIZE-EXCEEDED`. |
| `spec/fixtures/invalid/duplicate-nav-id.json` | Already pins later-occurrence drop and first-wins order for navigation only. |
| `spec/fixtures/invalid/nav-over-limit.json` | Already pins first-five navigation truncation; quick actions and menu remain gaps. |
| `siteskin-core/.../manifest/ManifestParser.kt` | Reads a 131,073-byte sentinel and rejects malformed UTF-8, but currently sends arbitrary nesting directly into kotlinx.serialization and recursively scans the resulting tree without an explicit depth bound. |
| `siteskin-core/.../manifest/ManifestParserTest.kt` | Pins byte sentinel and stream ownership; needs exact-size and depth-bound controls. |
| `siteskin-core/.../spec/SpecCorpusTest.kt` | Runs every portable fixture through schema/security and has explicit per-scheme and ordering assertions; the right place for matrix-completeness assertions. |
| `siteskin-core/.../origin/SiteOriginTest.kt` | Already proves Unicode/punycode equality and mixed-script signal for `аpple.com`; add/clarify adversarial matrix assertions only if existing coverage is not explicit enough. |
| `app/.../siteskin/OkHttpManifestSource.kt` | Manually follows exact-origin redirects and rejects a third redirect; its finite budget already terminates loops. |
| `app/.../siteskin/OkHttpManifestSourceTest.kt` | Covers a three-redirect chain but not an explicit self/two-node loop named as such. |

## Prior art
- `ADR-004` makes canonical full-origin equality authoritative and keeps PSL/homograph display data
  out of trust comparison.
- `ADR-006` keeps domain/TLS chrome browser-owned even when the manifest supplies deceptive brand
  data; HARDEN-002 owns broader impersonation controls.
- `ADR-007` (short form in `docs/adr/README.md`) requires action and URI allow-lists, specifically
  calling out `intent:`.
- `ADR-009`/`ADR-010` keep discovery non-blocking and failure graceful.
- `SPEC-001..003`, `CORE-001..006`, and `NET-001` implemented the controls. HARDEN-001 audits and
  strengthens their adversarial evidence rather than creating a parallel validator.
- Existing corpus assertions identify every hostile scheme rather than accepting a mere diagnostic
  count, and separately verify duplicate first-wins behavior. Those should be retained.

## Risks
- Parser stack/resource exhaustion → perform a byte-level, string/escape-aware structural depth
  check before `parseToJsonElement`; do not recursively inspect an unbounded tree.
- Diagnostic drift → excessive nesting should use the existing parse-layer `SS-E-PARSE`, and the
  spec/corpus must agree on its reachability and structural-validity metadata.
- Off-by-one rejection → pin the maximum accepted depth and the first rejected depth, plus exact
  128-KiB versus sentinel-byte behavior.
- False roadmap completion → add a named attack-matrix test that lists all five schemes and portable
  fixture names, rather than relying on incidental general tests.
- Misplaced transport corpus → represent redirect loops with MockWebServer, not fake JSON fixtures;
  assert the bounded request count and rejected result.
- Collection gaps → use separate quick-action and menu over-limit fixtures (or one fixture with
  unambiguous pointers/results) and duplicate cases beyond navigation so all three collections and
  global first-wins semantics are evidenced.
- Large fixture churn → preserve the pinned Bloom fixture byte-for-byte and avoid changing existing
  diagnostics or schema limits.

## Open questions
- Choose a finite maximum JSON nesting depth. The v1 schema needs fewer than ten structural levels;
  64 is a conservative interoperability ceiling with ample forward-compatible headroom and a small
  parser stack. It should become a documented `SiteSkinLimits` constant and normative transport/
  parse rule rather than an undocumented kotlinx.serialization accident.
- `ROADMAP.md` does not exist at repository root. The authoritative ticket entry is
  `docs/DEVELOPMENT_PLAN.md` M4; no duplicate roadmap file should be invented.
