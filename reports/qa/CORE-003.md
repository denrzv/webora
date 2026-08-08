# QA Report: CORE-003
Status: QA_PASSED

## Scope

Production version/schema validation over already parsed JSON, its stable diagnostic/result API, and
conformance with the published version table and fixture corpus. Transport parsing, runtime browser
fallback, origin/security normalization, networking, and UI are explicitly outside this ticket.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Current and future `1.x` versions | Execute every accepted `spec/versions.json` row through production validator | PASS — no errors or warnings |
| 2 | Unsupported canonical majors | Version table plus both `2.0` fixtures | PASS — exactly `SS-E-VERSION-UNSUPPORTED` |
| 3 | Alien unsupported document | Disable version short circuit as negative control | PASS — conformance test failed, then passed after restoration |
| 4 | Missing/non-string/malformed versions | Version table and focused tests | PASS — exactly `SS-E-SCHEMA-INVALID` |
| 5 | Required fields, types, patterns, nested action requirements | Entire parsable corpus plus focused collection/action tests | PASS |
| 6 | Unknown fields, action types, and icons | Security-layer corpus fixtures; enum negative control | PASS — structurally accepted; enum regression was detected |
| 7 | Diagnostic registry API | Compare public enum and registry in both directions, including uniqueness | PASS — all thirteen codes match |
| 8 | JVM/core boundary and project checks | `bash scripts/pre-commit-check.sh` | PASS — core without Android SDK, unit tests, Detekt, scans |

## Edge cases

- invalid manifest → regular browser mode: **N/A at runtime — no browser integration in scope.** The
  validator returns a rejecting error result and constructs no trusted configuration, enabling the
  caller's required fallback.
- origin change / redirect: **N/A — no origin or network input in schema validation.** These remain
  transport/security responsibilities.
- offline with cached manifest: **N/A — no cache or networking change.**
- oversized or malformed payload: **N/A at this API seam — CORE-002 owns size guarding and JSON
  parsing.** A malformed *structure* after successful parsing returns `SS-E-SCHEMA-INVALID`.
- accessibility (TalkBack, font scale): **N/A — no UI or user-visible behavior changed.**

## Result

Status: QA_PASSED

Notes: All in-scope scenarios and mandatory negative controls pass. The checkout intentionally has
no configured remote, so there is no branch CI result to inspect; the complete local pre-commit gate
is green.
