# QA Report: HARDEN-001
Status: QA_PASSED

## Scope

Adversarial manifest parsing, shared conformance fixtures, collection normalization, exact-origin
redirect termination, and retained IDN canonicalization guarantees.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Depth 64 accepted; depth 65 rejected before tree construction | Focused `ManifestParserTest` | PASS |
| 2 | Brackets/escaped quotes inside strings do not alter structural depth | Focused parser test | PASS |
| 3 | Exact 128 KiB reaches parsing; sentinel byte rejects | Focused parser test and existing oversized fixture | PASS |
| 4 | Five denied URI schemes remain independently represented | `SpecCorpusTest` | PASS |
| 5 | Navigation, quick actions, and menu keep first N in order | Security conformance corpus | PASS |
| 6 | Later duplicate action id drops while first survives | Security conformance corpus | PASS |
| 7 | Same-origin redirect loop terminates after allowed hops | `OkHttpManifestSourceTest` | PASS |
| 8 | Unicode and punycode preserve exact identity and homograph signal | Existing `SiteOriginTest` in core suite | PASS |
| 9 | Full repository guardrail | `bash scripts/pre-commit-check.sh` | PASS on rerun |

## Edge cases
- invalid manifest → regular browser mode: PASS — total validator returns rejection with no configuration.
- origin change / redirect: PASS — cross-origin behavior remains covered; the new same-origin loop is bounded.
- offline with cached manifest: PASS — no cache behavior changed; the full app unit suite remains green.
- oversized or malformed payload: PASS — size sentinel, malformed UTF-8/JSON, structural mismatch, and excessive depth are covered.
- accessibility (TalkBack, font scale): N/A — no UI or semantics change.

## Result
Status: QA_PASSED
Notes: The first full gate run exposed one intermittent failure in the pre-existing asynchronous
OkHttp cancellation test; its immediate full-gate rerun passed. No product or test code was changed
to mask it. No runtime instrumentation is required because this ticket changes pure validation and
JVM transport tests, not UI.
