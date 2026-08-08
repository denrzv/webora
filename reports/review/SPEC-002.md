# Review: SPEC-002
Date: 2026-08-08
Status: RESOLVED

## Summary

Reviewed the eight commits from `d7b95cb` (artifacts) through `67c1b13` (close-out) against
`docs/prd/SPEC-002.prd.md`'s twelve acceptance criteria.

The ticket delivers what it promised and found two real defects in the published schema on the way.
The layer model is the structurally significant change: it converts an ordering that existed only as
prose inside one registry field into data that a test reads, and it does so without editing a single
existing expectation file — which is the evidence that the model describes the corpus rather than
being imposed on it.

**One finding.** `versionTableMatchesTheSchemaGrammar` — the test that carries most of TASK-4's
weight — checks the decision table against a `Regex` built from the schema's pattern, using
`Regex.matches()`. That is a *full-region* match, while a JSON Schema `pattern` is applied with
*find* semantics. The test therefore evaluates a **stricter** reading of the pattern than the schema
itself applies, and cannot see a class of defect that would make the real schema more permissive.
Proven, not theorised: deleting the `^` anchor leaves the table test green.

That the suite still went red is luck of coverage, not design — the failure came from
`schemaVersionRejectsTrailingAndLeadingWhitespace`, which hardcodes eleven strings. The 17-row table
is the actual contract and it is the thing not being checked against real behaviour.

## Architecture

| Concern | Assessment |
|---|---|
| Layer order as data, not prose | **Good.** `layerOrder` sits in `diagnostics.json` beside the `layers` map it describes, so one concept stays in one published artifact. `layerOrderCoversEveryRegisteredLayer` closes both directions, which matters because an unordered layer would make `rejectingLayerIndex` return `null` and silently disable the invariant — a gap that presents as a passing test. |
| Splitting `schemaValid()` from reachability | **Good, and better than the research note's proposal.** The plan records why: deriving the schema check purely from layer order would have stopped testing the structural-validity claims that `oversized` and `version-major-2` make in their own notes. Two questions, two mechanisms. |
| `schemaValid` as a hand-written declaration | **Acceptable.** It is the same shape as the existing `parses` flag: declared by the fixture, asserted against reality, defaulted so no existing file changed. It would be a problem if it were *trusted*; it is not — see the negative control in TASK-5. |
| Decision table over document fixtures | **Good.** Version handling is a decision on a scalar. Seventeen near-identical manifests would have cost more and asserted less, and the table expresses the grammar-vs-policy split that documents cannot. |
| `versions.json` placement | **Correct.** A registry beside `diagnostics.json`, not a manifest under `fixtures/`. `SpecCorpus.fixtures` globs only `valid/` and `invalid/`, so no exclusion was needed. |
| Scope discipline | **Held, with one deliberate widening.** TASK-3 grew to fix all six schema patterns rather than one. Recorded in the tasklist with its reasoning rather than slipped in. `<link rel="siteskin">` was explicitly *not* implemented despite `ADR-002` parking it here. |
| Test-only surface | **Confirmed.** Nothing in `siteskin-core/src/main` or `:app` changed; `git diff --stat` on the range touches `spec/`, `docs/`, `CLAUDE.md` and `src/test` only. |

## Security

| Property | Assessment |
|---|---|
| Trailing-newline bypass | **Fixed, and this is the ticket's most valuable output.** Every `^…$` pattern in the published schema accepted a trailing newline, because `java.util.regex`'s `$` matches before a final line terminator while the ECMA-262 semantics JSON Schema cites do not. `"schemaVersion": "1.0\n"` validated. Fixed across all six patterns with `(?![\s\S])`, guarded structurally *and* behaviourally. |
| Version-string ambiguity | **Fixed.** `01.0`/`1.0` were two spellings of one version in a field that keys the manifest cache (`origin + schemaVersion`). Fixed in the grammar rather than by read-time normalization, which would be a second place for the two spellings to reappear. |
| Version layer as a control | **Sound.** An unsupported major rejects before structural interpretation, and `version-major-2-alien` is the evidence rather than the claim. The prohibition on reporting `SS-E-VERSION-UNSUPPORTED` for an absent version is stated explicitly, closing the misreading the old ordering invited. |
| Allow-list framing | **Sound.** `supportedMajors` is an allow-list; the `10.0` row exists specifically to fail an implementation that special-cases `2.x` as a deny-list. |
| Security carve-out | **Correctly framed.** Written as an exception to the breaking-change rules rather than by redefining security fixes as non-breaking, and bounded by four conditions. A policy that reclassifies its own inconvenient cases would not survive first use. |
| No new trust surface | **Confirmed.** No new URL-bearing field, no new fetch, no origin-binding change. The one place the version escapes its document — the cache key — is the constraint that drove the grammar fix, and is recorded in `SPEC.md` for `NET-002` to inherit. |
| Reserved code discipline | **Sound.** `SS-W-FIELD-DEPRECATED` is named in prose and absent from the registry, so `everyRegisteredCodeHasAFixture` keeps guarding the rule. Registering it early fails the build; registering it *with* a fixture passes, which is the intended path. |

## Findings

### FINDING-1 · Medium · test asserts a stricter grammar than the schema applies
**File:** `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpus.kt:116` (`schemaVersionGrammar`),
consumed at `SpecCorpusTest.kt` (`versionTableMatchesTheSchemaGrammar`)

`spec/versions.json`'s `wellFormed` column is checked with `Regex.matches()`, a full-region match.
A JSON Schema `pattern` is not applied that way — it is a *find*, so a pattern lacking `^` matches
anywhere in the string. The test consequently validates the table against a stricter reading than
the schema enforces, and is blind to any change making the real schema more permissive at the front
of the string.

Verified by deleting the `^` from `schemaVersion.pattern`: `versionTableMatchesTheSchemaGrammar`
**stayed green**. The suite went red only via `schemaVersionRejectsTrailingAndLeadingWhitespace`,
which hardcodes eleven strings — so the 17-row table, which is the contract this ticket added, was
not what caught it.

Current:
```kotlin
val matches = SpecCorpus.schemaVersionGrammar.matches(value)
if (matches == decision.wellFormed) { … }
```

Fix — drive the real validator instead of a regex reconstruction of it. This is strictly stronger
and, as a bonus, covers the `number` and `absent` forms the current test has to skip, since a type
failure and a `required` failure are things the validator knows about and a grammar does not:

```kotlin
val accepted = SpecCorpus.schemaAcceptsVersion(decision)   // builds a minimal doc, validates it
if (accepted == decision.wellFormed) { … }
```

`schemaVersionGrammar` then has no remaining consumer and should be deleted rather than left as an
attractive nuisance.

**Severity rationale:** medium, not high. The defect is in a *guard*, not in shipped behaviour — the
schema itself is correct today. It matters because the guard is the one this ticket added to stop
the schema and the table drifting, and a guard with a blind spot is worse than a known-absent one.

## Not findings

- **`versionTableCoversTheBoundary` hardcodes `listOf(1)` for `supportedMajors`.** Looks like
  duplicated policy. It is a deliberate canary: adding a supported major is exactly the kind of
  change that must be conscious, and `SPEC.md` §4.3(8) makes removing one breaking. A test that
  read the value from the file it is checking would assert nothing.
- **`diagnosticsDoNotCrossARejectingLayer` passes vacuously on the current corpus.** True, and
  recorded in the tasklist rather than papered over — no existing fixture pairs a `reject` with
  another diagnostic. It is a guard for future fixtures, and its negative control was performed by
  constructing a violation rather than by reordering the registry, which would not have failed.
- **`unknown-field-1.0` sits in `invalid/` while warning only.** Consistent with
  `showdomain-ignored`, which does the same. The buckets separate "a manifest a site would
  legitimately publish" from "a mistake or an attack"; a typo is a mistake.
- **`version-missing.expected.json` uses an empty-string pointer.** Correct JSON Pointer for "the
  root object is missing a required member", and parallel to `missing-required-site-id` pointing at
  `/site` for the same reason one level down.
- **Nothing cross-checks `SPEC.md` §4's prose table against `versions.json`.** Deliberate.
  `everyRegisteredCodeAppearsInSpec` sets the precedent that prose is checked by *containment*, not
  parsed — an assertion that survives someone reformatting the markdown.
- **`versions.json` carries both `registryVersion` and `schemaVersion`.** Matches
  `diagnostics.json`'s existing convention: the version *of the registry format* and the version *of
  the manifest schema it describes* are genuinely two things.

## Test coverage

| File | Tests | Coverage |
|---|---|---|
| `spec/diagnostics.json` (`layerOrder`) | `layerOrderCoversEveryRegisteredLayer`, `diagnosticsDoNotCrossARejectingLayer`, `parsesFlagAgreesWithTheLayerOrder` | Completeness both directions; short-circuit invariant (vacuous today, guards future fixtures); `parses` tied to the order |
| `spec/versions.json` | `versionTableMatchesTheSchemaGrammar`, `versionTableSeparatesGrammarFromPolicy`, `versionTableAcceptanceFollowsTheSupportedMajors`, `versionTableCoversTheBoundary`, `versionTableCodesAreRegistered` | 17 rows; grammar/policy split; coverage of the named boundary; code registration. **Grammar check weakened by FINDING-1.** |
| `spec/siteskin-1.0.schema.json` | `schemaPatternsAnchorAtEndOfInput`, `schemaVersionRejectsTrailingAndLeadingWhitespace` | Structural scan for a reintroduced bare `$`, plus behavioural probe through the real validator |
| 4 new fixtures | Whole corpus suite | Pairing, origin, disposition, reject-carries-no-result, schema verdict |
| `Fixture.schemaValid()` | `schemaLayerFixturesFailTheSchema`, `securityLayerFixturesPassTheSchema` | Both directions; negative control confirmed the declaration is asserted, not trusted |

Negative controls performed: 5 (TASK-1, TASK-3, TASK-4 ×2, TASK-5), all confirmed failing on the
right test with the right message, all restored. One planned control (TASK-1's registry reorder) was
found to be ineffective and replaced with a constructed violation rather than reported as passing.

## Verdict

**Sound. FINDING-1 fixed in `TASK-FIX-2` (see tasklist); this report is RESOLVED.**

The fix was verified by the same experiment that exposed the defect: with the leading `^` deleted,
`versionTableMatchesTheSchemaGrammar` previously stayed green and now fails with *"1.0.0: table says
wellFormed=false, but siteskin-1.0.schema.json accepts it"* — the validator's find-semantics locating
`1.0` inside `1.0.0`. It also picked up the two rows the regex could not speak to. No architectural or
security objection; the two schema defects found and fixed inside the ticket are worth more than the
policy prose it set out to write.

Environmental caveat carried to QA: `bash scripts/pre-commit-check.sh` cannot complete in this
container (no Android SDK, pre-existing — verified against a clean tree). `:siteskin-core:test` and
`detekt` are green.
