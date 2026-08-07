# SPEC-001: Implementation plan
Status: PLAN_APPROVED

References:
- PRD: `docs/prd/SPEC-001.prd.md`
- Draft spec: `spec/SPEC.md` (`Status: DRAFT` → becomes `SPEC_READY` here)
- `ADR-002` discovery · `ADR-004` origin binding · `ADR-006` browser-owned chrome ·
  `ADR-007` allow-listed actions · `ADR-010` graceful fallback

## Overview

SPEC-001 writes the contract, not the code. Three deliverables, in dependency order:

1. **`spec/SPEC.md`** — normative text, RFC 2119 keywords.
2. **`spec/siteskin-1.0.schema.json`** — JSON Schema 2020-12, structural validity only.
3. **`spec/fixtures/**`** — the conformance corpus, plus a diagnostic registry that makes
   "one fixture per code" a machine-checkable claim rather than a promise.

The ordering constraint that shapes everything below: **`:siteskin-core` does not exist yet.**
`CORE-002..005` are written to satisfy this corpus, so nothing here may assume a Kotlin type, and
nothing here may be validated *by* the code it is meant to constrain. What SPEC-001 can execute
today is corpus integrity and the schema layer. What it cannot execute, it must still specify
precisely enough that `CORE-004` has no latitude.

That gap is the main risk in this ticket and § Tests is mostly about closing it honestly rather
than papering over it.

---

## Three decisions this plan settles

These are not implementation details. Each one is a question `CORE-002..005` would otherwise
re-litigate, and each changes what the fixtures look like.

### 1. A diagnostic's *disposition* is part of its definition

`spec/SPEC.md` §9 lists codes but not what each one does to the manifest. That is a real gap: it
makes "rejected" untestable, and it hides an inconsistency already present in the draft —
`SS-E-ACTION-UNKNOWN` is an `E` code that per `ADR-007` explicitly does *not* reject the manifest.

Every diagnostic gets a declared disposition:

| Disposition | Meaning | Codes |
|---|---|---|
| `reject` | whole manifest discarded, regular browser mode | `SIZE-EXCEEDED`, `PARSE`, `VERSION-UNSUPPORTED`, `SCHEMA-INVALID` |
| `drop-item` | offending element removed, rest of the manifest applies | `ORIGIN-MISMATCH`, `SCHEME-DENIED`, `ACTION-UNKNOWN`, `ASSET-CROSS-ORIGIN`, `DUPLICATE-ID` |
| `warn` | nothing removed, diagnostic recorded | `LIMIT-TRUNCATED`, `CONTRAST-CORRECTED`, `FIELD-UNKNOWN` |

The rule behind the split: **reject only when the document cannot be interpreted as a whole.**
Anything narrower removes the offending element. A cross-origin `internal_url` is dangerous as an
*item*; once dropped, the remaining manifest is exactly as safe as one that never contained it, and
rejecting wholesale only punishes a sloppy site. Note this cannot be exploited by a hostile site —
the site authors the whole manifest, so "keep the good parts" and "reject everything" leave an
attacker with the same zero capabilities.

Consequences that must be spelled out in SPEC.md, because they are where drop-item gets awkward:

- Dropping every navigation item leaves navigation **empty, not absent** — SiteSkin mode still
  activates, the bottom bar does not render. It does not fall back to regular mode.
- `DUPLICATE-ID` drops the *later* occurrence, so the disposition is order-dependent and the spec
  must say so.
- Missing **required** fields (`schemaVersion`, `site.id`, `site.name`) are `SCHEMA-INVALID` →
  `reject`. Absence of a required field is not a droppable item.

`SS-E-SCHEMA-INVALID` is new in this plan — the draft table has no code for "failed the JSON
Schema", which every structurally malformed manifest needs.

### 2. Expected results are a canonical JSON projection, never a serialized Kotlin type

The PRD asks each `valid/` fixture to carry "the expected normalized result". The obvious
implementation — dump `SiteSkinConfiguration` to JSON — would make the corpus a mirror of our type,
and the corpus is supposed to be usable by *a different implementer* (PRD user story 2). It would
also invert the dependency: the contract would be defined by the code written to satisfy it.

So the expected output is a **canonical projection defined normatively in SPEC.md § Normalization**,
with a fixed field order and fixed shape. `CORE-004` owes the corpus test a projection function
onto that form. If the projection is awkward to produce, that is information about the type, not a
reason to change the corpus.

### 3. Fixtures carry their origin

A manifest does not contain its own origin, but almost every security rule is relative to one —
`/catalog` resolving inside the origin, `https://cdn.other.example/logo.png` falling outside it. A
corpus of bare manifests cannot express any of that, so **every fixture's metadata file declares
the origin it was served from**. No default; omitting it is a corpus-integrity failure. Origin-pair
cases (a manifest valid at one origin and invalid at another) are then expressible as two fixtures
sharing one manifest body.

---

## Flow

The pipeline as *specified*. `CORE-002..005` implement each arrow; SPEC-001 fixes the order and the
diagnostic emitted at each stage.

```
bytes ──size guard──► JSON parse ──► DTO ──schema──► ──security──► ──normalize──► canonical result
   │                      │                   │            │              │
SIZE-EXCEEDED          PARSE          SCHEMA-INVALID   ORIGIN-MISMATCH  LIMIT-TRUNCATED
   │                      │          VERSION-UNSUPPORTED SCHEME-DENIED  CONTRAST-CORRECTED
 reject                reject              reject       ACTION-UNKNOWN  FIELD-UNKNOWN
                                                      ASSET-CROSS-ORIGIN
                                                        DUPLICATE-ID
                                                          drop-item
```

**Discovery.** `GET {origin}/.well-known/siteskin.json`, HTTPS only, `application/json`, ≤128 KB
enforced before parse, same-origin redirects ≤2 hops, concurrent with page load (`ADR-009`).
Normative text only — `NET-001` implements it.

**Validation.** Two layers, and the boundary matters because it decides what the JSON Schema is
responsible for:

- *Schema layer* — shape, types, required fields, enum membership, version format. Expressible in
  JSON Schema 2020-12 and therefore checkable at SPEC-001 time.
- *Security layer* — origin binding, scheme allow-list, asset origin, duplicate ids, limits,
  contrast. **Not expressible in JSON Schema**, and deliberately not attempted: a `pattern` regex
  encoding "same origin as the serving origin" is impossible (the schema does not know the origin)
  and one encoding scheme allow-listing would duplicate a security control in a second language
  where it can silently drift. The schema's job stops at structure.

This split is why PRD acceptance criterion 2 must be read as *"rejects every **structurally**
invalid fixture"*. Most `invalid/` fixtures are perfectly well-formed JSON that only the security
layer rejects. The metadata format below makes that explicit per fixture rather than leaving it to
a reader's judgement.

**Normalization.** URL resolution to absolute same-origin, colour parsing and contrast correction,
limit clamping with truncation, dropped-item removal, diagnostics collected in emission order.

**UI state.** Out of scope. `SKIN-001..003`.

---

## Data

### Trust boundary

SPEC-001 defines the boundary but instantiates neither side of it. What it fixes:

- The DTO layer is **inert** — parsing success grants nothing. SPEC.md states this before it
  describes a single field (PRD NFR: a reader who skims must not come away thinking the site is in
  control).
- The canonical projection is the *trusted* form. Every value in it has passed the security layer.
- The corpus is the evidence for that claim, which is why a rule with no fixture does not exist.

### Corpus layout

```
spec/
├── SPEC.md                       normative, Status: SPEC_READY
├── siteskin-1.0.schema.json      structural validity only
├── diagnostics.json              the code registry — disposition, layer, description
└── fixtures/
    ├── valid/
    │   ├── bloom-flowers.json            manifest body
    │   └── bloom-flowers.expected.json   { origin, result, diagnostics }
    └── invalid/
        ├── nav-cross-origin.json
        └── nav-cross-origin.expected.json  { origin, diagnostics }
```

Metadata lives in a `.expected.json` sibling rather than inside the manifest, because a fixture
body must be a **byte-exact** example of what a site would actually serve. A fixture carrying an
extra `_meta` key would be testing a manifest no site would ever publish — and would additionally
trip the unknown-field warning it is not trying to test.

`.expected.json` shape:

```json
{
  "origin": "https://bloomflowers.example",
  "note": "why this fixture exists — one line, for the human reading a failure",
  "diagnostics": [
    { "code": "SS-E-ORIGIN-MISMATCH", "disposition": "drop-item", "pointer": "/bottomNavigation/2" }
  ],
  "result": { }
}
```

- `result` is the canonical projection; **required for `valid/`, forbidden for `invalid/`** where
  the disposition is `reject`, required where every diagnostic is `drop-item` or `warn` (because
  something still renders and the corpus must pin down *what*).
- `pointer` is an RFC 6901 JSON Pointer into the manifest body. Diagnostics without a location
  (`SIZE-EXCEEDED`) omit it.
- `diagnostics` is order-sensitive and complete: PRD acceptance criterion 3 requires "exactly its
  expected codes, no more and no fewer", so an unexpected extra diagnostic is a failure.

### Storage / cache keys

Not this ticket. `NET-002` keys on `origin + schemaVersion`; SPEC-001 only fixes the TTL ceiling
(`min(Cache-Control, 24h)`) as normative text so the cache has something to conform to.

---

## Security

Per `PROJECT_RULES.md`, every website-controlled capability needs an explicit browser-owned
contract. **The origin-boundary implications, stated before the file list:**

### Origin binding

Every URL-bearing field in the format is origin-relative or origin-checked, with no exceptions and
no field that opts out:

| Field | Rule | Violation |
|---|---|---|
| `site.homeUrl` | resolves inside the serving origin | `ORIGIN-MISMATCH`, drop → falls back to `/` |
| `branding.logoUrl` | same-origin, no subdomains | `ASSET-CROSS-ORIGIN`, drop → monogram |
| `action.url` (`internal_url`) | resolves inside the serving origin | `ORIGIN-MISMATCH`, drop item |
| `action.url` (`external_url`) | HTTPS, any origin, browser confirms before leaving | `SCHEME-DENIED` if not HTTPS |
| `match[]` patterns | path-only; a pattern containing a scheme or authority is invalid | `SCHEMA-INVALID`, reject |

The cases the corpus must pin down, because each is a plausible resolver bug rather than a typo:
protocol-relative `//evil.example/x`, a traversal escaping the origin (`/../../evil`), a userinfo
authority (`https://bloomflowers.example@evil.example/`), a port change
(`https://bloomflowers.example:8443/`), and a subdomain (`https://cdn.bloomflowers.example/logo.png`)
— which is a *rejection*, per `ADR-004`, and is the one most likely to be "fixed" into a bug by a
future contributor who assumes subdomains are friendly.

Origin comparison is `scheme + host + port` exact-match after canonicalization. `CORE-001` owns
canonicalization; SPEC-001 owns the statement that comparison happens on the canonical form, so the
two tickets cannot disagree about ordering.

### Allow-lists, never deny-lists

Enumerated normatively and encoded as JSON Schema `enum`s, so an unlisted value is a schema
failure rather than a runtime surprise:

- Schemes: `https`, `mailto`, `tel`, `geo`. The corpus carries a denied fixture for each of
  `javascript:`, `file:`, `content:`, `intent:`, `data:` (PRD AC 5).
- Action types: the nine from `ADR-007`.
- Icon names: a closed set — an open icon field is a way to smuggle a resource reference.
- Asset MIME: `image/png`, `image/webp`. **No SVG** — scripting-capable, and the corpus needs a
  fixture proving refusal rather than a sentence promising it.

### Fallback on failure

`ADR-010`: every rejection path ends in regular browser mode with the page still rendering. The
spec states the fallback next to each rejection, and the corpus asserts the *disposition*, which is
what makes "falls back" a testable claim instead of a reassuring adjective.

### What the format deliberately cannot express

Stated in SPEC.md as a normative non-feature, so a future contributor reads it as a decision rather
than an oversight: no `showDomain` (`ADR-006`), no script or code of any kind (`ADR-003`), no
arbitrary intents, no permission requests, no cross-origin anything. A manifest that contains
`toolbar.showDomain` is not an error — the field is *ignored*, and the corpus contains a fixture
proving it is ignored rather than honoured, with `FIELD-UNKNOWN`.

---

## File-by-file plan

### New: `spec/diagnostics.json`

The registry. One entry per code: `code`, `layer` (`schema` | `security` | `transport`),
`disposition`, `summary`. Machine-readable so the corpus test can assert bidirectional
completeness — every registered code has ≥1 fixture, every fixture code is registered.

### Modified: `spec/SPEC.md`

Draft → normative. RFC 2119 keywords, `Status: SPEC_READY`. Restructured so the trust model comes
before any field description (PRD NFR). New sections: Normalization (the canonical projection),
Glob grammar for `match`, Contrast (WCAG AA, normative), and a Diagnostics table generated to agree
with `diagnostics.json`. The existing §5 "There is no `showDomain`" survives as-is — it is already
doing exactly the job PRD AC 6 asks for.

Two grammars must be nailed down here because `CORE-006` and `SKIN-001` otherwise invent them:

- **Glob** — restricted deliberately: `*` matches within one path segment, `**` matches zero or
  more whole segments, everything else is literal. No `?`, no character classes, no braces,
  anchored at path start. Rationale is already in the PRD (regex from an untrusted source invites
  catastrophic backtracking); the restriction also makes longest-match tie-breaking well-defined
  for `CORE-006`.
- **Contrast** — WCAG 2.2 relative luminance, AA: 4.5:1 body, 3:1 large text and UI. Correction
  adjusts the *manifest-supplied* colour, never the browser-owned text colour, in deterministic
  steps until the ratio is met. Determinism is the requirement that matters: a fixture cannot pin
  an expected corrected value otherwise.

### New: `spec/siteskin-1.0.schema.json`

JSON Schema 2020-12. Structure only, per the layer split above. `additionalProperties` stays
**true** — unknown fields are ignored with `FIELD-UNKNOWN` by design (`SPEC-002` forward
compatibility), so a schema that rejected them would contradict the versioning policy. `$id` is
omitted for now; see § Open questions.

### New: `spec/fixtures/valid/**`, `spec/fixtures/invalid/**`

Corpus. Minimum coverage from PRD AC 5, plus the origin cases enumerated in § Security above, plus
one fixture per registered diagnostic code.

`invalid/oversized.json` is committed at ~129 KB rather than generated at test time. Generating it
would leave a third-party implementer without the case, which defeats the corpus's purpose; 129 KB
of repeated padding costs nothing in git and sits well under the `check-added-large-files` limit.

### New: `spec/fixtures/valid/bloom-flowers.json`

The canonical Bloom Flowers manifest, matching mockup screen 3. `denrzv/bloom-flowers`'s
`.well-known/siteskin.json` is a **byte-identical copy** of this file, which resolves PRD AC 7
without a cross-repo build dependency: a checksum comparison in that repo's CI is enough, and the
copy direction is one-way (this file is the source).

### New: `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`

See § Tests. Lives in `:siteskin-core` because that is the module the corpus ultimately constrains,
and because it must run under `ANDROID_HOME`-unset CI.

### Modified: `siteskin-core/build.gradle.kts`

A test-only JSON Schema validator, and wiring so the test can find `spec/` from the repo root:

```kotlin
testImplementation(libs.json.schema.validator)

tasks.test {
    // The corpus lives at the repo root, not in module resources — it is shared with
    // :siteskin-lint and with denrzv/bloom-flowers, and it is a published artifact in
    // its own right. Declared as an input so a corpus edit invalidates the test.
    val specDir = rootProject.layout.projectDirectory.dir("spec")
    inputs.dir(specDir).withPropertyName("specCorpus")
    systemProperty("siteskin.spec.dir", specDir.asFile.absolutePath)
}
```

Test-only keeps it off the compile classpath, so `assertNoAndroidDependencies` and the dexed
artifact are both unaffected.

### Modified: `gradle/libs.versions.toml`

Add the validator. Preferred: `io.github.optimumcode:json-schema-validator` — kotlinx.serialization
based, so it adds no Jackson to a module whose whole point is a small, auditable dependency
surface. Confirm 2020-12 support and pin the current version at implementation time; fall back to
`com.networknt:json-schema-validator` if 2020-12 support is not there.

### Modified: `docs/ROADMAP.md`

Tick `SPEC-001`.

---

## Tests

Everything below runs in `:siteskin-core:test` with `ANDROID_HOME` unset (PRD AC 8's environment,
and the `core` CI job).

**Corpus integrity** — cheap, and it is what stops the corpus rotting into decoration:

1. Every fixture body has an `.expected.json` sibling, and vice versa.
2. Every `.expected.json` declares a syntactically valid `origin`.
3. Every code in every `.expected.json` is registered in `diagnostics.json`.
4. Every code in `diagnostics.json` appears in ≥1 fixture — this is PRD AC 3, mechanized.
5. Every code in `diagnostics.json` appears somewhere in `SPEC.md` (string containment; enough to
   catch a code added to the registry but never documented).
6. Every fixture body parses as JSON — except `invalid/malformed-*.json`, which are declared
   non-parsing in their expected file and asserted to fail.
7. `valid/` fixtures carry a `result`; `reject`-disposition `invalid/` fixtures do not.

**Schema layer** — the only *behavioural* layer SPEC-001 can execute:

8. Every `valid/` fixture validates against `siteskin-1.0.schema.json`.
9. Every `invalid/` fixture whose expected diagnostic has `layer: schema` fails the schema.
10. Every `invalid/` fixture whose expected diagnostic has `layer: security` **passes** the schema.
    This is the assertion that keeps the layer split honest: it fails loudly if someone later
    smuggles a security rule into the schema, where it would silently diverge from `CORE-004`.

**Bloom Flowers** — `valid/bloom-flowers.json` validates, and its `result` round-trips the
projection. PRD AC 7.

**Deferred, and named as deferred rather than quietly skipped:** the security layer's *behaviour*
(does a cross-origin URL actually get dropped) cannot be tested here, because the thing that would
drop it does not exist. `CORE-003` and `CORE-004` each extend `SpecCorpusTest` to execute the
security-layer fixtures. Until then those fixtures are asserted structurally (tests 1–10) and no
further. The tasklist records this explicitly so it is not mistaken for coverage.

**Negative controls** are not applicable to this ticket in the `PROJECT_RULES.md` sense — there is
no protection to revert, since no security code is written here. They become mandatory at
`CORE-004`, where every protection this spec describes gets one. The tasklist says so rather than
leaving a reviewer to wonder whether they were forgotten.

---

## Rollout / versioning

- Corpus and schema are versioned **with** the schema version: `siteskin-1.0.schema.json`,
  `fixtures/` describing 1.0. A 1.1 corpus is additive alongside, never an edit of 1.0's — editing
  a frozen fixture is how a conformance suite stops meaning anything.
- `SPEC-002` inherits the version-negotiation fixtures (`1.0`, `1.1`, `2.0`, missing, malformed)
  seeded here.
- Freeze order matters: `HARDEN-001` reviews the `invalid/` corpus against the threat model
  **before** it is treated as frozen (PRD risk 1). Until that review, a fixture may be corrected;
  after it, correcting one is a versioned change.
- No published schema URL until the domain exists — see below.

## Open questions

- **`$id` for the schema.** A `$id` pointing at a URL that 404s is worse than no `$id`, and the
  domain in `docs/DEVELOPMENT_PLAN.md` § Hosting is not purchased yet. Recommendation: omit `$id`
  now, add it in the hosting ticket alongside the privacy-policy origin. Resolve before any
  third-party implementer is invited to use the schema, since `$id` is how they would reference it.
- **`icon` allow-list contents.** The draft uses Material icon names (`home`, `grid_view`,
  `shopping_cart`, `person`, `call`). The set must be closed, but its membership is a product
  decision about which sites can be represented — bounded by what `SKIN-003` will ship. Propose the
  minimal set covering the four demos, and let `DEMO-002` widen it if a demo cannot be expressed.
- **Does `external_url` belong in v1.0 at all?** It is the only action that leaves the origin, it
  needs a confirmation UI that `BROWSE-005` owns, and no demo currently requires it. Keeping it
  costs a fixture and a confirmation flow; dropping it to 1.1 costs a breaking-ish addition later
  (additive, so legal under the versioning policy). Weak recommendation: keep it — the confirmation
  flow is needed for page-originated external navigation regardless, so the action reuses machinery
  rather than adding it.
