# SPEC-001: Research
Status: RESEARCH_READY

> **Reconstructed after the fact.** `/researcher` became a gated workflow step *after* SPEC-001
> shipped, so this note did not inform the plan — it was written from the artifacts SPEC-001
> produced (`docs/prd/SPEC-001.prd.md`, `docs/plan/SPEC-001.md`, `spec/**`) to record what the
> ticket turned out to depend on. Read it as a map of the territory SPEC-001 covered, not as
> evidence that the territory was surveyed first. Every other ticket's research note is written
> before its plan.

## Question

What must be pinned down before a normative manifest format can be written, given that
`:siteskin-core` does not exist yet and the corpus written here is what `CORE-002..005` will be
implemented to satisfy.

The ordering constraint that dominates the ticket: nothing here may assume a Kotlin type, and
nothing here may be validated *by* the code it is meant to constrain.

## Origins involved

- **The serving origin** — `https://{origin}/.well-known/siteskin.json` (`ADR-002`). Every
  URL-bearing field in the format is resolved or checked relative to it; there is no field that
  opts out.
- **Asset origins** — none. `branding.logoUrl` is same-origin, and *subdomains are not the same
  origin* (`ADR-004`). `https://cdn.bloomflowers.example/logo.png` served from
  `https://bloomflowers.example` is a rejection, not a convenience.
- **External action targets** — `external_url` is the one field that may leave the origin, HTTPS
  only, and only behind a browser-owned confirmation (`BROWSE-005`).

A manifest does not carry its own origin, but almost every security rule is relative to one. That
is the finding that shaped the corpus layout: fixtures declare their origin in a sibling
`.expected.json`, and an origin-pair case is two fixtures sharing one manifest body.

## Manifest-controlled surface

Title, colours, a bounded logo slot, bottom-navigation items, and allow-listed quick actions —
all of it inert data until the security layer has passed it. Parsing success grants nothing.

## Browser-owned remainder

- Registrable domain and TLS indicator, always visible, browser typography, not suppressible by any
  field (`ADR-006`). `toolbar.showDomain` from the concept document is deliberately absent, and the
  corpus proves it is *ignored* rather than honoured (`invalid/showdomain-ignored.json`,
  `SS-W-FIELD-UNKNOWN`) — a sentence promising it would not have been evidence.
- The confirmation before any external navigation.
- Contrast correction: adjusts the *manifest-supplied* colour, never the browser-owned text colour.
- The disposition of every diagnostic. A site cannot influence whether its own mistake rejects the
  manifest or drops one item.

## Relevant code

| Path | Why it matters |
|---|---|
| `spec/SPEC.md` | The normative contract; trust model stated before any field description |
| `spec/siteskin-1.0.schema.json` | Structure only — the layer split below is the load-bearing decision |
| `spec/diagnostics.json` | Code registry: `layer`, `disposition`, summary. Makes "one fixture per code" machine-checkable |
| `spec/fixtures/{valid,invalid}/**` | The corpus; `.expected.json` siblings carry origin + expected diagnostics |
| `siteskin-core/src/test/kotlin/.../SpecCorpusTest.kt` | Executes the corpus under `ANDROID_HOME`-unset CI |
| `siteskin-core/build.gradle.kts` | `siteskin.spec.dir` + `inputs.dir` so a fixture edit invalidates the test |

## Prior art

`ADR-002` discovery · `ADR-003` data-never-code · `ADR-004` origin binding · `ADR-006`
browser-owned chrome · `ADR-007` allow-listed actions · `ADR-009` non-blocking discovery ·
`ADR-010` graceful fallback. `docs/DEVELOPMENT_PLAN.md` fixes the milestone ordering that puts the
spec before `CORE-*`.

## Findings that changed the plan

Three questions had to be answered before the file list, and each one changed what the fixtures
look like:

1. **A diagnostic's disposition is part of its definition.** The draft spec listed codes but not
   their effect, which made "rejected" untestable and hid a real inconsistency:
   `SS-E-ACTION-UNKNOWN` is an `E` code that per `ADR-007` deliberately does not reject. Rule
   settled: reject only when the document cannot be interpreted as a whole; anything narrower drops
   the offending element. Never infer disposition from the `E`/`W` prefix.
2. **Expected results are a canonical JSON projection, not a serialized Kotlin type.** Dumping
   `SiteSkinConfiguration` would invert the dependency — the contract defined by the code written to
   satisfy it — and would be useless to the second implementer the corpus exists for.
3. **The schema covers structure only.** Origin binding is not expressible in JSON Schema (it does
   not know the serving origin), and encoding the scheme allow-list there would duplicate a security
   control in a second language where it can silently drift. `securityLayerFixturesPassTheSchema`
   is the assertion that keeps the split honest.

## Risks

- **The corpus ossifies a mistake** → a fixture asserting wrong behaviour makes it a requirement.
  Plan's obligation: `HARDEN-001` reviews the `invalid/` corpus against the threat model before it
  is frozen; the corpus versions with the schema.
- **Spec/implementation drift once `CORE-*` starts** → the corpus is executed by
  `:siteskin-core:test`, so drift is a red build rather than a stale document.
- **The security layer's behaviour cannot be tested here** — the thing that would drop a
  cross-origin URL does not exist yet. Named as deferred in the tasklist rather than quietly
  skipped; `CORE-003`/`CORE-004` extend `SpecCorpusTest` to execute those fixtures, and that is
  where negative controls become mandatory.
- **Over-specifying v1.0** → the unknown-field policy (ignore + warn) makes additive growth legal.

## Open questions

Carried into `docs/plan/SPEC-001.md` § Open questions rather than resolved here: the schema `$id`
(blocked on the hosting domain), the closed `icon` allow-list membership, and whether
`external_url` belongs in v1.0 at all.
