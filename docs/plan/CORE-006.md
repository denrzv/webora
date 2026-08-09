# CORE-006: Implementation plan
Status: PLAN_APPROVED

References: `docs/prd/CORE-006.prd.md`, `docs/research/CORE-006.md`, `spec/SPEC.md` §7.1, and
`docs/adr/README.md` ADR-003/ADR-005.

## Overview

Add a stateless pure-JVM `NavMatcher` that receives trusted navigation items and the
browser-observed current URL, extracts only its raw path, evaluates the restricted SiteSkin glob
grammar with a handwritten segment matcher, ranks candidates, and returns the selected item or
`null`.

## Flow

```text
trusted ordered NavigationItem list + browser-observed current URL
  → defensive java.net.URI parsing → raw path only (query/fragment excluded)
  → exact literal candidates and restricted-glob candidates
  → exact first; otherwise longest literal prefix; otherwise earliest item
  → one trusted NavigationItem, or null
```

## Data

CORE-004 remains the remote-input trust boundary. It validates and bounds manifest collections and
constructs `NavigationItem`; CORE-006 consumes those trusted values and introduces no DTO, cache,
storage key, diagnostic, or normalization path. The list order is significant browser input because
the specification uses document order as the final tie-break.

The current page URL is browser-observed context. It is parsed with `java.net.URI`; only a
hierarchical HTTP(S) URI with authority and a non-null raw path participates. Query and fragment
never reach the matcher. The runtime remains responsible for activating SiteSkin only on the bound
origin; this path matcher does not weaken or repeat that origin check.

## Security

- **Website-controlled surface:** trusted manifest data may provide path patterns and item order.
  It cannot provide the current URL, active state, regex, callbacks, or cross-origin activation.
- **Browser-owned contract:** the browser supplies the observed URL; core fixes parsing, grammar,
  ranking, and null fallback; app UI may render only the single result.
- **Grammar allow-list:** only a complete `**` segment has recursive meaning. Otherwise `*` matches
  within one segment; all non-star characters, including regex metacharacters, are literal.
- **No regex execution:** tokenize paths into segments and use an iterative wildcard comparison.
  Recursive `**` matching uses bounded dynamic-programming state rather than exponential
  backtracking.
- **Origin boundary:** matching ignores the URL authority rather than comparing it to pattern text.
  Existing runtime origin activation remains mandatory and out of scope.
- **Fallback:** malformed, opaque, authority-less, or non-HTTP(S) URLs and unmatched paths return
  null without throwing or selecting the first item.

## File-by-file plan

### New: `siteskin-core/src/main/kotlin/dev/siteskin/core/nav/NavMatcher.kt`

Public documented stateless matcher, defensive URL path extraction, candidate ranking, literal
prefix calculation, and restricted segment-glob implementation.

### New: `siteskin-core/src/test/kotlin/dev/siteskin/core/nav/NavMatcherTest.kt`

Construct trusted items through existing validation and cover precedence, tie-breaking, wildcard
segment boundaries, zero-segment `**`, literal metacharacters, query/fragment exclusion, malformed
URLs, no default, and adversarial repeated wildcard patterns.

No schema, fixture, model, or validator file changes are planned because the v1 grammar and trusted
shape already exist.

## Tests

- Focused `NavMatcherTest` cases for all acceptance and boundary behavior.
- Security negative control: temporarily make `*` cross `/` (or route patterns through unrestricted
  regex behavior), confirm the segment-boundary test fails, then restore the protection.
- `./gradlew :siteskin-core:test` and `./gradlew :siteskin-core:check`.
- `bash scripts/pre-commit-check.sh` before the task commit.

## Rollout / versioning

No protocol or storage migration. This implements existing SiteSkin 1.0 behavior and is unused by
Android UI until SKIN-003.

## Open questions

None. Per the research interpretation, recursive `**` semantics apply only when `**` is a whole
segment; this is the narrow meaning of the normative phrase “whole path segments.”
