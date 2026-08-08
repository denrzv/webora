# QA Report: CORE-002
Status: QA_PASSED

## Scope
Pure-JVM SiteSkin DTO mapping and bounded manifest parsing, including strict encoding, unknown-field diagnostics, early size rejection, and graceful stream failure.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Complete and minimal DTO mapping | `SiteSkinManifestDtoTest` | PASS — all schema members map and absent fields remain inert nullable data. |
| 2 | Published valid corpus | `ManifestParserTest.allValidParsingFixturesParse` | PASS — every `spec/fixtures/valid/*.json` file parsed. |
| 3 | Malformed JSON and invalid UTF-8 | Focused parser tests | PASS — both return `SS-E-PARSE` without throwing. |
| 4 | Oversized streaming body | Counting/generating input plus negative control | PASS — rejection occurs after exactly 131,073 bytes; removing the cap fails the test. |
| 5 | Forward-compatible unknown fields | Root, nested, item, and action path tests plus negative control | PASS — known siblings survive and each occurrence emits `SS-W-FIELD-UNKNOWN`. |
| 6 | Stream ownership and failure | Close-tracking and throwing streams | PASS — caller stream stays open; `IOException` becomes rejection. |
| 7 | Repository guardrails | `bash scripts/pre-commit-check.sh` | PASS — secrets, shell, core without Android SDK, unit tests, and Detekt green. |

## Edge cases
- invalid manifest → regular browser mode: PASS — parser returns a sealed rejection and does not construct a trusted/activation type.
- origin change / redirect: N/A — parsing deliberately receives no origin and performs no discovery or redirect handling.
- offline with cached manifest: N/A — caching and network state are outside CORE-002.
- oversized or malformed payload: PASS — exercised directly, including early-read evidence, invalid UTF-8, empty and wrong-shaped JSON.
- accessibility (TalkBack, font scale): N/A — pure core data/parsing change with no UI.

## Result
Status: QA_PASSED
Notes: Local validation is complete. This managed checkout has no configured remote, so branch CI cannot be observed locally; the platform PR mechanism is the durable checkpoint.
