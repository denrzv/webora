# QA Report: SPEC-003
Status: QA_PASSED

## Scope

Pure-JVM total SiteSkin validation, the `siteskin-lint` origin command, bounded exact-origin HTTP
discovery, stable diagnostic/exit rendering, full corpus conformance, and Gradle application
distribution.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Raw JSON follows parse/version/schema/security order | `SiteSkinValidatorTest` and layer-order negative control | PASS |
| 2 | Every corpus fixture has expected activation and ordered codes | `TotalPipelineConformanceTest`, `CorpusCliTest` | PASS |
| 3 | Bloom Flowers activates with exit 0 | Explicit corpus positive control | PASS |
| 4 | Warning/drop-item manifests still activate and print codes | Command corpus test and exit-semantics negative control | PASS |
| 5 | Malformed/structural/version rejection exits non-zero | Core and CLI corpus coverage | PASS |
| 6 | Public input is one origin-only HTTPS URL | `CommandTest` argument table | PASS |
| 7 | Discovery uses well-known path and at most two redirects | `ManifestDiscoveryTest` | PASS |
| 8 | Cross-origin redirect never receives a request | MockWebServer distinct-port test and negative control | PASS |
| 9 | Missing/JSON media types pass; contradictory type fails | Focused discovery test and negative control | PASS |
| 10 | HTTP status and timeout fail without hostile content | Focused discovery tests | PASS |
| 11 | Installed script is executable and documents usage | `installDist` smoke, expected exit 2 | PASS |
| 12 | Full repository guardrails | `bash scripts/pre-commit-check.sh` | PASS |

## Edge cases

- invalid manifest → Rejecting diagnostics return non-zero; warning/drop-item diagnostics retain a
  trusted result and exit 0, matching registry disposition rather than directory naming.
- origin change / redirect → Redirects are compared by canonical scheme/host/effective port and
  capped at two; subdomain/port changes fail before the target receives a request.
- offline with cached manifest → No cache exists in this CLI. DNS, TLS, timeout, and other request
  failures return one concise operational error and exit 1.
- oversized or malformed payload → Core reads at most 131,073 bytes, emits the stable size/parse
  code, and returns no trusted configuration; corpus cases pass.
- accessibility (TalkBack, font scale) → No Android UI exists. Plain ordered terminal lines and
  stable codes work with screen readers and CI logs without color-only meaning.

## Result

Status: QA_PASSED

Notes: Focused core/CLI tests, all corpus fixtures, three security negative controls, installed
distribution smoke, pure-JVM dependency guard, full unit tests, detekt, gitleaks, and shellcheck
passed. No emulator or visual screenshot applies to this JVM command-line ticket.
