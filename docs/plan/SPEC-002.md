# SPEC-002: Implementation plan
Status: PLAN_APPROVED

## Overview

Three deliverables, in dependency order: a **layer model** the corpus can reason about, a
**version decision table** pinning the accept/reject boundary, and the **compatibility policy** in
normative prose. Everything else in the ticket hangs off one of those.

No Kotlin ships in `:siteskin-core/src/main`. This is a spec ticket; the only code is test-side
harness work in `siteskin-core/src/test`, which is where the corpus is executed.

### One refinement of the research note, recorded rather than absorbed

`docs/research/SPEC-002.md` § Findings 2 proposed deriving *everything* about the schema check from
layer ordering — a fixture rejected before the schema layer would simply not be schema-checked.
Working the file list through, that loses real coverage:

- `invalid/oversized.expected.json` states in its own note that the body "would pass
  `siteskin-1.0.schema.json`" — that claim is currently *tested*, and pure ordering would stop
  testing it.
- `invalid/version-major-2.expected.json` likewise rests on being structurally valid; that is the
  entire reason it demonstrates a policy rejection rather than a schema failure.

Both would become untested assertions in prose. So the plan splits the two questions the research
note conflated:

| Question | Answered by | Used for |
|---|---|---|
| Is this document structurally valid? | the schema, for **every** parsing fixture | keeps existing coverage |
| Would a browser ever *ask* the schema? | the layer order | the new short-circuit invariant |

A fixture that is deliberately structurally invalid *without* expecting `SS-E-SCHEMA-INVALID` — of
which the alien `2.0` document is the first and, for now, only one — declares `"schemaValid": false`
in its expectation file. That is a hand-written declaration, which the research note argued against
for *reachability*; it is the right shape here because it states a property of the document and is
**asserted against the real schema** rather than trusted, exactly like the existing `parses` flag.
Layer order then earns its place on a genuinely new invariant instead of duplicating one.

## Flow

- **discovery** — untouched. No new fetch, no new endpoint. `ADR-002`'s parked
  `<link rel="siteskin">` idea stays unimplemented; it appears in the policy only as a worked
  example of an additive change, which resolves that ADR's dangling pointer to this ticket without
  widening scope.
- **validation** — the ordering becomes normative: `transport → parse → version → schema →
  security`. A manifest rejected at a layer is never evaluated by a later one. The `version` layer
  is redefined precisely: it runs on a **present, well-formed** `schemaVersion` and has no opinion
  on absence or malformation, which belong to the schema because the grammar is structural.
- **normalization** — unchanged. `SPEC.md` §12 is not touched.
- **UI state** — not engaged.

## Data

### Trust boundary

Unchanged by this ticket, and that is worth stating rather than assuming: `schemaVersion` is a
scalar inside an already-untrusted document. No version string grants a capability, relaxes a
security rule, or alters which origin a manifest binds to. The single version-driven outcome
available to a site is a whole-manifest rejection of its own manifest.

The one place the version escapes its document is the manifest cache key — `origin + schemaVersion`
(`DEVELOPMENT_PLAN` decision 8, implemented by `NET-002`). This constrains the plan concretely:
**two distinct version strings must never denote one version**, or the cache key becomes ambiguous.
That is what forces the grammar fix into the schema rather than a read-time normalization of `01.0`
to `1.0`.

### Storage / cache keys

None introduced. The constraint above is recorded in `SPEC.md` so `NET-002` inherits it as a stated
requirement rather than rediscovering it.

## Security

- **Origin binding** — untouched; no new URL-bearing field.
- **Allow-lists** — the supported-major set is an allow-list of exactly `{1}`, stated as such. The
  policy text must make it impossible to read "reject `2.x`" as a deny-list that a `3.0` manifest
  would slip past.
- **Ordering as a control** — an unsupported major rejects *before* structural interpretation. The
  alien-`2.0` fixture is the evidence: a document whose shape is meaningless to `1.0` must be
  refused on its version alone, not produce a pile of schema errors that invite someone to "handle"
  them.
- **The security carve-out** — the breaking-change rules must not be able to trap a security fix
  behind a major bump. Written as an explicit exception (a security-motivated narrowing MAY ship in
  a minor), not by defining such changes as non-breaking. A policy that misdescribes a change in
  order to permit it will not survive its first real use.
- **Fallback on failure** — unchanged, `ADR-010`. Every path here ends in regular browser mode.
- **Anchoring** — the version grammar is matched with a full-region match against an anchored
  pattern. Trailing-whitespace and trailing-newline spellings are in the decision table
  deliberately: `$`-before-final-terminator is the classic way an anchored pattern lets `"1.0\n"`
  through, and a version check that can be bypassed by a trailing byte is a version check that is
  not a control.

## File-by-file plan

### New: `spec/versions.json`

The version decision table, beside `diagnostics.json` rather than under `fixtures/` — it is a
registry, not a manifest, and every entry in `fixtures/` is a document paired with an
`.expected.json`. (Research § Open questions.)

```jsonc
{
  "registryVersion": "1.0",
  "supportedMajors": [1],
  "currentMinor": 0,
  "note": "...",
  "decisions": [
    { "form": "string", "version": "1.0",  "wellFormed": true,  "decision": "accept" },
    { "form": "string", "version": "2.0",  "wellFormed": true,  "decision": "reject",
      "code": "SS-E-VERSION-UNSUPPORTED" },
    { "form": "string", "version": "1",    "wellFormed": false, "decision": "reject",
      "code": "SS-E-SCHEMA-INVALID" },
    { "form": "absent",                     "wellFormed": false, "decision": "reject",
      "code": "SS-E-SCHEMA-INVALID" }
  ]
}
```

`form` is `string` | `number` | `absent`, so the table can carry the two non-string cases a plain
string list cannot. Coverage: `1.0`, `1.1`, `1.999`, `0.9`, `2.0`, `10.0`, `1`, `1.0.0`, `01.0`,
`1.00`, `v1.0`, `""`, `" 1.0"`, `"1.0 "`, `"1.0\n"`, a JSON number, and absence.

### New: `spec/fixtures/invalid/version-missing.{json,expected.json}`

`schemaVersion` absent. `SS-E-SCHEMA-INVALID`, reject. The backlog asks for this document-shaped
even though the table covers the scalar — the two are complementary, and this one proves the
`required` keyword is actually doing the work end to end.

### New: `spec/fixtures/invalid/version-malformed.{json,expected.json}`

`"schemaVersion": "1"`. `SS-E-SCHEMA-INVALID`, reject.

### New: `spec/fixtures/invalid/version-major-2-alien.{json,expected.json}`

A `2.0` manifest whose structure is alien to `1.0` — `site` replaced by a differently-shaped object,
so it genuinely fails the `1.0` schema. Expects **only** `SS-E-VERSION-UNSUPPORTED`, and declares
`"schemaValid": false`. This is the fixture the ordering exists for: it can only be correct if the
version layer precedes the schema.

### New: `spec/fixtures/invalid/unknown-field-1.0.{json,expected.json}`

An unrecognised field in a `1.0` document (a plausible typo, not a `1.1` feature), warning with
`SS-W-FIELD-UNKNOWN` and surviving into a canonical result. Pins that the unknown-field policy is
version-independent — `forward-compat-1.1` alone leaves open the reading that it is a courtesy
extended to future minors only.

### Modified: `spec/diagnostics.json`

- Add `"layerOrder": ["transport", "parse", "version", "schema", "security"]` beside the existing
  `layers` map. One registry, not two.
- Correct the `version` layer description so it agrees with `SPEC.md` §4: it evaluates a present,
  well-formed version string; absence and malformation are the schema's.

### Modified: `spec/siteskin-1.0.schema.json`

`schemaVersion.pattern` `^[0-9]+\.[0-9]+$` → `^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$`, so `01.0` and
`1.0` are not two spellings of one version. Description updated to say why. No other schema change.

### Modified: `spec/SPEC.md`

§4 grows from one table to the versioning *policy*, with subsections:

- **§4.1 Layer ordering** — the five layers, in order, and the short-circuit rule. States the
  `version`/`schema` split and why the two codes are not one.
- **§4.2 What may change in a minor** — the enumerated non-breaking list, with
  `<link rel="siteskin">` discovery as the worked example.
- **§4.3 What forces a major** — the enumerated breaking list, and the security carve-out.
- **§4.4 Deprecation** — the lifecycle; `SS-W-FIELD-DEPRECATED` named as reserved and explicitly
  not registered, with the reason.
- **§4.5 The free-change window** — one dated paragraph recording that the `schemaVersion` grammar
  was tightened before the promise began binding, so a later reader does not conclude the policy was
  violated at birth.

`Status: SPEC_READY` is retained. The §11 diagnostics table is untouched — this ticket registers no
new code.

### Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpus.kt`

- Read `layerOrder`; expose `layerIndex(code)` and `rejectingLayerIndex(fixture)`.
- Add `Fixture.schemaValid: Boolean`, defaulting to `!expectsSchemaFailure(fixture)` so no existing
  expectation file changes.
- Read `spec/versions.json` into a `VersionDecision` list.
- Extract the schema's `schemaVersion` pattern from the schema file itself, so the decision table is
  checked against the published grammar rather than against a copy of it.

### Modified: `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`

New tests, and two existing ones re-expressed:

- `securityLayerFixturesPassTheSchema` — filter becomes `schemaValid` rather than
  `!expectsSchemaFailure`, so `oversized` and `version-major-2` keep their coverage and the alien
  fixture is excluded by its own declaration.
- `schemaLayerFixturesFailTheSchema` — extended to cover every fixture declaring `schemaValid:
  false`, not only those expecting `SS-E-SCHEMA-INVALID`.
- `diagnosticsDoNotCrossARejectingLayer` *(new)* — the short-circuit invariant. A fixture whose
  manifest is rejected at layer N must expect no diagnostic from a later layer. Passes on the
  current corpus unchanged.
- `parsesFlagAgreesWithTheLayerOrder` *(new)* — a fixture declaring `parses: false` must expect a
  rejecting diagnostic at `parse` or earlier, tying the two representations together.
- `layerOrderCoversEveryRegisteredLayer` *(new)* — completeness in both directions, matching the
  registry's existing style.
- `versionTableMatchesTheSchemaGrammar` *(new)* — the load-bearing one. Every `wellFormed: true`
  entry matches the schema's pattern and every `wellFormed: false` entry does not; asserted with a
  full-region match so a trailing newline cannot slip past an anchored pattern.
- `versionTableSeparatesGrammarFromPolicy` *(new)* — every `SS-E-VERSION-UNSUPPORTED` entry is
  well-formed and every `SS-E-SCHEMA-INVALID` entry is not. This is what proves the two codes are
  distinct layers rather than one check wearing two labels.
- `versionTableCoversTheBoundary` *(new)* — the PRD's named cases are all present, asserted
  individually rather than by counting.
- `versionTableCodesAreRegistered` *(new)* — no invented codes, mirroring
  `everyFixtureCodeIsRegistered`.

## Tests

Executed by `:siteskin-core:test` under `ANDROID_HOME`-unset, as today. The corpus directory is
already declared via `inputs.dir`, so `spec/versions.json` is picked up with no build change.

**Negative controls** (`CLAUDE.md` § Testing — recorded in the tasklist with their results):

1. Revert the schema pattern to `^[0-9]+\.[0-9]+$` → `versionTableMatchesTheSchemaGrammar` must
   fail on `01.0`/`1.00`. Proves the table is checked against the real grammar.
2. Move `version` after `schema` in `layerOrder` → the alien fixture's premise breaks and
   `diagnosticsDoNotCrossARejectingLayer` must react. Proves the order is read, not decorative.
3. Flip the alien fixture to `"schemaValid": true` → `securityLayerFixturesPassTheSchema` must fail.
   Proves the declaration is asserted rather than trusted.
4. Change one `SS-E-VERSION-UNSUPPORTED` entry to `wellFormed: false` →
   `versionTableSeparatesGrammarFromPolicy` must fail.

**Deferred, and named rather than skipped quietly:** nothing here executes a version layer, because
`:siteskin-core` has none yet — the same deferral `SPEC-001` made for the security layer.
`spec/versions.json` is authored in the shape `CORE-003` consumes unchanged, and `CORE-003` is where
the table stops pinning intent and starts pinning behaviour.

## Rollout / versioning

The schema stays `1.0` and `SPEC.md` stays `SPEC_READY`. The grammar tightening is the ticket's one
substantive format change and is taken deliberately inside the free-change window §4.5 documents —
after this ticket merges, the same change would require a major bump under the rules this ticket
itself writes. Nothing outside the repo consumes the corpus yet: no `$id`, no deployed manifest,
`siteskin-lint` unbuilt until `SPEC-003`.

## Open questions

Resolved from `docs/research/SPEC-002.md` § Open questions, recorded here rather than left open:

- **Layer order lives in `diagnostics.json`.** It is the registry that already declares the layers;
  a second file for one array would split one concept across two published artifacts.
- **The decision table lives at `spec/versions.json`, not under `fixtures/`.** `fixtures/` holds
  manifests with `.expected.json` siblings; a scalar table there would be the only entry that is
  neither, and `SpecCorpus.fixtures` would need an exclusion to skip it.
- **Deprecation lifetime is expressed in versions only, not in time.** "Until the next major" with
  no calendar guarantee is weaker than it sounds, and the honest response is to say so in the spec
  rather than to invent a duration against a release cadence this project does not have yet. Revisit
  when there is one.

Genuinely still open, carried forward:

- Whether `supportedMajors` should ever hold more than one entry — a browser supporting both `1.x`
  and `2.x` during a transition. Modelled as an array so the answer can be yes without a format
  change, but no policy is written for it; that is `2.0`'s problem and writing it now would be
  guessing.
