# Review: SPEC-003
Date: 2026-08-09
Status: RESOLVED

## Summary

Reviewed the total core validation path, CLI argument/exit contract, OkHttp discovery boundary,
corpus coverage, negative controls, and packaging. Core policy is shared correctly and redirect
origin checks fail closed. The one transport-contract finding was resolved by `TASK-FIX-1`.

## Architecture

| Concern | Assessment |
|---|---|
| Module boundary | PASS — validation remains pure JVM core; OkHttp stays in `:siteskin-lint`. |
| Shared validator | PASS — CLI receives only the total `SiteSkinValidator` outcome and contains no schema/security policy. |
| Trust boundary | PASS — accepted results require validator construction against the canonical caller origin. |
| CLI shape | PASS — thin `main`, testable runner, generated application distribution. |

## Security

| Property | Assessment |
|---|---|
| HTTPS input | PASS — public command rejects HTTP, paths, queries, fragments, and userinfo. |
| Redirects | PASS — automatic redirects are disabled; exact canonical origin and two-hop cap are asserted. |
| Response bounds | PASS — body streams into core's 131,073-byte probe; timeouts are explicit. |
| Response media type | PASS — absent/JSON declarations proceed; explicit contradictory types fail before validation. |
| Output safety | PASS — hostile bodies and exception details are not printed. |

## Findings

1. **RESOLVED — transport accepted an explicitly contradictory Content-Type.** `SPEC.md` §2 permits a missing
   header but requires rejection when the declared type contradicts JSON. `ManifestDiscovery`
   currently passes every successful response body to core. A server returning `text/html` with a
   JSON-looking body could therefore lint successfully even though a conforming browser must not
   activate it. `TASK-FIX-1` now accepts missing and JSON media types (including parameters),
   rejects explicit non-JSON types before reading the body, and covers both directions.

## Not findings

- Corpus transport is injected rather than served from public loopback HTTP. This preserves each
  fixture's declared HTTPS origin for security normalization; separate MockWebServer tests prove
  request and redirect behavior.
- `invalid/` fixtures with `warn` or `drop-item` diagnostics exit 0. The directory denotes a
  diagnostic fixture, while registry disposition and trusted configuration determine activation.
- Operational errors do not print synthetic `SS-*` codes. The protocol registry has no DNS, TLS,
  HTTP-status, or usage diagnostics, and inventing one would violate corpus completeness.
- Schema diagnostics may lack pointers. The published CLI promise is stable codes; detailed schema
  pointers would require expanding `SchemaValidator` and are not reimplemented in the command.

## Test coverage

| Area | Coverage |
|---|---|
| Total validator | stage ordering, malformed/structural distinction, origin binding, stream ownership, all fixtures |
| Command | usage/origin grammar, accepted diagnostics, rejecting status, concise operational failure |
| HTTP | well-known path, two same-origin redirects, cross-origin refusal, hop limit, status, timeout |
| Corpus/distribution | all fixtures, explicit Bloom positive, generated script usage smoke |

## Verdict

RESOLVED — finding 1 is fixed and no open findings remain.
