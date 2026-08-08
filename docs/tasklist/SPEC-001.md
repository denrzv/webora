# SPEC-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/SPEC-001.prd.md`
- Plan: `docs/plan/SPEC-001.md`

## Sequencing invariant — read before starting TASK-1

`SpecCorpusTest` asserts **bidirectional completeness**: every code in `spec/diagnostics.json` has
at least one fixture, and every code used by a fixture is registered. That invariant is what makes
PRD acceptance criterion 3 mechanical instead of aspirational — but it also means a task that
registers a code without adding its fixture leaves the build red.

So **the registry grows with the fixtures, never ahead of them.** Each task below adds codes,
fixtures and the matching `SPEC.md` rows together. The registry is complete when TASK-6 lands, and
the build is green at every commit in between. Do not "scaffold the registry first" — it is the one
ordering that breaks this ticket.

## Note on `bash scripts/pre-commit-check.sh`

Per repo convention every task ends with the command gate. Be aware that the script runs
`./gradlew test` across **all** modules, which needs an Android SDK for `:app`. On a container
without one (see `BOOTSTRAP.md` § 6), run the core subset per task:

```bash
ANDROID_HOME= ANDROID_SDK_ROOT= ./gradlew :siteskin-core:test :siteskin-core:detekt
```

and run the full script once, on a machine with an SDK, before `/validate`. Record which of the two
you ran in the task. A task that claims the full gate on an SDK-less box is claiming something that
did not happen.

---

## Tasks

- [x] **TASK-1: Corpus harness, diagnostic registry, transport and parse fixtures**
  - **Done.** `:siteskin-core:test` 13 tests green (10 corpus + 3 pre-existing),
    `:siteskin-core:detekt` green, both with `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset. Core subset
    gate only — no Android SDK on this container.
  - *Deviation:* the plan named three layers (`schema` / `security` / `transport`); the registry
    ships **five** — `transport`, `parse`, `version`, `schema`, `security`. `parse` and `version`
    were folded into the others in the plan, which does not survive contact with the fixtures:
    `siteskin-1.0.schema.json` validates the *format* of `schemaVersion` but deliberately does not
    pin the major, so a `2.0` manifest passes the schema and is rejected by policy. Collapsing
    `version` into `schema` would have forced either a wrong code or a schema that pins the major
    and cannot be reused for 1.x.
  - *Deviation:* `io.github.optimumcode:json-schema-validator` **0.5.1** confirmed to support draft
    2020-12 — verified empirically by `jsonSchemaValidatorSupportsDraft2020_12`, which is committed
    rather than run once and forgotten, so a future version bump that drops 2020-12 fails there
    instead of obscurely inside a corpus assertion. No fallback to networknt needed.
  - *Added beyond the task:* `corpusIsDiscovered`. Every other test in the class is a "for all
    fixtures" assertion and passes vacuously against an empty list, so a broken `siteskin.spec.dir`
    would have turned the whole suite green while asserting nothing. Also
    `fixtureDispositionsMatchTheRegistry`, `validFixturesDeclareNoRejection` and
    `invalidFixturesDeclareAtLeastOneDiagnostic`, which close the same class of hole.
  - New: `spec/diagnostics.json` — registry format (`code`, `layer`, `disposition`, `summary`),
    seeded with `SS-E-SIZE-EXCEEDED`, `SS-E-PARSE`, `SS-E-VERSION-UNSUPPORTED` only
  - New: `spec/fixtures/invalid/oversized.{json,expected.json}` (~129 KB of padding — committed,
    not generated; see plan § File-by-file for why)
  - New: `spec/fixtures/invalid/malformed-json.{json,expected.json}` (declared non-parsing)
  - New: `spec/fixtures/invalid/version-major-2.{json,expected.json}`
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/spec/SpecCorpusTest.kt`
  - Modified: `gradle/libs.versions.toml` — add `io.github.optimumcode:json-schema-validator`,
    test-only. Verify it supports draft 2020-12 **before** pinning; fall back to
    `com.networknt:json-schema-validator` if not, and record which you used and why.
  - Modified: `siteskin-core/build.gradle.kts` — `testImplementation` the validator; declare
    `spec/` as a test input dir and pass it as `siteskin.spec.dir`
  - Acceptance:
    1. `SpecCorpusTest` discovers fixtures from `spec/`, not from module resources.
    2. Every fixture body has an `.expected.json` sibling and vice versa.
    3. Every `.expected.json` declares a syntactically valid `origin`.
    4. Registry and fixture codes agree in both directions.
    5. `reject`-disposition fixtures carry no `result` key.
    6. The validator is on the **test** classpath only — `:siteskin-core:check` still passes with
       `ANDROID_HOME` unset and `assertNoAndroidDependencies` still finds nothing.
    7. Core subset gate passes (see note above).
  - Tests: `fixtureBodiesAndExpectationsArePaired`, `everyExpectationDeclaresAnOrigin`,
    `everyFixtureCodeIsRegistered`, `everyRegisteredCodeHasAFixture`,
    `rejectFixturesCarryNoResult`, `malformedFixturesFailToParse`

- [x] **TASK-2: `spec/SPEC.md` → normative, `Status: SPEC_READY`**
  - **Done.** Core subset gate green. `everyRegisteredCodeAppearsInSpec` and `specDeclaresItselfReady`
    added to `SpecCorpusTest`.
  - *Deviation:* added `SS-W-ICON-UNKNOWN`, a 14th code the plan did not anticipate. Writing §7
    forced the question the plan skipped: if `icon` is allow-listed and the allow-list is encoded as
    a JSON Schema `enum`, then a typo'd icon name is `SCHEMA-INVALID` and kills the whole
    integration — which directly contradicts the reasoning `ADR-007` gives for unknown *action*
    types. Resolved by not enumerating icons in the schema at all. The schema constrains `icon` to
    `^[a-z][a-z0-9_]{0,31}$`, which is what actually delivers the security property (an icon field
    structurally cannot carry a URL or any resource reference), and an unrecognised name degrades to
    a generic glyph with a warning.
  - *Deviation:* §12 gained two stated exceptions to "absent optional values are omitted" —
    `origin` and `site.homeUrl` are always present, and an emptied collection is `[]` rather than
    absent. Both surfaced while hand-writing the first expected results, which were otherwise
    unwritable: `[]` and "omitted" would have been indistinguishable, and §10 requires them to mean
    different things.
  - *Deviation:* documented `menu`, which the limits table has always bounded (20 items) and which
    `SiteSkinLimits.MAX_MENU_ITEMS` already declares, but which no structural section defined.
  - Modified: `spec/SPEC.md`
  - Restructure so the **trust model precedes any field description** (PRD NFR — a skimming reader
    must not come away thinking the site is in control). RFC 2119 keywords throughout.
  - New sections: **Normalization** (the canonical projection, with fixed field order — this is the
    shape `CORE-004` owes the corpus), **Glob grammar** for `match`, **Contrast** (WCAG 2.2
    relative luminance, AA 4.5:1 body / 3:1 large, correction adjusts the *manifest* colour never
    the browser-owned text, deterministic steps), **Disposition** (the reject / drop-item / warn
    model from the plan).
  - Document the drop-item consequences explicitly: empty navigation activates SiteSkin with no
    bottom bar rather than falling back; `DUPLICATE-ID` drops the *later* occurrence, so order
    matters; a missing required field is `reject`, not a droppable item.
  - Retain §5 "There is no `showDomain`" with its `ADR-006` pointer (PRD AC 6), and state the
    format's deliberate non-features (`ADR-003`) as decisions rather than omissions.
  - Acceptance:
    1. `Status: SPEC_READY`.
    2. Every code registered so far appears verbatim in the document.
    3. The canonical projection is specified precisely enough to write an expected file by hand.
    4. Glob grammar excludes `?`, character classes and braces, and is anchored at path start.
    5. Contrast correction is deterministic — same input always yields the same corrected value.
    6. Core subset gate passes.
  - Tests: `everyRegisteredCodeAppearsInSpec` (string containment — enough to catch a code added to
    the registry but never documented)

- [x] **TASK-3: JSON Schema and the valid corpus**
  - **Done.** 17 corpus tests green, detekt green, `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset.
  - *Deviation:* the task said "Enums encode the allow-lists: nine action types, four schemes, the
    closed icon set". **The schema enumerates none of them, deliberately.** An `enum` on
    `action.type` makes an unrecognised type `SS-E-SCHEMA-INVALID` → `reject`, which is precisely
    the outcome `ADR-007` exists to forbid; the same argument applies to icons. Both are constrained
    by pattern instead — `^[a-z][a-z_]{0,31}$` and `^[a-z][a-z0-9_]{0,31}$` — which delivers the
    real security property (neither field can carry a URL or resource reference) while leaving the
    allow-list enforcement in the security layer where the disposition can be `drop-item`. Schemes
    were never expressible here anyway: the schema does not know the serving origin.
    `schemaDoesNotEnumerateActionTypesOrIcons` asserts this directly, because no *valid* fixture
    uses an unknown type and the corpus would otherwise not notice an enum being added.
  - *Deviation:* no `maxLength` or `maxItems` anywhere in the schema, though the limits are real.
    Encoding them would make an over-long title a rejection, while §8 requires truncation with a
    warning. Limits are a security-layer concern for the same reason the allow-lists are.
  - *Note:* `if`/`then` conditionals require `url` for `internal_url`/`external_url` and `value` for
    `phone`/`email`/`map`. These fire only for known types, so unknown types stay unconstrained and
    reach the security layer intact.
  - New: `spec/siteskin-1.0.schema.json` — draft 2020-12, structure only,
    `additionalProperties: true` (unknown fields are ignored by design; a schema rejecting them
    would contradict the `SPEC-002` forward-compatibility policy). No `$id` — see plan § Open
    questions.
  - New: `spec/fixtures/valid/bloom-flowers.{json,expected.json}` — the flagship, matching mockup
    screen 3
  - New: `spec/fixtures/valid/minimal.{json,expected.json}` — required fields only, proving the
    format's floor is genuinely small
  - New: `spec/fixtures/valid/forward-compat-1.1.{json,expected.json}` — a `1.1` manifest with
    unknown fields, accepted with `SS-W-FIELD-UNKNOWN` (registers that code)
  - Modified: `SpecCorpusTest` — schema-layer assertions
  - Acceptance:
    1. Every `valid/` fixture validates against the schema.
    2. Each `valid/` fixture's `expected.result` is a complete canonical projection.
    3. Enums encode the allow-lists: nine action types, four schemes, the closed icon set, the two
       asset MIME types.
    4. Core subset gate passes.
  - Tests: `validFixturesPassTheSchema`, `schemaEncodesTheActionTypeAllowList`,
    `unknownFieldsAreAcceptedNotRejected`

- [x] **TASK-4: Origin-binding invalid corpus**
  - **Done.** Seven fixtures, core subset gate green. Verified the `test` task actually re-executed
    rather than passing an up-to-date check — `:siteskin-core:test` is absent from Gradle's
    UP-TO-DATE list and its results are newer than both the fixtures and the registry, which
    confirms the `inputs.dir(spec/)` wiring from TASK-1 does what it claims.
  - *Note:* `nav-userinfo-authority` is the sharpest of the six. The serving origin's host appears
    verbatim inside the URL, so any check built on `contains` or `startsWith` passes while the real
    host is `evil.example`. It is the fixture that punishes comparing strings instead of parsed
    origins, and `CORE-001` should treat it as its acceptance criterion.
  - New fixtures, each registering `SS-E-ORIGIN-MISMATCH` usage — one per resolver failure mode
    rather than one representative case, because these are the bugs most likely to be introduced by
    a plausible-looking refactor:
    - `nav-cross-origin` — absolute URL to another origin
    - `nav-protocol-relative` — `//evil.example/x`
    - `nav-traversal-escape` — `/../../evil`
    - `nav-userinfo-authority` — `https://bloomflowers.example@evil.example/`
    - `nav-port-change` — `https://bloomflowers.example:8443/`
    - `home-url-cross-origin` — the same rule applied to `site.homeUrl`
  - New: `spec/fixtures/invalid/logo-subdomain.{json,expected.json}` — `SS-E-ASSET-CROSS-ORIGIN`
    on `https://cdn.bloomflowers.example/logo.png`. Per `ADR-004` a subdomain is a **different
    origin**; this fixture exists specifically because it is the one a future contributor is most
    likely to "fix" into a bug.
  - Acceptance:
    1. Each fixture declares `disposition: drop-item` with an RFC 6901 `pointer` at the offending
       element.
    2. Each carries a `result` showing what survives — a dropped item must not silently take the
       rest of the manifest with it.
    3. All pass the JSON Schema (they are structurally valid; only the security layer rejects
       them), which is asserted, not assumed.
    4. Core subset gate passes.
  - Tests: `securityLayerFixturesPassTheSchema` — the assertion that keeps the layer split honest.
    It fails loudly if a security rule is later smuggled into the schema, where it would silently
    drift from `CORE-004`.

- [ ] **TASK-5: Scheme allow-list invalid corpus**
  - New: one fixture per denied scheme (PRD AC 5) — `javascript:`, `file:`, `content:`, `intent:`,
    `data:` — registering `SS-E-SCHEME-DENIED`
  - New: `external-url-http.{json,expected.json}` — `external_url` over cleartext HTTP is denied
    even though `http` is a real scheme
  - Acceptance:
    1. Five denied schemes plus the HTTP case, each `drop-item` with a pointer.
    2. `intent:` has its own fixture and is not folded into a generic case — `ADR-007` names it as
       the example proving why this is an allow-list rather than a deny-list.
    3. Core subset gate passes.
  - Tests: covered by the registry/integrity suite; add `everyDeniedSchemeHasItsOwnFixture`
    asserting the five names appear across `invalid/`

- [ ] **TASK-6: Limits, contrast, duplicate ids, unknown action type**
  - New fixtures registering the remaining codes — `SS-E-ACTION-UNKNOWN`, `SS-E-DUPLICATE-ID`,
    `SS-W-LIMIT-TRUNCATED`, `SS-W-CONTRAST-CORRECTED`:
    - `nav-over-limit` (6 items → 5), `menu-over-limit`, `quick-actions-over-limit`
    - `title-over-length`, `label-over-length`
    - `duplicate-nav-id` — asserts the **later** occurrence is the one dropped
    - `unknown-action-type` — one item dropped, manifest otherwise intact (`ADR-007`)
    - `hostile-contrast` — text and background within AA threshold, corrected deterministically
    - `showdomain-ignored` — a manifest containing `toolbar.showDomain: false`, proving it is
      *ignored* with `SS-W-FIELD-UNKNOWN` rather than honoured (`ADR-006`)
  - Acceptance:
    1. Registry is now complete; `everyRegisteredCodeHasAFixture` covers all 13 codes.
    2. Truncation fixtures carry a `result` pinning exactly what survived.
    3. `hostile-contrast.expected.json` pins the corrected colour value, which is only possible
       because TASK-2 made correction deterministic. If it cannot be pinned, TASK-2 is unfinished.
    4. Core subset gate passes.
  - Tests: `everyRegisteredCodeHasAFixture` (now exhaustive),
    `duplicateIdDropsTheLaterOccurrence`, `showDomainIsIgnoredNotHonoured`

- [ ] **TASK-7: Bloom Flowers manifest in `denrzv/bloom-flowers`** *(cross-repo)*
  - New (in `denrzv/bloom-flowers`): `.well-known/siteskin.json` — a **byte-identical** copy of
    `spec/fixtures/valid/bloom-flowers.json`; copy direction is one-way, the fixture is the source
  - New (in `denrzv/bloom-flowers`): a CI step comparing the checksum against the fixture, so the
    copy cannot drift silently
  - Acceptance:
    1. PRD AC 7 — the manifest validates against `siteskin-1.0.schema.json`.
    2. Checksums match; the check fails if either file is edited alone.
    3. Scope stays at the manifest and its guard. The site itself (`INTEGRATION.md`, pages, logo
       asset) is `DEMO-001` — do not build it here.
  - Tests: checksum comparison in `bloom-flowers` CI; `validFixturesPassTheSchema` already covers
    the webora side

- [ ] **TASK-8: Close out — roadmap, deferred-coverage record**
  - Modified: `docs/ROADMAP.md` — tick `SPEC-001`
  - Modified: `CLAUDE.md` — a short note that the corpus lives at `spec/fixtures/` and is executed
    by `:siteskin-core:test` via `siteskin.spec.dir`, so the next session does not go looking for
    it in module resources
  - Acceptance:
    1. Full `bash scripts/pre-commit-check.sh` run on a machine **with** an Android SDK, green.
    2. The deferred-coverage section below is accurate as-left.
  - Tests: full gate

---

## Deferred, deliberately — not gaps

Recorded here so a reviewer does not mistake absence for oversight, and so `/validate` has
something to check the claim against.

**Security-layer behaviour is not tested by this ticket.** Whether a cross-origin `internal_url`
actually gets dropped cannot be asserted before the code that drops it exists. SPEC-001 asserts
those fixtures *structurally* — paired, registered, origin-declared, schema-passing — and no
further. `CORE-003` extends `SpecCorpusTest` to execute the schema-validation layer against the
corpus; `CORE-004` extends it to execute the security layer and the canonical projection. Both
tickets inherit this file's fixtures as their acceptance criteria.

**Negative controls are not applicable here**, in the `PROJECT_RULES.md` sense: there is no
protection to revert, because no security code is written in this ticket. They become mandatory at
`CORE-004`, where every protection this spec describes gets one. This is not a waiver — it is the
reason the requirement does not bind yet.

**The `invalid/` corpus is not frozen until `HARDEN-001` reviews it** against the threat model
(PRD risk 1: a fixture asserting wrong behaviour makes that behaviour a requirement). Until that
review a fixture may be corrected in place; after it, correcting one is a versioned change.

## Open questions carried from the plan

Not blocking TASK-1..6; each needs an answer before the corpus is frozen.

- Schema `$id` — omitted until the domain in `docs/DEVELOPMENT_PLAN.md` § Hosting resolves. A `$id`
  that 404s is worse than none.
- `icon` allow-list membership — propose the minimal set covering the four demos in TASK-3; let
  `DEMO-002` widen it if a demo cannot be expressed.
- Whether `external_url` belongs in v1.0 — TASK-5 assumes it does, per the plan's weak
  recommendation. If it moves to 1.1, `external-url-http.json` moves with it.
