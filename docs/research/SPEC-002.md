# SPEC-002: Research
Status: RESEARCH_READY

## Question

`SPEC-001` answered *how a reader treats a version*. This ticket must answer *how the format is
allowed to change*, and the plan cannot commit to a file list until three things are settled:

1. Where the version decision sits relative to the schema, given that `spec/SPEC.md` §4 and
   `spec/diagnostics.json` currently disagree about it.
2. What shape the version corpus takes. Version handling is a decision on a **scalar**, not on a
   document, and the existing corpus is document-shaped — one manifest per rule.
3. Whether the compatibility promise can be written without freezing a grammar we would rather fix
   first.

## Origins involved

- **The serving origin** — unchanged from `SPEC-001`. Versioning adds no URL-bearing field and no
  new fetch. `schemaVersion` is a scalar inside a document already bound to its origin.
- **Asset origins** — none, and this ticket adds none.
- **Cross-origin exposure of the version itself** — the version participates in the manifest cache
  key (`origin + schemaVersion`, `DEVELOPMENT_PLAN` decision 8, implemented by `NET-002`). That is
  the one place the version escapes its document, and it is the reason the policy must say whether a
  minor bump is observable. It is: a site bumping `1.0`→`1.1` gets a distinct cache entry rather
  than a stale one. Nothing in this ticket may make two different version strings collide in that
  key, which rules out normalizing `01.0` to `1.0` at read time and pushes the fix into the grammar
  instead. **This is the finding that decided criterion 10.**

## Manifest-controlled surface

A site controls exactly one new thing: the version string it declares. That is a smaller surface
than it looks, and its limits are the point of the ticket.

- A site **cannot** obtain a capability by declaring a higher minor. `1.1` does not unlock a `1.1`
  field in a `1.0` reader — the reader does not have it. Declaring `1.999` gets the same treatment
  as declaring `1.0` plus a pile of `SS-W-FIELD-UNKNOWN`.
- A site **cannot** avoid a security rule by declaring a version. There is no version at which
  origin binding relaxes, and the breaking-change policy must be written so that no future version
  can be argued into one.
- A site **can** cause a whole-manifest rejection by declaring a major we do not know. That is the
  intended and only version-driven rejection.

## Browser-owned remainder

- **Which majors are supported.** Not negotiable by any field, and the rejection happens before the
  document is structurally interpreted.
- **The layer ordering itself.** A manifest cannot ask to be schema-checked before its version is
  considered, which is what makes an alien `2.0` document safe to refuse without parsing its shape.
- **The disposition of every version diagnostic** — `reject` for an unsupported major, as registered.
- **Whether a deprecated field still works.** The deprecation lifetime is a promise the browser
  keeps; a site cannot shorten or extend it.

## Relevant code

| Path | Why it matters |
|---|---|
| `spec/SPEC.md` §4 | The versioning section this ticket extends; also where the ordering contradiction lives |
| `spec/diagnostics.json` | `layers` declares transport/parse/version/schema/security but no *order* — the ordering is prose in the `version` entry and nothing reads it |
| `spec/siteskin-1.0.schema.json` | `schemaVersion` pattern `^[0-9]+\.[0-9]+$` — accepts `01.0`; deliberately does not pin the major |
| `siteskin-core/src/test/kotlin/.../spec/SpecCorpus.kt` | `expectsSchemaFailure` is the binary split that the alien-`2.0` fixture breaks; `bodyParses` is the hand-rolled short-circuit that layer ordering subsumes |
| `siteskin-core/src/test/kotlin/.../spec/SpecCorpusTest.kt` | `securityLayerFixturesPassTheSchema`, `schemaLayerFixturesFailTheSchema`, `malformedFixturesFailToParse` — the three tests that encode the current, incomplete layer model |
| `spec/fixtures/valid/{minimal,forward-compat-1.1}.json` | The `1.0` and `1.1` cases the backlog asks for; already present, not to be duplicated |
| `spec/fixtures/invalid/version-major-2.json` | The `2.0` case; deliberately *structurally valid*, which is why it does not test the ordering |
| `spec/fixtures/invalid/{oversized,malformed-json}.json` | The two existing pre-schema rejections; the evidence that the harness's layer model is already load-bearing and already ad hoc |

## Prior art

`ADR-007` (unknown action type drops an item; unknown major rejects the document — and it cites
`SPEC-002` as the owner of that rule) · `ADR-010` graceful fallback · `ADR-003` manifest-as-data ·
`SPEC-001`'s PRD risk *"over-specifying v1.0 makes 1.1 additions awkward"*, whose stated mitigation
was the unknown-field policy — this ticket is where that mitigation stops being a single sentence.
`DEVELOPMENT_PLAN` decisions 11 and 12 pre-commit dark variants and localization to `1.1`, so the
policy must make that arrival legal without a major bump; rows 8 (cache key) and 14 (`ADR-012`).

## Findings that changed the plan

Four, each of which moved a file into or out of the list.

1. **The `version`/`schema` contradiction is real and is not a wording slip.** `diagnostics.json`
   says the version layer runs "after parsing, before schema validation". `SPEC.md` §4 routes an
   *absent or malformed* `schemaVersion` to `SS-E-SCHEMA-INVALID`. If the version layer truly ran
   first, it would have to produce a diagnostic for a missing version itself. The resolution that
   keeps both true: the version layer runs on a **present, well-formed** version string and has no
   opinion on anything else — the grammar is structural and belongs to the schema, the *major
   policy* is not structural and cannot live there. That is a real distinction, not a compromise,
   and it is why the two codes are not one code. It must be stated in `SPEC.md`, because an
   implementer reading only `diagnostics.json` would order the checks wrongly and produce
   `SS-E-VERSION-UNSUPPORTED` for a manifest with no version at all.

2. **The corpus cannot express the ordering, and the gap is already there.** A fixture's expected
   diagnostics are matched against the schema by a binary rule: schema-layer codes must fail the
   schema, everything else must pass it. An alien `2.0` document belongs to neither class — it is
   rejected before the schema sees it, so the schema's verdict is *meaningless* rather than
   pass-or-fail. The same is already true of `malformed-json`, handled by a bespoke `parses` flag,
   and *accidentally* true of `oversized`, which passes the schema only because a 132 KB body of
   padding happens to be structurally valid JSON. Adding a third special case would be the moment
   to notice the pattern. **The plan therefore derives reachability from an ordered layer list in
   the registry**, and `bodyParses` becomes one consequence of that order rather than its own rule.
   This is a net simplification of `SpecCorpusTest`, not an addition to it.

3. **A version corpus should not be document-shaped.** Thirteen near-identical manifests differing
   only in one scalar would triple the fixture count while testing one field, and each would need an
   `.expected.json` asserting an origin that has nothing to do with the case. A single decision
   table at `spec/fixtures/versions.json` is denser, is directly consumable by `CORE-003` and
   `siteskin-lint`, and — the deciding argument — can assert something the document corpus cannot:
   that the *malformed* rejections fail the schema pattern while the *unsupported-major* rejections
   pass it. That assertion is the proof that the two codes are distinct layers rather than one check
   with two labels. Document fixtures are then added only for the two cases a scalar table genuinely
   cannot carry: the alien `2.0` structure, and an unknown field in a `1.0` document.

4. **`ADR-002` and `ADR-007` both forward-reference `SPEC-002`, and one of them is a scope trap.**
   `ADR-007`'s reference is this ticket's business — the unknown-major rule. `ADR-002`'s is not:
   it parks HTML `<link rel="siteskin">` discovery on "`SPEC-002`". Implementing that here would be
   scope creep into `NET-001`. But it is an excellent *worked example* of the policy — a new
   discovery mechanism is additive, degrades to `.well-known` in an older reader, and is therefore
   a minor. The plan uses it as the policy's illustration and leaves the mechanism unimplemented,
   which resolves the dangling pointer without widening the ticket.

## Risks

- **Tightening the `schemaVersion` pattern is itself a breaking change by the rules being written in
  the same commit** → the plan must state the free-change window explicitly in `SPEC.md` rather than
  letting the contradiction sit. Obligation: a dated sentence recording that the grammar was fixed
  before the promise began binding, so a future reader does not conclude the policy was already
  violated once.
- **The decision table pins intent, not behaviour** — nothing in this ticket executes a version
  layer, because there is not one yet. Obligation: named as deferred in the tasklist, and the table
  authored in the shape `CORE-003` will consume unchanged. The same deferral `SPEC-001` made for
  the security layer, made the same way rather than quietly.
- **Deriving reachability from a layer order could silently weaken existing assertions** — if the
  order is wrong, fixtures stop being schema-checked and the suite goes green while asserting less.
  Obligation: a negative control per `CLAUDE.md` § Testing, and an explicit assertion that the set
  of schema-checked fixtures does not shrink except for the two known pre-schema rejections.
- **A reserved-but-unregistered diagnostic code invites a future contributor to register it early**,
  ahead of any fixture, which breaks `everyRegisteredCodeHasAFixture` and looks like a harness bug
  rather than a policy violation. Obligation: the spec sentence reserving it says why it is absent.

## Open questions

Carried into `docs/plan/SPEC-002.md` § Open questions rather than resolved here:

- Whether the layer order belongs in `diagnostics.json` (alongside the `layers` map it already
  carries) or in a new file. It changes what `SpecCorpus` reads and is a structural choice about the
  published artifact, not just about the test.
- Whether `spec/fixtures/versions.json` sits under `fixtures/` at all, given it is not a manifest
  and the directory's every other entry is. The alternative is `spec/versions.json`, beside
  `diagnostics.json`, which is the other machine-readable registry.
- Whether the deprecation lifetime should be expressed in versions ("until the next major") or also
  in time. A major bump could in principle arrive a month later, which would make the guarantee
  worthless — but this project has no release cadence to anchor a duration to yet.
