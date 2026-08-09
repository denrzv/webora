# QA Report: CORE-004
Status: QA_PASSED

## Scope

Pure-JVM security normalization from schema-valid parsed JSON and a browser-observed HTTPS origin to
an immutable trusted configuration. Scope includes exact-origin URLs/assets, allow-listed inert
actions/icons, duplicate and limit handling, Unicode-safe strings, WCAG correction, ordered stable
diagnostics, and canonical corpus projection. Byte parsing/unknown-field discovery (CORE-002),
platform action resolution (CORE-005), fetching, cache, and UI remain outside this ticket.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Exact HTTPS origin and canonical URLs | `OriginPolicyTest` plus cross-origin/port/protocol-relative/userinfo/traversal corpus fixtures | PASS |
| 2 | External and internal scheme policy | Focused denied-action test and all `scheme-*` / `external-url-http` fixtures | PASS — unsafe item drops with stable pointer |
| 3 | Action and icon allow-lists | Unknown action/icon fixtures and focused bypass controls | PASS — item drops or generic icon substitutes as specified |
| 4 | Assets and subdomains | `logo-subdomain` canonical conformance | PASS — logo drops, branding remains |
| 5 | Duplicate and collection limits | Duplicate/over-limit fixtures plus focused bypass controls | PASS — first id and first N safe items win |
| 6 | Unicode-safe string limits | Combining-mark focused test and label fixture | PASS — no grapheme split, stable warning pointer |
| 7 | WCAG normalization | Colour unit tests and hostile-contrast canonical fixture | PASS — deterministic correction and diagnostic |
| 8 | Cross-stage diagnostic ordering | Combined origin/duplicate/limit/contrast regression test | PASS — exact SPEC §12 code/pointer order |
| 9 | Trusted API boundary | Reflection/API test | PASS — no normal public constructor or `copy()` escape hatch |
| 10 | Complete security corpus | `SecurityConformanceTest` | PASS — every reachable canonical result and security diagnostic matches |
| 11 | Pure-JVM/project gates | `./gradlew :siteskin-core:check detekt`; `bash scripts/pre-commit-check.sh` | PASS |

## Edge cases

- invalid manifest → regular browser mode: **PASS at this seam.** Invalid browser origin or violated
  caller precondition constructs no trusted configuration; localized manifest defects drop/fallback
  without turning an explicitly emptied navigation list into whole-manifest rejection.
- origin change / redirect: **Origin change PASS for normalized values; redirect N/A.** Every retained
  internal URL is bound to the supplied exact origin. Fetch/redirect enforcement belongs to NET-001.
- offline with cached manifest: **N/A — no cache or networking logic changed.** Cached bytes must
  still pass the same pipeline before this validator is invoked.
- oversized or malformed payload: **N/A at this API seam — CORE-002 owns size guard and parsing.**
  This API deliberately consumes schema-valid parsed JSON.
- accessibility (TalkBack, font scale): **No UI in scope.** Bounded text remains Unicode-safe and
  manifest colours are corrected to WCAG AA targets before later UI consumption.

## Result

Status: QA_PASSED

Notes: All in-scope scenarios, canonical fixtures, review regression, Detekt, Android-dependency
check, and mandatory negative controls pass. The checkout intentionally has no configured remote,
so there is no branch CI result to inspect; the complete local pre-commit gate is green.
