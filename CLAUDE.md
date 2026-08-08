# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## Project Overview

**Webora Browser** is an Android browser that adapts its native chrome to websites which publish a
**SiteSkin** manifest. For a site with a valid manifest, the browser renders a branded top bar,
bottom navigation and quick actions around the WebView. For every other site it behaves like an
ordinary browser.

The product's central contract:

> The website defines what should be available. The browser decides how it is safely displayed and
> executed.

The app brand is **Webora**; the protocol is **SiteSkin**. They are deliberately separate names —
site owners implement a vendor-neutral protocol, not one browser's private format.

> **Repo naming:** the repo is `denrzv/webora`. An earlier bootstrap session was blocked from
> attaching it and wrote into `denrzv/skinsite`, which is why some history refers to a pending
> rename — there is none. `skinsite` never received a commit and needs no migration.

## Build Commands

```bash
./gradlew :siteskin-core:test          # pure JVM, needs no Android SDK — start here
./gradlew :siteskin-core:check         # + the Android-dependency leak check
./gradlew test                         # all unit tests
./gradlew :app:assembleDebug           # the task that actually runs D8
./gradlew :app:assembleDebugRelease    # local-testing APK, cleartext HTTP permitted
./gradlew detekt                       # complexity gate
./gradlew :siteskin-lint:run --args="https://site.example"
bash scripts/pre-commit-check.sh       # everything above, in order
```

**Stack:** Kotlin 2.3.20, AGP 9.1.0, minSdk 26, targetSdk 36, Compose BOM 2026.03.01,
kotlinx.serialization, OkHttp 5.

---

## AIDD workflow (mandatory)

See `workflow.md` for the state machine and `PROJECT_RULES.md` for the hard rules.

1. `/idea <TICKET> "Title"` → `Status: PRD_READY`
2. `/researcher <TICKET>` → `Status: RESEARCH_READY`
3. `/plan <TICKET>` → `Status: PLAN_APPROVED`
4. `/tasks <TICKET>` → `Status: TASKLIST_READY`
5. Per task: `/implement <TICKET> TASK-N` → `/pre-commit` → commit `<TICKET> TASK-N: <short>`
6. `/review` → findings become `TASK-FIX-N` micro-tasks
7. `/qa` when green, then `/validate`

Research runs **between** `/idea` and `/plan`, never after it. The PRD says what the ticket is for;
the plan commits to a trust boundary and a file list. Deciding those without first mapping the
affected origins, the manifest-controlled surface and what must stay browser-owned means the plan
is a guess that implementation then has to relitigate. `/plan` reads
`docs/research/<TICKET>.md` as its input.

A `PreToolUse` hook blocks `Edit|Write` until all four artifacts carry their ready status —
research included, gated on `Status: RESEARCH_READY` rather than on the file existing, since a gate
`touch` satisfies is decoration.
`docs/`, `reports/` and `spec/` are exempt, or writing the PRD would be blocked by the gate that
the PRD exists to satisfy.

The full milestone plan, ticket index and the decisions behind them live in
**`docs/DEVELOPMENT_PLAN.md`**. Read it before proposing architecture.

---

## Architecture

```
:siteskin-core   pure JVM — origin, DTOs, validation, actions, nav matching
:siteskin-lint   CLI wrapping core; the tool site owners run
:app             app.webora.browser — Compose, WebView, OkHttp
```

### The module boundary is the security boundary

`:siteskin-core` has **zero** Android dependencies and is where every security decision lives:
origin binding, scheme allow-listing, action resolution, contrast correction, limit enforcement.
This is not tidiness. It means the security logic is testable with plain JUnit in milliseconds, with
no emulator and no Android SDK — so there is never a reason to skip a security test because the
harness is slow.

Two mechanisms enforce it, because a documented rule is not a rule:
- `:siteskin-core:check` depends on `assertNoAndroidDependencies`, which fails on any `androidx.*`
  or `com.android*` artifact on the compile classpath.
- CI's `core` job installs no Android SDK at all and runs `:siteskin-core:check` with `ANDROID_HOME`
  unset.

Core declares `interface ManifestSource` and consumes bytes. OkHttp lives in `:app`. Core never
learns what a `Context` is.

### Trust pipeline

```
Remote bytes → size guard → JSON parse → DTO → schema validation
             → security validation → normalization → SiteSkinConfiguration
```

`SiteSkinConfiguration` is constructible only via the validator. If you hold one, it passed. Never
add a public constructor or a `copy()` that skips validation — that turns a compile-time guarantee
back into a code-review guarantee.

Parsing success is not validity. A DTO is untrusted remote input that happens to be well-formed.

---

## Java version — two knobs, do not conflate them

| Knob | Value | Constrained by |
|---|---|---|
| Gradle toolchain (runs the build) | **25 LTS** | Gradle/AGP. Free to raise. |
| `jvmTarget` / source & target compatibility | **21** | D8/R8 dexing, *not* the build JDK |

`:siteskin-core` is a plain JVM library but is dexed into the APK, so it inherits `:app`'s ceiling.

If you change the bytecode target, verify with `./gradlew :app:assembleDebug` — that is the task
that invokes D8. `compileDebugKotlin` succeeding proves nothing about dexability, and the failure
otherwise surfaces at assemble time, long after the code looks fine.

---

## Security notes

### Browser-owned chrome (ADR-006)

The registrable domain and TLS indicator are **always visible in SiteSkin mode**, in browser-owned
typography, and no manifest field can suppress them. The concept document's `toolbar.showDomain`
is deliberately **not** implemented as site-configurable.

The reason is concrete: a manifest supplies a title, colours and a logo. Allow it to also hide the
domain and a manifest on `evil.example` renders chrome reading "Your Bank" with a bank logo and no
contradicting signal anywhere on screen. That is both the sharpest security hole in the design and
a Google Play *Deceptive Behavior* violation, which is suspension-grade rather than
rejection-and-resubmit.

The site gets colours, a title, and a bounded logo slot *beside* the domain — never instead of it.

### First-use consent (ADR-011)

The first time an origin's manifest validates, the user is asked before the chrome changes. Allow /
Not now / Never for this site. This is the enforcement point for the per-site opt-out, and it is
what makes the trust boundary legible rather than implicit.

### Allow-lists, never deny-lists

Schemes (`https`, `mailto`, `tel`, `geo`), action types, icon names, asset MIME types. An unknown
action type drops that item and keeps the rest of the manifest; an unknown *major* schema version
rejects the whole manifest and falls back to regular browsing.

### Failure is always graceful

An invalid manifest never breaks browsing. Every failure path ends in regular browser mode with the
page still rendering. `ADR-010`.

### Discovery never blocks rendering

The page load and the manifest fetch are concurrent. The chrome transitions when validation
succeeds. Never gate `onPageStarted` on a network call. `ADR-009`.

---

## Testing

JUnit 4 + MockK. **No Robolectric** — deliberate. Consequences:

- Android-touching objects split into a thin public wrapper reading real framework state, plus an
  `internal` pure function the tests call directly. Test the pure function.
- MockWebServer for anything network-shaped. Distinct ports are distinct origins, which is exactly
  what origin-binding tests need.
- `mockkObject` is a **partial** mock: unstubbed functions run the real implementation. Stub every
  function a test path touches.

**Security tests need a negative control.** Revert the protection, confirm the test fails, restore
it. A test that passes with and without the fix is not evidence — it is decoration. Record the
negative-control result in the tasklist.

The conformance corpus in `spec/fixtures/` is the contract. Validation code is written to satisfy
it, not the other way round. Adding a validation rule means adding a fixture first.

### How the corpus is wired (SPEC-001)

It lives at the **repo root**, not in module resources — it is shared with `:siteskin-lint` and
`denrzv/bloom-flowers`, and it is a published artifact a second implementer is meant to consume
without running Gradle. `:siteskin-core`'s `test` task passes its path as `siteskin.spec.dir` and
declares it via `inputs.dir`, so editing a fixture reruns the tests instead of hitting an
up-to-date check.

`spec/diagnostics.json` is the machine-readable registry. `SpecCorpusTest` asserts completeness in
**both** directions: every registered code has a fixture, and no fixture invents a code. So the
registry must grow *with* its fixtures — adding codes ahead of them leaves the build red.

Three invariants worth knowing before you touch the schema:

- **`siteskin-1.0.schema.json` covers structure only.** Origin binding is not expressible there (it
  does not know the serving origin), and the allow-lists are deliberately *not* `enum`s: an enum on
  `action.type` or `icon` turns an unknown value into a whole-manifest rejection, which is exactly
  what `ADR-007` forbids. `securityLayerFixturesPassTheSchema` fails if a security rule is ever
  smuggled in — verified with a negative control.
- **No `maxLength`/`maxItems` in the schema.** `SPEC.md` §8 requires over-limit values to be
  *truncated with a warning*; a schema constraint would make them fatal.
- **The disposition is a property of the code, not the fixture.** `reject` / `drop-item` / `warn`
  live in the registry, and `SS-E-ACTION-UNKNOWN` is an `E` code that deliberately does not reject.
  Never infer behaviour from the `E`/`W` prefix.

`spec/fixtures/valid/bloom-flowers.json` is byte-identical to `denrzv/bloom-flowers`'s published
`.well-known/siteskin.json`, pinned by SHA-256 in both repos. Changing it means updating the
constant in `SpecCorpusTest`, the copy, and that repo's `.sha256` — all three.

### Versioning and the layer model (SPEC-002)

**Validation runs `transport → parse → version → schema → security`, and a rejection short-circuits
every later layer.** The order is data, not prose: `layerOrder` in `spec/diagnostics.json`, asserted
by `diagnosticsDoNotCrossARejectingLayer`. A fixture may not expect a diagnostic from a layer its
own rejection has already made unreachable.

The one counter-intuitive consequence, and the thing to not "fix":

- **`version` runs before `schema`, but only on a *present, well-formed* version string.** An absent
  or malformed `schemaVersion` is `SS-E-SCHEMA-INVALID`, never `SS-E-VERSION-UNSUPPORTED` — with no
  version there is no major to evaluate. `invalid/version-missing.json` pins it.
- Version runs first so that a `2.0` document whose *shape* is alien to `1.0` is refused on its
  version rather than producing a heap of structural errors about a format we already decided not to
  interpret. `invalid/version-major-2-alien.json` is the only fixture that can prove this, and it
  carries `"schemaValid": false` for the purpose.

`Fixture.schemaValid()` and the layer order answer **two different questions** — "is this document
structurally valid?" (asserted against the real schema, for every parsing fixture) versus "would a
browser ever ask the schema?" Collapsing them loses coverage: `oversized` and `version-major-2` are
both rejected pre-schema and both still prove they are structurally valid.

`spec/versions.json` is the version decision table — every spelling at the boundary, with its
decision and diagnostic. It is a **registry**, which is why it sits beside `diagnostics.json` rather
than under `fixtures/`. Its `wellFormed` column is checked against the `schemaVersion` pattern read
out of the schema file at test time, never a copy.

**Two schema landmines, both fixed and both easy to reintroduce:**

- **Never end a schema `pattern` with a bare `$`.** JSON Schema specifies ECMA-262 regexes, where an
  unflagged `$` matches only at end of input. `java.util.regex` also matches it *before a final line
  terminator*, so `"schemaVersion": "1.0\n"` validated against a schema that meant to forbid it —
  and `schemaVersion` keys the manifest cache. Anchor with `(?![\s\S])`. Guarded by
  `schemaPatternsAnchorAtEndOfInput`, and the reason to check regex behaviour against the engine
  rather than the specification it cites.
- **No leading zeros in `schemaVersion`.** `01.0` and `1.0` must not be two spellings of one
  version, for the same cache-key reason. Fix the grammar, never normalize at read time — a
  normalization step is a second place for the two spellings to reappear.

Both were taken as **breaking changes inside a free-change window** that `SPEC.md` §4.5 documents
and closes. There is no second window. For genuine defects found later, §4.3's security carve-out
lets a narrowing ship in a minor — deliberately written as an exception rather than by redefining
such changes as non-breaking.

`SS-W-FIELD-DEPRECATED` is **reserved in `SPEC.md` §4.4 and deliberately absent from
`diagnostics.json`.** Nothing in `1.0` is deprecated, so it has no fixture, and a code with no
fixture does not exist. Register it in the same commit as the fixture that produces it.

---

## Detekt

Wired in the root `build.gradle.kts` for all subprojects, so a new module cannot silently escape the
gate. `scripts/pre-commit-check.sh` invokes it **unconditionally** — never guard it behind
`tasks --all | grep -q detekt`, which lets an unwired plugin masquerade as a passing build.

Two config landmines, both pre-fixed here:
- `complexity > ComplexMethod` is a deprecated name; with `warningsAsErrors: true` it fails the
  whole run. Use `CyclomaticComplexMethod`.
- `naming > FunctionNaming > ignoreAnnotated: ['Composable']` — otherwise every composable trips
  PascalCase.

The baseline starts empty. Anything added to it is a reviewed exception with a ticket.

---

## Non-negotiable constraints

- The manifest is data, never code. No `addJavascriptInterface`, no dynamic loading, no arbitrary
  intents.
- No website gains an Android permission by publishing a manifest. A "Call" action opens
  `ACTION_DIAL`; it does not grant `CALL_PHONE`.
- HTTPS required for SiteSkin mode. Only `debugRelease` relaxes cleartext, and only in its own
  source set.
- Subdomains are not trusted. Assets are same-origin only.
- No telemetry without explicit opt-in.
- Never block the main thread on network or image decoding.
