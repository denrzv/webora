# QA Report: CORE-005
Status: QA_PASSED

## Scope

Pure-JVM resolution of CORE-004 trusted action data into a closed semantic command hierarchy. Scope
includes all nine v1 types, trusted home/current-page source selection, fail-closed inconsistent
state, external-navigation separation, and the Android dependency boundary. Raw manifest
validation, Android execution, navigation matching, network behavior, and UI remain out of scope.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | All nine v1 action types | `ActionResolverTest.all v1 action types resolve to semantic effects` | PASS — expected eight semantic effects produced |
| 2 | Internal vs external navigation | Typed equality assertions | PASS — external command remains distinct for confirmation |
| 3 | Trusted home source | Trusted `SiteConfiguration` plus hostile unused payload | PASS — `site.homeUrl` wins |
| 4 | Browser-observed share source | Browser page plus hostile unused payload | PASS — current page wins |
| 5 | Unknown action type | `open_intent` allow-list test | PASS — no resolved capability |
| 6 | Missing required payload | URL/value cases with absent payloads | PASS — each returns null |
| 7 | Closed public effect surface | `TrustedModelApiTest` Java sealed-subclass inspection | PASS — only eight intended variants |
| 8 | Security negative control | Generic unknown-type external navigation temporarily introduced | PASS — focused test failed with exit 1, then protection restored |
| 9 | Pure-JVM boundary | `./gradlew :siteskin-core:check` | PASS — Android dependency leak and Detekt gates green |
| 10 | Full project gate | `bash scripts/pre-commit-check.sh` | PASS |

## Edge cases

- invalid manifest → regular browser mode: **N/A at this seam.** CORE-004 drops unknown or unsafe
  manifest items before this resolver; inconsistent trusted data returns null rather than executing.
- origin change / redirect: **N/A — no navigation lifecycle logic changed.** Internal/home URLs are
  already exact-origin trusted values; origin transitions belong to SKIN-004 and redirects NET-001.
- offline with cached manifest: **N/A — no network/cache logic changed.** Resolution is deterministic
  pure data transformation after cache validation.
- oversized or malformed payload: **N/A — CORE-002/CORE-004 own bounds, parsing, and normalization.**
  Missing payload defense-in-depth cases return null.
- accessibility (TalkBack, font scale): **N/A — no UI changed.** Semantic variants preserve enough
  distinction for later browser-owned accessible labels and confirmation UI.

## Result

Status: QA_PASSED

Notes: Review finding 1 is resolved, all task and fix-task statuses are complete, focused and full
local gates pass, and the security negative control is recorded. This managed checkout has no
configured remote, so no branch CI result exists to inspect; local checkpoint commits are valid
under the repository's managed-cloud workflow.
