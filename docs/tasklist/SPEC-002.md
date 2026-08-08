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

## Tasks

- [ ] **TASK-1: layer ordering in the registry, and the short-circuit invariant**
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

- [ ] **TASK-2: the layer ordering becomes normative**
  - Modified: `spec/SPEC.md` — new §4.1 stating the five layers in order, the short-circuit rule,
    and the `version`/`schema` split with the reason the two codes are distinct.
  - Acceptance: `SPEC.md` and `diagnostics.json` no longer contradict each other on where an absent
    `schemaVersion` is caught. A reader implementing from `diagnostics.json` alone orders the checks
    correctly.
  - Tests: `everyRegisteredCodeAppearsInSpec` and `specDeclaresItselfReady` still green (§4 edits
    must not disturb `Status: SPEC_READY`).

- [ ] **TASK-3: tighten the `schemaVersion` grammar inside the free-change window**
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

- [ ] **TASK-4: the version decision table**
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

- [ ] **TASK-5: the document fixtures**
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

- [ ] **TASK-6: the compatibility policy**
  - Modified: `spec/SPEC.md` — §4.2 what may change in a minor (with `<link rel="siteskin">` as the
    worked example, resolving `ADR-002`'s dangling pointer without implementing it), §4.3 what
    forces a major plus the security carve-out, §4.4 the deprecation lifecycle with
    `SS-W-FIELD-DEPRECATED` reserved and explicitly unregistered.
  - Acceptance: PRD criteria 3 and 4 satisfied; the rules are enumerated and mechanical, not
    "use judgement". The carve-out is stated as an exception, not by redefining a breaking change
    as non-breaking.
  - Tests: `everyRegisteredCodeAppearsInSpec` green — and `SS-W-FIELD-DEPRECATED` must **not** be in
    `diagnostics.json`, so `everyRegisteredCodeHasAFixture` stays satisfied.

- [ ] **TASK-7: close out**
  - Modified: `docs/BACKLOG.md` — mark `SPEC-002` done, note that the `1.0`/`1.1`/`2.0` fixtures the
    entry asked for were already delivered by `SPEC-001` and what was added instead.
  - Modified: `CLAUDE.md` — a short note under the corpus section on the layer model and
    `spec/versions.json`, since a future session reads that file and not this tasklist.
  - Modified: `docs/adr/README.md` — `ADR-002`'s "`SPEC-002`" pointer for `<link rel="siteskin">`
    retargeted, so it does not read as unfinished work in this ticket.
  - Acceptance: `bash scripts/pre-commit-check.sh` passes; no stale forward-reference to this ticket
    remains for work it did not do.

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
