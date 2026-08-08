# SPEC-002: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/SPEC-002.prd.md`
- Research: `docs/research/SPEC-002.md`
- Plan: `docs/plan/SPEC-002.md`

## Sequencing invariant

Same rule `SPEC-001` established: **a registry entry and the thing that exercises it land in the
same commit.** `spec/versions.json` and the tests that read it are one task, not two, because a
table nothing executes is a table nobody notices is wrong. No new diagnostic code is registered by
this ticket, so `everyRegisteredCodeHasAFixture` stays satisfied throughout.

Tasks are ordered by dependency: the harness must understand layers before a fixture can rely on
the ordering, and the grammar must be final before a table can be checked against it.

## Gate status in this environment

`bash scripts/pre-commit-check.sh` **cannot pass in the container this ticket was implemented in**,
and the failure is pre-existing rather than caused by any change here. Its `unit tests` step runs
`./gradlew test`, which includes `:app:testDebugUnitTest`, which needs an Android SDK — there is
none installed, no `ANDROID_HOME`, and no `local.properties`. Verified by stashing every change and
re-running against a clean tree: same failure, same step.

What *was* run to completion for every task below, and is green:

- `ANDROID_HOME= ANDROID_SDK_ROOT= ./gradlew :siteskin-core:test` — the corpus suite, which is the
  entirety of this ticket's executable surface
- `./gradlew detekt`
- `gitleaks` and `shellcheck` are absent from the container; the script warns and skips them, as it
  does on any machine without them. CI runs both.

`:app` compiles no code from this ticket — nothing here touches `siteskin-core/src/main` or `:app`.
The unrunnable step is therefore unrunnable, not failing. **This must be confirmed on a machine with
the SDK before the ticket is considered validated**, and `/qa` records it as an open environmental
caveat rather than a pass.

## Tasks

- [x] **TASK-1: layer ordering in the registry, and the short-circuit invariant**
  - Modified: `spec/diagnostics.json` — add `layerOrder`; correct the `version` layer description
    to match the split (`present, well-formed` version strings only).
  - Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpus.kt` — read
    `layerOrder`; `layerIndex(code)`, `rejectingLayerIndex(fixture)`; add `Fixture.schemaValid`
    defaulting to `!expectsSchemaFailure`.
  - Modified: `.../spec/SpecCorpusTest.kt` — `layerOrderCoversEveryRegisteredLayer`,
    `diagnosticsDoNotCrossARejectingLayer`, `parsesFlagAgreesWithTheLayerOrder`.
  - Acceptance: all three new tests pass against the **unchanged** corpus. No expectation file is
    edited in this task — if one needs editing, the invariant is wrong, not the fixture.
  - Tests: the three above, plus the existing suite green.
  - Negative control: reorder `layerOrder` so `security` precedes `parse`;
    `diagnosticsDoNotCrossARejectingLayer` must fail. Restore.
  - **Result:** ✅ 3 new tests pass; no expectation file edited; 29 tests green.
    **The planned negative control was wrong and was replaced.** Reordering `layerOrder` does not
    fail the suite, because `diagnosticsDoNotCrossARejectingLayer` is *vacuous on today's corpus* —
    no existing fixture pairs a `reject` with any other diagnostic, so there is no crossing to
    detect at any ordering. That makes it a guard for future fixtures rather than a test of current
    ones, which is legitimate but is not what a negative control proves.
    Replaced with a constructed violation: a scratch fixture expecting `SS-E-SIZE-EXCEEDED`
    (reject, transport) alongside `SS-W-ICON-UNKNOWN` (warn, security). Exactly one test failed —
    `diagnosticsDoNotCrossARejectingLayer`, reporting *"SS-W-ICON-UNKNOWN sits at layer 'security',
    after the rejection at 'transport' — that layer never runs"*. Scratch fixture removed, suite
    green again.
    Worth carrying forward: the vacuity is the reason `parsesFlagAgreesWithTheLayerOrder` was
    written to cover the two directions that *are* live on the current corpus. Its first draft also
    asserted "rejected at or before parse ⟹ `parses: false`", which is false — `oversized` is
    refused at the transport layer and its body is valid JSON by design. Caught before running.

- [x] **TASK-2: the layer ordering becomes normative**
  - Modified: `spec/SPEC.md` — new §4.1 stating the five layers in order, the short-circuit rule,
    and the `version`/`schema` split with the reason the two codes are distinct.
  - Acceptance: `SPEC.md` and `diagnostics.json` no longer contradict each other on where an absent
    `schemaVersion` is caught. A reader implementing from `diagnostics.json` alone orders the checks
    correctly.
  - Tests: `everyRegisteredCodeAppearsInSpec` and `specDeclaresItselfReady` still green (§4 edits
    must not disturb `Status: SPEC_READY`).
  - **Result:** ✅ §4.1 added with the layer table and the short-circuit rule stated normatively.
    The contradiction is resolved in both directions: `SPEC.md` now says the version layer runs only
    on a present, well-formed string, and `diagnostics.json` (TASK-1) says the same. Added a
    explicit prohibition — an implementation MUST NOT report `SS-E-VERSION-UNSUPPORTED` for a
    manifest declaring no version — because that is the exact error the old ordering invited.
    Also generalised the §4 table while there: `1.1` became `1.y` for any `y`, and `2.0` became
    "any other major", with the supported set named as an allow-list. The old table could be read
    as three special cases, which is precisely the deny-list reading `ADR-007` warns against.

- [x] **TASK-3: tighten the `schemaVersion` grammar inside the free-change window**
  - Modified: `spec/siteskin-1.0.schema.json` — pattern → `^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$`,
    description updated.
  - Modified: `spec/SPEC.md` — §4.5 recording the change and the window, so the policy written in
    TASK-6 is not read as having been violated at birth.
  - Acceptance: every existing fixture still passes/fails the schema exactly as before — the
    tightening must be invisible to the current corpus, since no fixture uses a leading zero.
  - Tests: `validFixturesPassTheSchema`, `schemaLayerFixturesFailTheSchema`,
    `securityLayerFixturesPassTheSchema` all green and unchanged in meaning.
  - Negative control: temporarily add `"schemaVersion": "01.0"` to a scratch copy of
    `valid/minimal.json`; it must fail the schema. (Scratch only — not committed.)
  - **Result:** ✅ Corpus unaffected, as predicted — the leading-zero narrowing changes the verdict
    on exactly `01.0` and `1.00` and nothing else, and no fixture uses either.
    **This task uncovered a second, sharper defect and was widened to fix it.** Probing the real
    validator rather than reasoning about the regex showed `"schemaVersion": "1.0\n"` was
    **accepted**. JSON Schema specifies ECMA-262, where an unflagged `$` matches only at end of
    input; `java.util.regex`'s `$` also matches before a *final line terminator*, so on a JVM
    validator every `^…$` pattern in the schema admitted a trailing newline. The leading-zero fix
    alone would not have closed it — `"1.0\n"` and `"01.0\n"` just move the ambiguity along.
    Fixed by anchoring with `(?![\s\S])`, which means "no character of any kind follows" and is
    identical under both engines. Applied to **all six** patterns, not just `schemaVersion`: the
    defect is in the anchoring idiom, not in one field. Verified through the validator, not by
    inspection.
    Scope note: fixing the other five patterns goes beyond TASK-3 as planned. Taken deliberately
    because §4.5 — written in this same task — closes the free-change window, and a narrowing
    deferred past it would need a `2.0` for what is a regex-engine bug. Recorded in `SPEC.md`
    §4.5(b) as a breaking change rather than reclassified as compliant.
    Negative control (both defects at once): reverting `schemaVersion` to `^[0-9]+\.[0-9]+$` failed
    exactly two tests — `schemaVersionRejectsTrailingAndLeadingWhitespace` (on `1.0\n`) and
    `schemaPatternsAnchorAtEndOfInput` (naming the offending pattern). Restored; 31 tests green.
    Two guards were added rather than one, because either alone is weak: the structural scan would
    pass a pattern that is anchored but wrong, and the behavioural probe covers only the one field
    it exercises.

- [x] **TASK-4: the version decision table**
  - New: `spec/versions.json` — `supportedMajors`, `currentMinor`, and the `decisions` array over
    `form` ∈ {`string`, `number`, `absent`}.
  - Modified: `.../spec/SpecCorpus.kt` — read the table; extract the schema's `schemaVersion`
    pattern from the schema file rather than restating it.
  - Modified: `.../spec/SpecCorpusTest.kt` — `versionTableMatchesTheSchemaGrammar`,
    `versionTableSeparatesGrammarFromPolicy`, `versionTableCoversTheBoundary`,
    `versionTableCodesAreRegistered`.
  - Acceptance: the table covers every case named in PRD criterion 5, and the grammar assertion runs
    against the pattern read out of `siteskin-1.0.schema.json`, not a copy.
  - Tests: the four above.
  - Negative controls: (a) revert the schema pattern to `^[0-9]+\.[0-9]+$` →
    `versionTableMatchesTheSchemaGrammar` must fail on `01.0`; (b) flip one
    `SS-E-VERSION-UNSUPPORTED` entry to `wellFormed: false` →
    `versionTableSeparatesGrammarFromPolicy` must fail. Restore both.
  - **Result:** ✅ 17 decisions; all four tests pass; 35 tests green.
    Both negative controls confirmed:
    (a) loosening the schema pattern back to `^[0-9]+\.[0-9]+$` failed
    `versionTableMatchesTheSchemaGrammar` with *"01.0: table says wellFormed=false, schema grammar
    says true"* — proving the table is checked against the published pattern and not a copy of it.
    It also re-failed TASK-3's two anchoring guards, which is the correct coupling.
    (b) flipping `2.0` to `wellFormed: false` failed `versionTableSeparatesGrammarFromPolicy` with
    *"rejects for its major but is not well-formed — the version layer never sees a malformed
    string"*, and independently failed the grammar check. Restored; green.
    Two notes on how the tests are written. `versionTableMatchesTheSchemaGrammar` deliberately skips
    the `number` and `absent` forms: their `wellFormed: false` is a `type`/`required` failure, and
    asking a *grammar* about them would be the wrong question answered correctly by luck.
    `versionTableSeparatesGrammarFromPolicy` recomputes acceptance as "well-formed AND major ∈
    supportedMajors" rather than trusting the table's own `decision` column, so the two have to
    agree instead of the test restating one of them.

- [x] **TASK-5: the document fixtures**
  - New: `spec/fixtures/invalid/version-missing.{json,expected.json}` — `SS-E-SCHEMA-INVALID`.
  - New: `spec/fixtures/invalid/version-malformed.{json,expected.json}` — `"1"`,
    `SS-E-SCHEMA-INVALID`.
  - New: `spec/fixtures/invalid/version-major-2-alien.{json,expected.json}` — `2.0`, structurally
    alien, `"schemaValid": false`, expects **only** `SS-E-VERSION-UNSUPPORTED`.
  - New: `spec/fixtures/invalid/unknown-field-1.0.{json,expected.json}` — `SS-W-FIELD-UNKNOWN` on a
    `1.0` document, with a canonical result.
  - Acceptance: the alien fixture is accepted by the harness *because* the version layer precedes
    the schema; it would be un-expressible under the pre-TASK-1 model.
  - Tests: full corpus suite; `fixtureBodiesAndExpectationsArePaired`,
    `rejectFixturesCarryNoResult`, `invalidFixturesDeclareAtLeastOneDiagnostic`,
    `everyExpectationDeclaresAnOrigin` all green.
  - Negative control: flip the alien fixture to `"schemaValid": true` →
    `securityLayerFixturesPassTheSchema` must fail. Restore.
  - **Result:** ✅ 4 fixtures (8 files); 35 tests green.
    Negative control confirmed: with `"schemaValid": true` the suite failed exactly one test,
    `securityLayerFixturesPassTheSchema`, reporting *"invalid/version-major-2-alien: /site: element
    is not a object"*. So the declaration is asserted against the real schema rather than trusted —
    and, incidentally, that failure is the proof the new fixtures are actually being discovered and
    exercised rather than sitting inert in the directory.
    Two existing tests were re-expressed to use `schemaValid()` rather than
    `!expectsSchemaFailure()`. `securityLayerFixturesPassTheSchema` keeps `oversized` and
    `version-major-2` in scope — both rejected before the schema runs, both claiming structural
    validity in their own notes — while excluding the alien fixture by its own declaration.
    `schemaLayerFixturesFailTheSchema` widens correspondingly, so a fixture cannot declare itself
    malformed and then quietly satisfy the schema.
    `unknown-field-1.0` carries an unknown *object* (`analytics`) as well as a misspelled scalar,
    on purpose: an implementation tempted to treat an unrecognised object as "structure we do not
    understand" and reject it fails on that field and not on the typo.

- [x] **TASK-6: the compatibility policy**
  - Modified: `spec/SPEC.md` — §4.2 what may change in a minor (with `<link rel="siteskin">` as the
    worked example, resolving `ADR-002`'s dangling pointer without implementing it), §4.3 what
    forces a major plus the security carve-out, §4.4 the deprecation lifecycle with
    `SS-W-FIELD-DEPRECATED` reserved and explicitly unregistered.
  - Acceptance: PRD criteria 3 and 4 satisfied; the rules are enumerated and mechanical, not
    "use judgement". The carve-out is stated as an exception, not by redefining a breaking change
    as non-breaking.
  - Tests: `everyRegisteredCodeAppearsInSpec` green — and `SS-W-FIELD-DEPRECATED` must **not** be in
    `diagnostics.json`, so `everyRegisteredCodeHasAFixture` stays satisfied.
  - **Result:** ✅ §§4.2–4.4 added; 35 tests green, detekt green. `SS-W-FIELD-DEPRECATED` appears
    once in `SPEC.md` and zero times in `diagnostics.json`, as intended.
    No new test was added to protect the reservation, because the existing
    `everyRegisteredCodeHasAFixture` already does it exactly right: registering the code early fails
    the build for want of a fixture, while registering it *together with* a fixture is the intended
    path and correctly passes. A dedicated "this code must stay unregistered" test would have
    blocked the legitimate case.
    Two things the policy says that are worth not losing. The breaking-change rules end with the
    security carve-out written as an **exception** — such a change genuinely is breaking, and a
    policy that reclassifies its own inconvenient cases as compliant would not survive first
    contact; it is bounded instead by four conditions (narrowest fix, recorded, fixtured, degrades
    gracefully). And §4.4 states the honest limit of the deprecation guarantee: it is expressed in
    versions, not time, which is only a strong promise if majors are rare — and this format has no
    release cadence yet to anchor a duration to. Saying so beats implying a calendar commitment.

- [ ] **TASK-7: close out**
  - Modified: `docs/BACKLOG.md` — mark `SPEC-002` done, note that the `1.0`/`1.1`/`2.0` fixtures the
    entry asked for were already delivered by `SPEC-001` and what was added instead.
  - Modified: `CLAUDE.md` — a short note under the corpus section on the layer model and
    `spec/versions.json`, since a future session reads that file and not this tasklist.
  - Modified: `docs/adr/README.md` — `ADR-002`'s "`SPEC-002`" pointer for `<link rel="siteskin">`
    retargeted, so it does not read as unfinished work in this ticket.
  - Acceptance: `bash scripts/pre-commit-check.sh` passes; no stale forward-reference to this ticket
    remains for work it did not do.

- [x] **TASK-FIX-1: split `versionTableSeparatesGrammarFromPolicy`**
  - Source: `detekt` gate, run during TASK-6's pre-commit. Not a `/review` finding.
  - Modified: `.../spec/SpecCorpusTest.kt` — the acceptance recomputation moves into its own
    `versionTableAcceptanceFollowsTheSupportedMajors`.
  - Cause: the test landed in TASK-4 at cyclomatic complexity **10** against a threshold of 10.
    I ran `detekt` after TASK-3 and then not again until TASK-6, so TASK-4 and TASK-5 were committed
    without it. The gate did its job; my sequencing did not. Running the full gate per task, rather
    than the test task per task, is the correction.
  - Acceptance: `detekt` green; the two assertions keep their distinct failure messages rather than
    being merged into one.
  - **Result:** ✅ 36 tests green, detekt green. The split is also the better shape — one test asks
    whether the two rejection codes stay on their own sides of the grammar line, the other asks
    whether acceptance matches the supported-major allow-list, and they fail for different reasons.

## Deferred

Named here rather than skipped silently, following `SPEC-001`'s precedent:

- **Nothing in this ticket executes a version layer.** `:siteskin-core` has no validator yet, so
  `spec/versions.json` pins intent, not behaviour. `CORE-003` executes the table against the real
  implementation; the table's shape is fixed now so that consumption needs no edit.
- **`SS-W-FIELD-DEPRECATED` is reserved, not registered.** It enters `diagnostics.json` in the same
  commit as the first fixture that produces it, which cannot exist until `1.1` deprecates something.
- **`<link rel="siteskin">` discovery** stays unimplemented; `ADR-002` parked it on this ticket, and
  this ticket demotes it to an illustration of the minor-version policy. The mechanism belongs to
  `NET-001`.
