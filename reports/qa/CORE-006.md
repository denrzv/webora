# QA Report: CORE-006
Status: QA_PASSED

## Scope

Pure-JVM active navigation selection for trusted SiteSkin navigation items, including URL path
extraction, exact/glob precedence, restricted wildcard grammar, and no-selection fallback.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Exact path beats glob regardless of order | `NavMatcherTest.exact match beats an earlier and longer glob` | PASS |
| 2 | Longest literal prefix and document-order tie | Focused unit test | PASS |
| 3 | `*` cannot cross a path segment | Positive, empty-segment, and negative unit assertions | PASS |
| 4 | `**` matches zero, one, or many whole segments | Focused unit test | PASS |
| 5 | Non-star metacharacters stay literal | Encoded URI path cases for `?`, brackets, and braces | PASS |
| 6 | Query and fragment are ignored | Absolute URL unit test | PASS |
| 7 | Invalid/unsupported/unmatched URL returns no item | Negative table plus empty-list test | PASS |
| 8 | Repeated recursive wildcards complete deterministically | 64-pattern/64-segment unit test | PASS |
| 9 | Full repository guardrails | `bash scripts/pre-commit-check.sh` | PASS |

## Edge cases

- invalid manifest → N/A — CORE-004 owns manifest rejection; this matcher accepts trusted items.
- origin change / redirect → The runtime must deactivate/rebind before calling this path-only
  matcher; malformed or unsupported observed URLs return no selection.
- offline with cached manifest → N/A — pure deterministic matching has no network or cache access.
- oversized or malformed payload → CORE-004 enforces manifest limits; malformed current URLs return
  no selection and repeated-wildcard coverage confirms bounded evaluation.
- accessibility (TalkBack, font scale) → No UI is added; returning at most one item gives SKIN-003
  one unambiguous selected state to announce.

## Result

Status: QA_PASSED

Notes: Focused tests, the security negative control, full JVM/unit suites, dependency guard,
detekt, gitleaks, and shellcheck passed. No emulator or manual UI scenario applies to this core-only
ticket.
