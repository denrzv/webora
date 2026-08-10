# Review: HARDEN-002
Date: 2026-08-10
Status: RESOLVED

## Summary

Reviewed TASK-1 for origin provenance, consent ordering, accessibility semantics, branding bounds,
fallback, complexity, and test strength. No open findings.

## Architecture

| Concern | Assessment |
|---|---|
| Trust seam | PASS — trusted branding and browser-observed identity remain separate required inputs. |
| Origin boundary | PASS — consent display and storage now identify the same complete canonical origin. |
| UI ownership | PASS — copy, actions, security semantics, and logo bounds remain app-owned. |
| Complexity | PASS — the changes add no branching and Detekt passes without suppression or baseline. |

## Security

| Property | Assessment |
|---|---|
| Non-suppressible identity | PASS — hostile brand values cannot remove the dedicated domain/TLS node. |
| Informed consent | PASS — regular mode precedes Allow and the prompt names the exact grant origin. |
| Accessibility identity | PASS — logo descendants are decorative; security identity has browser-authored semantics. |
| Stale/cross-origin safety | PASS — existing generation and exact-origin publication/action checks remain intact. |
| Fallback | PASS — Not now, Never, stale, mismatched, and rejected candidates remain regular mode. |

## Findings

None.

## Not findings

- The active top bar deliberately shows registrable domain rather than full origin. ADR-006 defines
  that compact always-visible signal; the consent prompt is where full-origin grant precision is
  required.
- `clearAndSetSemantics { }` does not hide the logo visually. It prevents decorative monogram text
  from competing with the browser-owned domain/TLS accessibility identity.
- The persisted enum still has only Allow and Never. Not now is intentionally ephemeral under
  ADR-011, so adding a stored value would weaken rather than complete the contract.
- No mixed-script warning was added. HARDEN-001 pins the signal, while ADR-006 requires domain/TLS
  visibility; a new warning policy would be a separate product decision.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `SiteSkinRuntimeTest.kt` | Ask/Allow/Never, stale generation, different origin | Consent-before-branding and publication boundary |
| `SiteConsentStoreTest.kt` | scheme/host/port isolation and canonical equivalence | Exact-origin persistence |
| `SiteSkinTopBarModelTest.kt` | hostile title with independent security model | Identity provenance |
| `SiteSkinTopBarTest.kt` | security description, logo bound, decorative monogram | Compose presentation/semantics |
| `SiteSkinConsentDialogTest.kt` | canonical origin, boundary copy, three actions | Informed browser-owned consent |

## Verdict

RESOLVED — ready for QA; no `TASK-FIX-*` required.
