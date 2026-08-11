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

### Origin model

`SiteOrigin` is the sole origin-comparison type. It canonicalizes scheme, host and port before it
can be constructed; origin binding compares that complete tuple, never suffixes or registrable
domains. The registrable domain and mixed-script signal are browser-owned display properties and
do not participate in equality. See `ADR-004` for the canonicalization and bundled Public Suffix
List decisions.

Use `java.net.URI`, never `java.net.URL`: `URL.equals` may perform DNS resolution. JDK 25's URI/IDN
behaviour also has several non-obvious constraints pinned by the core tests:

- `IDN.toASCII` does not lowercase an ASCII host, so canonicalization lowercases its ASCII output.
- `URI.host` is null for a valid Unicode hostname, so parsing starts from the raw authority.
- `URI.resolve` accepts protocol-relative references and inherits the base scheme; reject them
  before resolution.
- `URI.normalize` preserves a leading traversal as a residual `..`; reject that residue rather
  than treating normalization as permission to accept the URL.
- IPv6 literals keep their brackets and bypass IDN conversion.

### Trust pipeline

```
Remote bytes → size guard → JSON parse → DTO → schema validation
             → security validation → normalization → SiteSkinConfiguration
```

`SiteSkinConfiguration` is constructible only via the validator. If you hold one, it passed. Never
add a public constructor or a `copy()` that skips validation — that turns a compile-time guarantee
back into a code-review guarantee.

Parsing success is not validity. A DTO is untrusted remote input that happens to be well-formed.


Manifest parsing is a bounded stream operation: core consumes no more than 131,073 bytes, rejects
malformed UTF-8 rather than accepting replacement characters, and leaves the caller-owned stream
open. `ignoreUnknownKeys` is paired with an explicit shape walk that emits
`SS-W-FIELD-UNKNOWN` paths; enabling it alone would silently discard protocol diagnostics.
Before constructing a JSON tree, a non-recursive structural scan rejects object/array nesting over
64 levels with `SS-E-PARSE`. The scan is string/escape aware; kotlinx.serialization remains
authoritative for all other JSON grammar. This prevents a small, syntactically valid manifest from
turning parser or unknown-field recursion into stack exhaustion.

### Action-resolution seam (CORE-005)

`ActionResolver` accepts only a trusted `NormalizedAction`, trusted `SiteConfiguration`, and the
browser-observed current page URL. It maps the nine v1 action types into the closed
`ResolvedAction` hierarchy; `home` uses `site.homeUrl`, while `share` uses the browser-observed
page rather than manifest payload. Unknown or internally inconsistent values fail closed.

Resolved actions remain pure data. External-navigation confirmation, Android intent construction,
handler selection, permissions, WebView operations, and SiteSkin menu UI belong to the app layer.
Do not add a generic URI, intent, package, component, flag, MIME-type, or extras variant to the
sealed model; that would turn the action allow-list back into arbitrary native capability.

### Navigation matching (CORE-006)

`NavMatcher` consumes trusted ordered `NavigationItem` values and a browser-observed absolute page
URL, then returns at most one active item. It uses only the decoded HTTP(S) path; query, fragment,
authority, malformed URLs, and unsupported schemes cannot become match content. Runtime origin
activation remains a separate mandatory boundary.

Manifest patterns are never compiled as regex. A handwritten bounded matcher implements `*`
within one segment and a complete `**` segment across zero or more whole segments. Exact literals
beat globs; otherwise the longest literal prefix wins, then document order. No match means no active
item — never default to the first navigation entry.

### SiteSkin navigation presentation (SKIN-003)

Bottom navigation, quick actions, and the SiteSkin menu are standalone app-layer components backed
by a pure `SiteSkinChromeModel`. The model consumes only trusted `SiteSkinConfiguration` plus the
browser-observed current page URL, applies defense-in-depth 5/5/20 caps, and uses `NavMatcher` for
an active id; no match means no selection. Components emit the original trusted `NavigationItem`,
never raw URI or intent fields. The menu always appends a separately labelled closed browser section
for page information and settings, which manifest entries cannot suppress or replace. Symbolic
icons use a closed local decorative mapping with cleared accessibility semantics.

These components deliberately are not wired into `BrowserScreen`. `SKIN-004` owns consent-aware,
origin-bound activation and connects typed selections to `ActionResolver` and browser-owned effect
dispatch.

### Total validation and `siteskin-lint` (SPEC-003)

`SiteSkinValidator` is the shared activation seam for browser and tooling. It consumes one
caller-owned stream without closing it, preserves raw JSON through the normative
`parse → version → schema → security` order, and returns either a trusted configuration with
non-rejecting diagnostics or rejection with no configuration. Do not assemble those validators
again in a caller or decode to a DTO before structural validation; both create diagnostic drift.

`:siteskin-lint` owns only command/transport policy. Its public command accepts an origin-only HTTPS
URL, fetches `/.well-known/siteskin.json`, follows at most two exact-origin redirects, rejects an
explicitly non-JSON response type, and streams into `SiteSkinValidator`. Exit 0 means a trusted
configuration exists, even when warnings or dropped items are reported. DNS/TLS/HTTP/usage failures
are tool errors, not synthetic `SS-*` diagnostics.

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

### WebView host policy (BROWSE-001)

Every app-owned WebView is created through `HardenedWebView` and hardened before its first load.
The immutable browser policy enables in-renderer JavaScript but disables file/content access,
file-URL escalation, and mixed content; AndroidX WebKit enables Safe Browsing when the installed
provider supports it. Top-level HTTP(S) stays renderer-owned and other schemes fail closed for a
later browser-owned dispatcher. Do not introduce `addJavascriptInterface` or let page, manifest,
or UI state parameterize these settings.

### Browser state and navigation (BROWSE-002)

Browser chrome uses the sealed `BrowserMode` hierarchy from ADR-008: Home, Regular with an optional
browser-observed origin, or Integrated with a trusted origin and `SiteSkinConfiguration`. Page
callbacks may produce only Regular mode; a future validated SiteSkin activation seam must produce
Integrated mode. Address input is resolved by browser-owned policy into explicit HTTP(S), host-like
input promoted to HTTPS, or an encoded HTTPS search query; denied schemes never reach the renderer.
System and predictive back consult live WebView history and delegate when it cannot be consumed.

### Home and onboarding (BROWSE-003)

A first launch shows browser-owned product onboarding and persists only a local completion Boolean;
this never substitutes for ADR-011's future per-origin SiteSkin consent. Returning launches enter
`BrowserMode.Home`, whose recents and favourites remain honest empty states until their persistence
contract exists. Suggested integrations are a compiled browser-owned HTTPS-only catalogue: remote
pages and manifests cannot add, relabel, reorder, theme, or provide artwork for entries. Home does
not create a WebView or perform network work; explicit destinations pass through `AddressResolver`
before switching to Regular mode and composing the hardened renderer.

### Regular chrome and load failures (BROWSE-004)

Regular-mode identity is derived only from the committed main-frame `SiteOrigin`, never editable
address text or document content. HTTPS displays the browser-owned secure affordance and every valid
origin displays its registrable domain. WebView errors cross into Compose only for requests marked
`isForMainFrame`; legacy SSL callbacks are cancelled but cannot replace a page because they do not
identify the main frame. Error UI maps framework codes to closed browser-owned reasons, displays at
most the registrable domain, and retains a retry capability only for the exact observed HTTP(S) URL.

### External navigation and transfers (BROWSE-005)

Only main-frame `mailto`, `tel`, and `geo` navigation may leave the renderer, and each request first
becomes inert typed data shown in a browser-owned confirmation; subframes and arbitrary schemes
cannot prompt or launch. Android intents are constructed from a closed mapping and never parsed from
remote intent syntax. Downloads accept absolute HTTP(S) only and use browser-selected
`DownloadManager` policy. Uploads use a single-result SAF picker only when page accept hints reduce
to the browser MIME allow-list; unsafe hints cancel rather than widening to `*/*`, and only a
system-selected `content:` URI returns to the page. None of these flows grants a runtime permission.

### Manifest discovery transport (NET-001)

The app observes main-frame page starts without delaying WebView rendering, canonicalizes an HTTPS
`SiteOrigin`, and asynchronously requests only `/.well-known/siteskin.json`. OkHttp automatic
redirects stay disabled: the app follows at most two redirects after comparing each target's full
canonical origin. Explicit call/connect/read/write timeouts and a 128 KiB sentinel read bound remote
work before bytes enter the shared `SiteSkinValidator`. A new navigation or composition disposal
cancels both the discovery coroutine and its underlying OkHttp call; rejected, missing, oversized,
timed-out, or otherwise failed discovery remains regular browser mode. Applying accepted
configuration remains a later M3 ticket.

### Manifest cache (NET-002)

Only a `SiteSkinValidator`-accepted body enters the in-memory manifest cache. Its key is the full
canonical origin plus the trusted exact schema version, while an origin-scoped active-version index
prevents lookup from crossing ports, schemes, hosts, or subdomains. Website-controlled
`Cache-Control` can shorten freshness but cannot exceed 24 hours; missing, ambiguous, malformed, or
overflowing `max-age` is immediately stale. Stale entries contribute `ETag` and `Last-Modified` to
conditional discovery. A `304` refreshes the same exact entry, and only explicit transport
unavailability may reuse stale accepted bytes; HTTP rejection or invalid replacement content fails
closed to regular browsing. Cached bytes are validated again for the currently observed origin
before an outcome is emitted.

### Brand asset pipeline (NET-003)

Later SiteSkin UI receives only a closed decoded `Bitmap` or browser-generated monogram; it never
receives a remote logo URL to hand to a general-purpose image loader. The app rechecks the trusted
configuration's logo against its complete canonical HTTPS origin and manually follows at most two
exact-origin redirects. Only PNG/WebP declarations with matching byte signatures and decoder types
are accepted; SVG is always refused. Input is capped at 512 KiB, bounds-only decoding enforces 1024
pixels per axis and 1,048,576 total pixels before full allocation, and all transport/decode work is
off-main and cancellable. Superseded work cannot publish, while every non-cancellation failure yields
a deterministic monogram from trusted bounded site identity. Asset persistence and rendering remain
later-ticket concerns.

### SiteSkin theme projection (SKIN-001)

Integrated UI colour roles are created only by `SiteSkinTheme.from(SiteSkinConfiguration)` in the
app layer. The projector exposes a closed six-role light/dark model rather than a general Material
`ColorScheme`: primary/secondary/background containers and their content colours are the entire
website-influenceable surface. Regular-browser, system-bar, domain, and TLS presentation stay
browser-owned and absent from this model.

Core still owns remote colour parsing and normative manifest correction. The app parses only its
canonical opaque trusted values, fills omissions with compiled defaults, derives the dark surface,
and runs a final WCAG guard over every newly formed pair before returning it (4.5:1 body, 3:1 UI).
Do not parse manifest colours in composables or bypass this projector when `SKIN-002`/`003` render
the integrated chrome.

### SiteSkin top bar (SKIN-002)

The standalone integrated top bar is constructed from four closed inputs: a core-trusted
`SiteSkinConfiguration`, the decoded-or-monogram `BrandAsset`, a projected `SiteSkinColorScheme`,
and `SecurityPresentation` derived from the committed browser origin. Its model requires the
browser-observed registrable domain and TLS enum; rendering has no manifest-controlled branch that
can hide, replace, or reorder the security identity. The domain/TLS row uses browser-authored
semantics and the theme's 4.5:1 body pair, not its 3:1 non-text UI pair.

Logos always render through the same clipped 40 dp slot with fit scaling, regardless of bitmap
intrinsics. The top bar has a minimum rather than fixed height so scaled title, subtitle, and
security text can expand without overlap. Manifest discovery, asset decoding, and colour parsing
remain outside composition. The component is intentionally not selected from discovery results;
`SKIN-004` owns consent, activation, and origin-change deactivation before this chrome reaches the
runtime browser screen.

### SiteSkin runtime activation (SKIN-004)

Every main-frame page start deactivates an integrated skin unless the new browser-observed
`SiteOrigin` exactly equals its active origin, then starts a new attributed discovery generation
without delaying WebView rendering. Accepted results can activate only when both their canonical
origin and generation still match current browser state. Coroutine cancellation limits wasted work;
the publication-time origin/generation comparison is the security control.

First-use consent is browser-owned and keyed by full canonical origin. Only Allow and Never persist;
Not now is ephemeral, and stale dialog actions recheck origin/generation before applying. The global
switch and decision-management UI remain for `PRIV-001`. Integrated composition consumes only the
trusted configuration, bounded decoded-or-monogram brand asset, projected theme, and browser-derived
domain/TLS identity. All site item selections pass through `ActionResolver` into an exhaustive
browser dispatcher; external HTTPS and Android capabilities confirm through browser-owned UI before
leaving the renderer.

### Brand-impersonation controls (HARDEN-002)

SiteSkin branding is always presentation beside browser identity, never identity itself. Active
chrome shows browser-derived registrable domain and TLS state through a dedicated semantics node;
the bounded 40 dp logo and its descendants are explicitly decorative. First-use consent displays
the complete canonical origin (including scheme and a non-default port) so the visible grant matches
the exact `SiteOrigin` persistence key. Regular chrome remains active until Allow, Not now never
persists, Never persists only for that origin, and every Allow action rechecks current origin and
navigation generation before applying branding.

### Privacy controls (PRIV-001)

The global SiteSkin preference is browser-owned, local, defaults enabled, and is checked before
discovery and again before candidate publication. Turning it off cancels discovery, dismisses
pending consent, and immediately projects Integrated mode back to Regular without changing the
committed page. Persisted Allow/Never decisions remain keyed and displayed by complete canonical
origin and can be reset individually.

Clear browsing data is an explicit confirmed operation covering WebView cookies, Web Storage,
cache/form/history state, the in-memory manifest cache, and all per-origin SiteSkin decisions. It
deliberately preserves onboarding completion and the global SiteSkin preference. Webora ships no
telemetry/analytics SDK or remote preference sync; `docs/privacy/DATA_SAFETY.md` is the
implementation-backed release mapping.

### Accessibility contract (A11Y-001)

Accessibility is infrastructure with a gate, not a per-screen sweep. A sweep is correct once and
then decays — `HomeScreen` grew inline copy while `strings.xml` was already the rule. Each guarantee
therefore has one enforcement point plus a test that fails when a call site bypasses it.

Browser-owned controls go through `WeboraButton` / `WeboraTextButton`, which bake in the 48 dp
`MINIMUM_TOUCH_TARGET`. Material 3 `Button` is a 40 dp target — it applies
`defaultMinSize(minHeight = 40.dp)` and, unlike `Switch` and `IconButton`, never calls
`minimumInteractiveComponentSize()` — so the raw component is **out of bounds** in browser-owned
Compose, not merely discouraged. `BrowserSurfaceConventionsTest` reads the sources and enforces
that, plus the rule that no string literal reaches `Text(` or an accessible-name argument. Its
scanned set is discovered from `@Composable` rather than listed, so a screen that does not exist yet
is already covered, and a coverage floor keeps the scan from passing vacuously.

`browserAnnouncement(state)` derives the status announcement from current state rather than from a
transition: a Compose live region announces when its content changes, so the derived value already
is the transition, and carrying a previous state would add a second place for it to go stale.
Failure is assertive, progress polite. The live region is a persistent node, not the progress
indicator — hanging it there would destroy the node the moment loading finished.

`SiteSkinTheme.scheme(darkTheme)` selects a projection from the system setting. Both projections
were always computed and guarded; only the light one was consumed. The choice is browser-owned: a
manifest supplies colours and does not decide whether the user's dark-theme preference applies.

**Accessibility is a security surface.** Assistive technology reads the semantics tree, not the
pixels, so every visual bound on manifest content needs a counterpart there. Regular mode publishes
the same browser-authored security node integrated mode already had, built only from the committed
`SiteOrigin` — never editable address text, never page content, never a manifest field; no origin
yields no node rather than a blank one. `SiteSkinChromeModel.accessibleLabel` re-bounds manifest
label text using `SiteSkinLimits.MAX_LABEL_LENGTH` read from core, because one line and an ellipsis
bound the pixels, not the string a screen reader speaks. Both bounds carry negative controls.

`docs/accessibility/CONFORMANCE.md` maps each guarantee to its WCAG 2.2 criterion, its code, and its
test — and marks which are enforced by the JVM gate versus recorded as instrumented evidence. Do not
promote an instrumented assertion to a gate claim; the gate is JVM-only.

### SiteSkin Integration Inspector (DEVX-001)

The browser computed everything a site owner needs and then discarded it: a rejection's diagnostics,
the HTTP status of a refused response, which of `NET-002`'s cache paths served the navigation. A
bounded per-origin **trace** records that as discovery happens, and a debug-only panel reads it.

**The trace observes and never decides.** `ManifestDiscoveryCoordinator` takes a
`SiteSkinTraceSink` defaulting to a discarding `None` — a sink rather than a nullable recorder,
because a null check is a branch and a branch is somewhere the traced and untraced paths can
diverge. `SiteSkinTraceNeutralityTest` runs the discovery matrix twice, recording and discarding,
and asserts the same `ManifestDiscoveryOutcome` and the same `CandidateDisposition` both times. It
carries its own guards against proving nothing: the matrix size, and that the matrix actually
reaches activation, consent and refusal.

**The trusted configuration is already normalized, so the panel cannot show "what the manifest
asked for".** Core truncates over-limit collections and corrects failing colours during security
validation — a six-item `bottomNavigation` arrives with five, `#FFFFFF` on `#FFFFFF` arrives as
`#6F6F6F`. The colour field is therefore named `trusted`, not `requested`, and
`SS-W-LIMIT-TRUNCATED` / `SS-W-CONTRAST-CORRECTED` in the record's diagnostics are the only account
of what changed. The count and colour pairs are divergence indicators between core's normalization
and the app's own caps, expected never to fire; a separate test proves the flag can fire so it is
not decoration.

**Availability comes from the variant source set, never `BuildConfig.DEBUG`.** AGP derives that flag
from `isDebuggable`, and `debugRelease` sets it — gating on it would collect trace data in a variant
compiled against the release stub with no panel to show it. `SITESKIN_INSPECTOR_AVAILABLE` is a
`const val` declared beside the panel in each variant's own file, so the two cannot disagree, and it
folds out the snapshot assembly at compile time in the release variants. `debugRelease` shares
`src/release/java` through an explicit `srcDir` — `initWith(release)` copies build-type
configuration and not sources — and it needs `kotlin.srcDir` as well as `java.srcDir`, or AGP 9's
built-in Kotlin compilation never sees the file.

**Absence is asserted against compiled output, because no test can assert it.** AGP 9.1 creates
`testDebugUnitTest` and nothing else; enabling host tests for `release` fails inside AGP with a
`NullPointerException`. `assertInspectorAbsentFromReleaseVariants` walks the release and
`debugRelease` Kotlin output and fails if the panel class is there **or if the stub's class is
not** — without the second half, renaming the panel makes the check pass while proving nothing.
Wired into `:app:check`, `scripts/pre-commit-check.sh` and CI, which otherwise runs only `test`.

**Untrusted text is bounded before it is displayed.** `SS-W-FIELD-UNKNOWN` reports the key it did
not recognise, so a diagnostic pointer is arbitrary website text; response headers are never
validated at all. `inspectorValue` flattens both to one line and bounds them by
`SiteSkinLimits.MAX_SUBTITLE_LENGTH` read from core. It is a character walk rather than a regex
because `\s` in `java.util.regex` matches neither `U+2028` nor `U+00A0`, and it strips Unicode
format characters too, since `RIGHT-TO-LEFT OVERRIDE` reverses everything after it without
containing a newline. Labels stay browser-authored and are rendered as separate `Text` nodes,
never concatenated with a value.

`BrowserSurfaceConventionsTest` now scans `src/main/java`, `src/debug/java` and `src/release/java`,
and asserts every root contributes — a debug-only screen is browser-owned UI, and leaving the source
set unscanned would make it an escape hatch from the rule the scan exists to enforce.

### Reference integration (DEMO-001)

`denrzv/bloom-flowers` is a real static site, not a fixture with a checksum. It is the first thing
in the project to exercise the protocol against a document tree, and it immediately found something
the corpus structurally cannot: **a manifest can be valid, origin-bound and well-formed while
describing its own site incorrectly.** `home` declared no `match`, so `NavMatcher` left no item
active on the reference integration's own landing page — legal under `SPEC.md` §7.1, and exactly
the wrong thing for the artifact site owners copy.

`ReferenceIntegrationNavTest` is the guard. It drives the published fixture through `NavMatcher` for
every route the site serves and asserts the selected id, plus a path the manifest does not describe
selecting nothing so it cannot pass by matching everything. `OriginCorpusTest` proves the manifest's
URLs *resolve*; this proves they resolve to the right tab, and `tools/check-routes.py` in the other
repository proves they resolve to a file that exists.

**The manifest now lives in five pinned places, not three.** `spec/fixtures/valid/bloom-flowers.json`,
its `.expected.json`, `BLOOM_FLOWERS_SHA256` in `SpecCorpusTest`, the served copy, and that repo's
`.sha256`. Three independent guards catch a partial update from three directions —
`SpecCorpusTest` (the hash), `SecurityConformanceTest` (the canonical result) and the other repo's
`sha256sum --check`. Always **recompute** a checksum with `sha256sum` from the file; transcribing
one is how four copies agree and the fifth does not.

**Discovery is origin-rooted, so the deployment origin is a functional requirement.** A GitHub Pages
*project* site at `denrzv.github.io/bloom-flowers/` cannot host a SiteSkin integration at all:
`/.well-known/siteskin.json` belongs to whatever owns the user-site root, and `internal_url: "/catalog"`
resolves outside the deployment. This is stricter than `DEVELOPMENT_PLAN.md`'s argument that a
shared origin destroys the skin-swap demo — that one is about `DEMO-002`; this one applies to a
single site.

That rule selected the hosting twice. `DEMO-001` chose a custom domain; `SCOPE-001` re-chose when
`webora.app` turned out to be taken, and the answer was the only other free shape that owns an
origin root: a Pages **user** site. `denrzv/denrzv.github.io` publishes by checking out
`denrzv/bloom-flowers` at deploy time — nothing is copied, so the manifest keeps one source of
truth — and it strips that repository's `CNAME`, because Pages honours a custom domain even with no
DNS behind it and will 301 every request into a void. The manifest itself never needed editing:
every URL in it is origin-relative, which is what lets one byte-identical file be both the fixture
bound to `bloomflowers.example` and the document served from `denrzv.github.io`.

`denrzv.github.io` is a stopgap and cannot supply the distinct origins `DEMO-002` would need.

**Route layout is decided by the manifest, not by taste.** Manifest paths are origin-absolute and
cannot be re-authored per host, and only the directory layout (`catalog/index.html`) resolves
everywhere — a flat `catalog.html` serves `/catalog` on GitHub Pages and 404s under
`python3 -m http.server`. The published `match` arrays already tolerate the redirect, including the
case that reads like an off-by-one: `**` matches *zero* or more whole segments, so `"/cart/**"`
selects `/cart` as well as `/cart/`.

**The reference site takes no dependency and no exception.** No CDN, hosted font, icon set,
analytics, cookie, form or storage — it is the artifact most likely to be copied verbatim, so
anything it does becomes a pattern. It also derives its own darker brand shades for body text rather
than shipping white-on-`#D94F8A` at 3.86:1, mirroring the contrast guard `SKIN-001` runs over
manifest colours so the page and the native chrome agree.
