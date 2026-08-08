# SPEC-002: Versioning and compatibility policy
Status: PRD_READY

## Context / Problem

`SPEC-001` shipped `spec/SPEC.md` §4, which answers one question: *given a `schemaVersion` string,
does this manifest apply?* Accept `1.x`, reject `2.x`, ignore unknown fields. That is the reader's
half of versioning and it is done.

The writer's half is missing entirely. Nothing in the repo says what may change inside `1.x`, what
forces `2.0`, how a field is retired, or how long a site owner may rely on a field once it is
published. Those are not documentation gaps — they are the contract a site owner is being asked to
build against. A site that adopts SiteSkin is committing engineering effort to a format on the
promise that it will keep working, and right now that promise is unwritten.

The timing is the point. **This ticket is the last moment the format can change freely.** Nothing is
deployed: the schema has no `$id`, no site outside this repo serves a manifest, and the only
consumer of the corpus is the corpus's own tests. Once `SPEC-003` puts `siteskin-lint` in site
owners' hands and `DEMO-001` publishes a real manifest, every change starts costing someone else
something. Writing the compatibility promise *and* making the last free corrections under it, in
one ticket, is deliberate — the alternative is discovering the grammar was slightly wrong after it
became binding.

There is also a concrete, already-visible defect. `spec/SPEC.md` §4 routes an absent or malformed
`schemaVersion` to `SS-E-SCHEMA-INVALID` and an unsupported major to `SS-E-VERSION-UNSUPPORTED`,
while `spec/diagnostics.json` declares the `version` layer to run *before* the `schema` layer. Both
cannot be true as written. Resolving that ordering is a prerequisite for the fixtures this ticket
adds, not a tidy-up.

## Goals

- A normative **compatibility policy** in `spec/SPEC.md`: what a major bump means, what a minor bump
  means, and the enumerated tests for whether a proposed change is breaking.
- A normative **deprecation lifecycle**: how a field is retired, what a conforming browser does with
  a deprecated field, and the guaranteed lifetime a site owner gets.
- The **layer ordering** stated normatively — transport → parse → version → schema → security — with
  the `version`/`schema` split for `schemaVersion` resolved and testable.
- A **version decision table** at `spec/fixtures/versions.json`, pinning accept/reject and the
  resulting diagnostic for every version string at the boundary, exercised by `SpecCorpusTest`.
- Document-shaped fixtures for the cases the decision table cannot express: a `2.0` manifest whose
  *structure* is alien to `1.0`, and an unknown field in a `1.0` manifest.
- The `schemaVersion` grammar tightened so a version has exactly one spelling, taken now while
  tightening is still free.

## Non-goals

- Defining `1.1`'s content. Dark variants, localization and badges stay deferred exactly as
  `SPEC-001` left them; this ticket says how they may arrive, not what they are.
- Registering `SS-W-FIELD-DEPRECATED`. Nothing in `1.0` is deprecated, and this repo's rule is that
  a code with no fixture does not exist. The code name is **reserved** in the spec so the first real
  deprecation cannot pick a colliding one; it enters `diagnostics.json` in the same commit as the
  fixture that produces it. See § Open questions.
- Kotlin enforcement of any of this. `CORE-003` implements the version layer against the table this
  ticket writes.
- Signed manifests and key rotation — `ADR-012`.
- A patch component. `MAJOR.MINOR` is the whole grammar; see the policy for why editorial changes
  bump nothing.

## User stories

- As a site owner, I can read one section and know whether the field I am about to depend on can be
  taken away from me, and with how much notice.
- As a Webora maintainer proposing a schema change, I can decide *from the written rules* whether it
  is a minor addition or a `2.0`, without relitigating the question per change.
- As an implementer of a different browser, I can tell from the corpus alone which version strings
  my implementation must accept and which diagnostic each rejection must produce.
- As a site owner who typo'd a field name, my integration still works and my lint output tells me
  which field was ignored.

## Acceptance criteria

1. `spec/SPEC.md` §4 states the layer ordering — transport → parse → version → schema → security —
   and states that a manifest rejected at one layer is never evaluated by a later one.
2. `spec/SPEC.md` §4 resolves the `version`/`schema` split explicitly: a **well-formed** version
   string whose major is unsupported yields `SS-E-VERSION-UNSUPPORTED`; an **absent, non-string or
   malformed** `schemaVersion` yields `SS-E-SCHEMA-INVALID`. `spec/diagnostics.json`'s `version`
   layer description agrees with it.
3. `spec/SPEC.md` carries a **breaking-change** subsection enumerating, as testable rules, what
   forces a major bump and what may ship in a minor. It names the security carve-out explicitly: a
   change required to close a security hole may narrow behaviour within a minor.
4. `spec/SPEC.md` carries a **deprecation lifecycle** subsection: a field is marked deprecated in a
   minor, MUST keep working for the remainder of its major, and MAY be removed only at a major.
   `SS-W-FIELD-DEPRECATED` is named as reserved and not registered.
5. `spec/fixtures/versions.json` exists as a machine-readable decision table covering at minimum:
   `1.0`, `1.1`, `1.999`, `0.9`, `2.0`, `10.0`, `1`, `1.0.0`, `01.0`, `1.0 ` (trailing space), the
   empty string, a non-string value, and absence. Each entry declares the decision and, on
   rejection, the diagnostic code.
6. `SpecCorpusTest` executes that table: every accepted version matches the schema's
   `schemaVersion` pattern, every entry rejected as malformed fails it, and every entry rejected for
   its major *passes* the pattern — proving the two rejection reasons are genuinely distinct layers
   rather than one check wearing two labels.
7. `spec/fixtures/invalid/version-major-2-alien.json` is a `2.0` manifest that is structurally
   invalid against `siteskin-1.0.schema.json`, expecting **only** `SS-E-VERSION-UNSUPPORTED`. The
   corpus harness must not require it to pass the `1.0` schema — a document rejected at the version
   layer never reaches the schema.
8. The corpus harness derives "does this fixture reach the schema layer?" from the registry's layer
   ordering rather than from a hand-set flag, and the existing `parses` short-circuit is expressed
   through that same ordering rather than as a separate special case.
9. `spec/fixtures/invalid/unknown-field-1.0.json` shows an unrecognised field in a **`1.0`**
   manifest warning with `SS-W-FIELD-UNKNOWN` and surviving into a canonical result — pinning that
   the unknown-field policy is version-independent rather than a courtesy extended only to `1.1`.
10. `siteskin-1.0.schema.json`'s `schemaVersion` pattern rejects leading zeros, so `01.0` and `1.0`
    are not two spellings of one version. `spec/SPEC.md` records this as a change made before the
    compatibility promise began binding.
11. Every existing fixture and test still passes unchanged in meaning; no existing expected
    diagnostic is edited to accommodate the new ordering.
12. `bash scripts/pre-commit-check.sh` passes.

## NFR

- **Security/privacy:** the version layer is a security control, not a formality — it is what stops
  the browser interpreting a format it does not know. The policy must therefore state that an
  unsupported major rejects *before* any structural interpretation, and the alien-`2.0` fixture is
  the evidence rather than the claim. The breaking-change rules must not be able to trap a security
  fix behind a major bump; the carve-out in criterion 3 is what prevents the compatibility promise
  from becoming a reason to leave a hole open.
- **Reliability/fallback:** every rejection in the policy ends in regular browser mode, unchanged
  from `ADR-010`. A deprecated field never degrades to an error inside its major.
- **Performance:** the version check is a string comparison on an already-parsed scalar and runs
  before schema validation, so an unsupported major costs strictly less than a supported one. No
  fixture in this ticket is large enough to affect test runtime.
- **Accessibility:** not engaged. No user-visible surface changes.

## Risks

- **The policy is written to match what we already do, rather than what is right.** Mitigation: the
  ticket is explicitly the last free-change window, and criterion 10 exercises that freedom —
  tightening a grammar rather than grandfathering it. A policy ticket that changed nothing would be
  evidence the rules were reverse-engineered from the implementation.
- **Layer ordering changes the meaning of existing fixtures.** Mitigation: criterion 11. The
  ordering is derived from the registry the fixtures already declare, and `oversized` and
  `malformed-json` are the two that stop being coincidentally schema-valid — neither expectation
  file changes, only whether the harness asks the schema about them at all.
- **A decision table is easier to satisfy than to be right about.** A table can pin
  `"1"` → reject without anything proving the *browser* will reject it. Mitigation: stated as
  deferred, not hidden — `CORE-003` executes this table against the real version layer, and the
  table is authored so it can be consumed unchanged when it does.
- **Reserving a diagnostic code that never gets used** leaves a dangling name in the spec.
  Mitigation: it is one sentence, and the alternative — registering a code with no fixture — breaks
  `everyRegisteredCodeHasAFixture` and the rule it enforces.

## Open questions

- Should `SS-W-FIELD-DEPRECATED` be registered now with a synthetic fixture? **Decision: no.**
  Registering it would require inventing a deprecated `1.0` field to point at, which would make the
  registry assert something false about the format in order to satisfy a test. The name is reserved
  in prose; the code and its fixture land together when `1.1` first deprecates something.
- Is a security-motivated narrowing inside a minor a breaking change? **Decision: it is breaking by
  the mechanical test and permitted anyway**, recorded as an explicit exception rather than by
  quietly defining it as non-breaking. A policy that has to lie about the nature of a change to
  permit it will not survive its first real use.
- Does `0.9` reject as an unsupported major or as malformed? **Decision: unsupported major.** It is
  a well-formed `MAJOR.MINOR` whose major is not `1`, which is exactly the `SS-E-VERSION-UNSUPPORTED`
  case. Treating `0.x` as malformed would put a second rule in the grammar.
- Should the minor be used for anything beyond diagnostics? Carried to `docs/plan/SPEC-002.md` — it
  keys the manifest cache per `NET-002` (`origin + schemaVersion`), and the policy must not
  contradict a decision that ticket has already taken.
