# Review: SKIN-002
Date: 2026-08-09
Status: RESOLVED

## Summary
Reviewed commit `7c188b3` and the `TASK-FIX-1` working change for architecture, browser-owned
identity, accessibility, bounded imagery, tests, and complexity. Both findings are resolved.

## Architecture
| Concern | Assessment |
|---|---|
| Module boundary | PASS — Android bitmap and Compose code stay in `:app`; core remains unchanged. |
| Trust seam | PASS — model factory consumes only trusted configuration, closed brand asset, and browser observation. |
| Origin ownership | PASS — domain/TLS are required siblings of branding and are rendered unconditionally. |
| Scope | PASS — activation and ordinary browser chrome remain unchanged for `SKIN-004`. |
| Complexity | PASS — the full Detekt gate passes. |

## Security
| Property | Assessment |
|---|---|
| Domain suppression | PASS — no visibility field or manifest-controlled conditional exists. |
| TLS identity | PASS — wording maps from the closed browser-owned enum. |
| Logo confinement | PASS — both bitmap and monogram variants occupy the same 40 dp clipped slot. |
| Raw remote input | PASS — no DTO, JSON, raw colour, URL parsing, network, or decoding enters rendering. |

## Findings
1. **RESOLVED — Normal-size security text used a colour pair guaranteed only to 3:1.** `onPrimary` was the
   theme's non-text/UI contrast role, but the 12 sp TLS/domain text requires 4.5:1. Render the bar's
   text with the 4.5:1 `background`/`onBackground` pair so browser identity cannot be made faint.
2. **RESOLVED — A fixed 80 dp bar could clip three scaled text lines.** The bar now has a minimum
   height so large font scale expands rather than overlaps/clips the brand and security content.

## Not findings
- The component is intentionally standalone; `SKIN-004` owns activation and integration.
- The top-bar model is `internal`, so its data-class constructor does not expose a module API to
  manifest or core callers; production creation still has the typed factory.
- The bitmap itself is Android data at the final rendering edge; all fetch/decode policy remains in
  the existing loader.
- Runtime instrumentation and screenshots require a connected device; compiling the tests is the
  managed-cloud fallback mandated by repository instructions.

## Test coverage
JVM tests cover trusted toolbar mapping, omission fallback, closed brand variant, and the identity
negative control. Compose instrumentation covers mandatory security semantics and fixed logo width.

## Verdict
RESOLVED — `TASK-FIX-1` uses the 4.5:1 body pair and a minimum rather than fixed height; focused,
instrumentation-compilation, and full gates pass.
