# Review: CORE-004
Date: 2026-08-08
Status: RESOLVED

## Summary

The implementation establishes a narrow pure-JVM trust boundary, matches all currently reachable
canonical fixtures, and includes meaningful negative controls. One ordering defect remains: colour
and toolbar-limit diagnostics can be emitted before earlier normalization stages when a single
manifest triggers both. The corpus currently isolates these cases, so a combined focused test is
required.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — all URI, model, colour, and policy code remains in Android-free core |
| Trust construction | PASS — public configuration has no normal public constructor/copy path; validator is the supported factory |
| CORE-002 seam | PASS — parsed JSON is explicit and unknown-field discovery remains assigned to CORE-002 |
| CORE-005 seam | PASS — actions remain inert normalized data, with no platform dispatch or permissions |

## Security

| Property | Assessment |
|---|---|
| Exact origin | PASS — parsed scheme/host/effective-port comparisons, traversal/userinfo controls, and negative control |
| Allow-lists | PASS — action/icon sets and HTTPS external navigation are positive allow-lists |
| Bounded UI | PASS — collections and grapheme-safe strings clamp with stable pointers |
| Contrast | PASS — exact deterministic sRGB algorithm matches the hostile fixture |
| Diagnostic ordering | RESOLVED — TASK-FIX-1 adds a combined exact-order regression test |

## Findings

### FINDING-1 · Medium · SPEC §12 normalization order
**File:** `siteskin-core/src/main/kotlin/dev/siteskin/core/SecurityValidator.kt:42-48`

Current normalization constructs branding and toolbar before navigation. `normalizeBranding` can
emit contrast warnings and `normalizeToolbar` can emit limit warnings before navigation emits URL,
action, duplicate, and collection-limit diagnostics. Each published fixture currently triggers an
isolated rule, so canonical conformance remains green while a combined manifest reports the wrong
order.

Fix: separate branding URL preparation from colour correction, and perform diagnostic-producing
work in the normative order: home/logo URLs; action/icon policy; duplicate removal; collection and
string limits; colours. Add one combined test asserting exact code/pointer order.

## Not findings

- `SecurityValidator` accepting `JsonObject` is not a parser bypass: CORE-003 validates this same
  parsed-tree seam, and CORE-002 is explicitly responsible for the byte/parser/DTO adapter.
- Unknown-field diagnostics are intentionally absent from security conformance comparisons. CORE-002
  owns discovery, while this validator proves unknown fields cannot enter the trusted projection.
- Empty navigation is nullable only when absent; an explicitly present collection that loses every
  item remains an empty list, preserving the canonical distinction required by SPEC §10.
- `phone`, `email`, and `map` retain inert `value` strings rather than opening URI handlers. CORE-005
  owns sealed action resolution; platform execution does not belong at this trust boundary.
- Detekt suppressions in `OriginPolicy` preserve explicit fail-closed returns in security code rather
  than obscuring the cases inside a generalized boolean.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `OriginPolicyTest.kt` | 4 | HTTPS origin canonicalization, origin attacks, caller origin, external HTTPS |
| `ColorPolicyTest.kt` | 3 | colour canonicalization, unchanged contrast, deterministic correction |
| `SecurityValidatorTest.kt` | 4 | trusted output, action/icon/duplicate/limits, invalid origin, graphemes |
| `TrustedModelApiTest.kt` | 1 | no normal public constructor or copy escape hatch |
| `SecurityConformanceTest.kt` | corpus-driven | every security-reachable canonical result and diagnostic |

## Verdict

RESOLVED by `TASK-FIX-1`: branding colour and toolbar clamping now run after URL/action,
duplicate, and collection work, with a combined exact-order regression test. No finding blocks QA.
