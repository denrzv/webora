# Review: CORE-006
Date: 2026-08-09
Status: RESOLVED

## Summary

Reviewed the navigation matcher, focused tests, AIDD artifacts, and trust-boundary decisions. The
implementation follows SiteSkin 1.0 precedence, fails to no selection, stays in pure JVM core, and
does not execute manifest patterns as regular expressions. No fix task is required.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — implementation and tests stay in `:siteskin-core` with no Android types. |
| Trust boundary | PASS — trusted `NavigationItem` values enter core; the browser-observed URL remains caller context. |
| API shape | PASS — one stateless operation returns at most one trusted item or `null`. |
| Complexity | PASS — iterative dynamic-programming state is bounded and detekt is green. |

## Security

| Property | Assessment |
|---|---|
| Pattern execution | PASS — no regex compilation or remote code execution. |
| Glob scope | PASS — `*` stays within a segment and only a complete `**` segment spans segments. |
| URL authority | PASS — only hierarchical HTTP(S) page URLs are accepted; authority/query/fragment do not become path patterns. |
| Failure behavior | PASS — malformed, unsupported, or unmatched input returns `null`, never the first item. |
| Negative control | PASS — bypassing bounded glob evaluation caused the cross-segment assertion to fail. |

## Findings

None.

## Not findings

- Decoding the URI path before matching is intentional: spec metacharacters such as `?`, brackets,
  and braces can only appear percent-encoded in a valid absolute URI, while the grammar requires
  them to be treated as literal path characters. Decoding also prevents `%2F` from letting `*`
  silently cross a semantic segment boundary.
- `NavMatcher` does not compare the page origin to the manifest origin. That check remains the
  runtime activation boundary; this component accepts trusted items and intentionally consumes
  only the observed page path.
- Dynamic-programming arrays allocate per pattern segment. Manifest list and string limits are
  already enforced by CORE-004, and the approach provides predictable polynomial behavior instead
  of regex/backtracking risk.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `NavMatcherTest.kt` | 8 focused tests | exact/glob precedence, ties, both wildcards, literal metacharacters, URI components, invalid input, no default, repeated wildcards |
| Core/full suites | pre-commit gate | JVM regression, dependency boundary, all unit tests, detekt, secrets, shell scripts |

## Verdict

RESOLVED — no open findings and no `TASK-FIX-*` work required.
