# QA Report: SPEC-002
Status: QA_PASSED

## Scope

Versioning and compatibility policy. Ten commits, `d7b95cb`..`1bee1ff`.

**Nothing executable ships.** The change is `spec/` (normative text, JSON Schema, two registries,
four fixtures), `docs/`, `CLAUDE.md`, and `siteskin-core/src/test`. No file under
`siteskin-core/src/main` or `:app` is touched, so there is no runtime behaviour to exercise — the
version layer this ticket specifies is implemented by `CORE-003`.

QA is therefore scoped to what *is* assertable now: that the corpus and registries say what the spec
says, that the guards added actually detect the defects they name, and that the two schema
corrections behave as claimed against the real validator.

## Test scenarios

| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Corpus suite green after every task | `ANDROID_HOME= ./gradlew :siteskin-core:test` | ✅ 36 tests, 0 failures |
| 2 | Complexity gate | `./gradlew detekt` | ✅ green (after `TASK-FIX-1`) |
| 3 | Android-free core | test task run with `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset | ✅ green |
| 4 | Layer order and `layers` describe the same set | `layerOrderCoversEveryRegisteredLayer` | ✅ |
| 5 | No fixture expects a diagnostic past its own rejection | `diagnosticsDoNotCrossARejectingLayer` | ✅ (vacuous on today's corpus — see Edge cases) |
| 6 | `parses` agrees with the layer order | `parsesFlagAgreesWithTheLayerOrder` | ✅ |
| 7 | 17-row version table matches real schema behaviour | `versionTableMatchesTheSchemaGrammar` via `schemaAcceptsVersion` | ✅ all 17 rows, including `number` and `absent` |
| 8 | Grammar and policy rejections stay on their own layers | `versionTableSeparatesGrammarFromPolicy` | ✅ |
| 9 | Acceptance follows `supportedMajors`, recomputed independently | `versionTableAcceptanceFollowsTheSupportedMajors` | ✅ |
| 10 | Boundary cases named individually, not counted | `versionTableCoversTheBoundary` | ✅ 15 spellings + both non-string forms |
| 11 | Table invents no diagnostic code | `versionTableCodesAreRegistered` | ✅ |
| 12 | No schema pattern ends in a bare `$` | `schemaPatternsAnchorAtEndOfInput` | ✅ all 6 patterns |
| 13 | `schemaVersion` rejects leading/trailing whitespace incl. `\n`, `\r\n`, `\t` | `schemaVersionRejectsTrailingAndLeadingWhitespace` | ✅ 11 spellings rejected, `1.0` accepted |
| 14 | Alien `2.0` expressible only because version precedes schema | `invalid/version-major-2-alien` through the full corpus suite | ✅ |
| 15 | Unknown-field policy is version-independent | `invalid/unknown-field-1.0` | ✅ warns twice, survives to a canonical result |
| 16 | `SS-W-FIELD-DEPRECATED` reserved but unregistered | `grep` both files + `everyRegisteredCodeHasAFixture` | ✅ 1 hit in `SPEC.md`, 0 in `diagnostics.json` |
| 17 | Existing corpus unchanged in meaning | `git diff d7b95cb..HEAD -- spec/fixtures/{valid,invalid}` | ✅ additions only; no pre-existing `.expected.json` edited |

### Negative controls

Per `CLAUDE.md` § Testing, every protection was reverted and confirmed to fail. Six performed, all
restored:

| Control | Expected failure | Result |
|---|---|---|
| Fixture pairing a transport `reject` with a security `warn` | `diagnosticsDoNotCrossARejectingLayer` | ✅ exactly 1 test failed, naming the layer and the code |
| `schemaVersion` pattern reverted to `^[0-9]+\.[0-9]+$` | anchoring + whitespace guards | ✅ 3 tests failed |
| Same revert, checked against the table | `versionTableMatchesTheSchemaGrammar` | ✅ failed on `01.0` |
| `2.0` row flipped to `wellFormed: false` | `versionTableSeparatesGrammarFromPolicy` | ✅ failed, plus the grammar check |
| Alien fixture flipped to `"schemaValid": true` | `securityLayerFixturesPassTheSchema` | ✅ failed on `/site: element is not a object` |
| Leading `^` deleted from `schemaVersion.pattern` | `versionTableMatchesTheSchemaGrammar` | ✅ **after** `TASK-FIX-2`; **stayed green before it**, which is what made it `/review` FINDING-1 |

The last row is the one worth keeping: the control was run, it *passed when it should have failed*,
and that is how the finding was found. A control that only ever confirms is not doing its job.

One planned control was **withdrawn as invalid**: reordering `layerOrder` does not fail
`diagnosticsDoNotCrossARejectingLayer`, because no existing fixture pairs a `reject` with anything.
Replaced with a constructed violation rather than reported as a pass.

## Edge cases

- **invalid manifest → regular browser mode** — unchanged and untouched. Every version rejection in
  this ticket carries disposition `reject`, which `SPEC.md` §10 and `ADR-010` define as falling back
  to regular browsing with the page still rendering. No new failure path was introduced; the alien
  `2.0` and missing-version fixtures both land on the existing one.
- **origin change / redirect** — N/A, no logic change. This ticket adds no URL-bearing field, no
  fetch and no redirect handling. The version's only interaction with origin is the cache key
  (`origin + schemaVersion`), and the grammar fix *strengthens* it by removing the second spelling —
  recorded in `SPEC.md` for `NET-002` to inherit rather than left to be rediscovered.
- **offline with cached manifest** — N/A here, but with a caveat that belongs to `NET-002`, not to
  silence: because `schemaVersion` participates in the cache key, a site bumping `1.0`→`1.1` gets a
  distinct entry rather than a stale one. That is the intended behaviour and the reason two
  spellings of one version had to be eliminated. `NET-002` must not normalize the version when
  constructing the key.
- **oversized or malformed payload** — covered and *clarified*. `oversized` (transport) and
  `malformed-json` (parse) both reject before the schema, which was previously expressed by one
  hand-rolled flag and one accident; both are now consequences of the published layer order.
  `parsesFlagAgreesWithTheLayerOrder` ties the two representations together. Neither fixture's
  expectations changed.
- **accessibility (TalkBack, font scale)** — N/A. No user-visible surface. Nothing in this ticket
  reaches a pixel; the nearest UI consumer is `SKIN-002`.

## Result

Status: QA_PASSED
Notes:

**One environmental caveat, and it is not a pass.** `bash scripts/pre-commit-check.sh` does **not**
complete in this container. Its `unit tests` step runs `./gradlew test`, which includes
`:app:testDebugUnitTest`, which needs an Android SDK — none is installed, `ANDROID_HOME` and
`ANDROID_SDK_ROOT` are empty, and there is no `local.properties`.

Confirmed **pre-existing** by stashing every change in this ticket and re-running against a clean
tree: identical failure, identical step. `gitleaks` and `shellcheck` are also absent and are skipped
with a warning, as the script does on any machine lacking them; CI runs both.

This ticket compiles no `:app` code and touches nothing under `siteskin-core/src/main`, so the
unrunnable step is unrunnable rather than failing. **It has not been observed green and must be
before `/validate` is considered complete on a machine with the SDK.** PRD acceptance criterion 12
is therefore *unverified in this environment*, not satisfied — every other criterion (1–11) is
verified by the scenarios above.
