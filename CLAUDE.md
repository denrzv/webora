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

### Integrated mode keeps browser Back visible (UX-008)

SiteSkin replaces both regular chrome surfaces, but it does not replace the browser's history
contract. Integrated top chrome therefore always carries a leading Back affordance before site
branding. Its enabled state comes only from browser-observed WebView history and its callback is the
existing controller operation; a manifest has no field or model seam through which it can suppress,
relabel, reorder, restyle, or dispatch it.

The placement is intentionally asymmetric: SiteSkin navigation remains the only bottom bar and is
still primary, while the browser control occupies a fixed slot beside branding. The slot uses the
Webora Material scheme even though its surrounding bar uses the validated SiteSkin scheme. Do not
change it to a SiteSkin colour merely to blend the row—the visual boundary is the ownership boundary.
At the history root it remains visible and disabled, matching regular chrome and avoiding a shifting
identity layout. The tagged domain/TLS row remains after title/subtitle in the same identity column.

### SiteSkin consent actions are one browser-owned hierarchy (UX-007)

The first-use consent dialog renders all three decisions in one full-width vertical action block:
filled **Allow**, outlined **Not now**, then text **Never for this site**. This is deliberate policy,
not incidental `AlertDialog` slot placement. The stack keeps long labels and 200% font scale from
creating a split row, while the existing browser wrappers preserve 48 dp targets. Manifest data may
influence only the separately attributed bounded preview; it has no path into action labels, order,
emphasis, styling, enabled state, layout, or callbacks. Outside dismissal remains Not now.

### SiteSkin semantic icons (UX-005)

Manifest icon values are semantic tokens, not resource names. Core's closed allow-list normalizes
unknown values to `generic` with `SS-W-ICON-UNKNOWN`; only then can the app's closed mapping select
a bundled `R.drawable`. Never replace that mapping with `Resources.getIdentifier`, URI/file lookup,
a remote asset, or a font glyph. The vectors are decorative: the bounded item label and selected
state remain the accessibility contract. The quick-action launcher previews the first trusted,
bounded action's icon while its browser-authored plural label and expanded action list remain intact.

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

### Local browsing records (BROWSE-007)

History and favourites are browser-owned, app-private records. Identity and navigation target use a
canonical full HTTP(S) URL with its fragment removed; a bounded sanitized HTML title is presentation
only and never a key or command. Successful main-frame completion is the only recording seam, and a
matching failed navigation finish is suppressed. Persisted records are untrusted after restore, so
the versioned codec revalidates every entry and enforces 200 visits, 100 favourites and ten distinct
Home recents. Clear browsing data deletes history/recents but deliberately preserves favourites, as
the confirmation states. Do not add sync, telemetry, favicon fetches or browser-controlled networking
to this store without a new privacy/trust-boundary ticket.

### Home and regular pages share one browser shell (UX-011)

`BrowserNavigationShell` is the one placement contract for `BrowserNavigationDock` on Home/new-tab
and ordinary pages. Its fixed browser-authored order is Back, Forward, Reload, Home, Tabs and More;
the direct Tabs action and overflow both reach the real session switcher. Home keeps page-dependent
actions visible and disabled, while regular enabled state and dispatch come only from the active
tab's observed state and controller.

The shell accepts no manifest configuration, labels, icons, colours, order, URL or generic action.
A manifest therefore cannot hide, relabel, reorder, restyle, enable or dispatch it. SiteSkin may
occupy the integrated bottom product slot only through its existing validated chrome model.

### Mode-aware chrome handoff (UX-012)

`ChromeHandoff` is the exhaustive pure projection from the selected tab's browser-observed
`BrowserMode` to its visible top, content-action and bottom layers. Home and regular modes authorise
the shared browser bottom shell and no SiteSkin actions. Integrated mode alone authorises SiteSkin
quick actions and bottom navigation, paired with an indivisible `PROTECTED_INTEGRATED` top contract
whose domain/TLS identity and Back escape are browser-owned. Renderers consume that projection rather
than maintaining independent mode booleans.

An exact-origin observation drops integrated mode before the next projection, and tab switching
projects only `BrowserSession.activeTab`; inactive configuration and asynchronously loaded brand
assets cannot authorise or decorate the selected tab. A brand asset is associated by configuration
identity and the active configuration falls back immediately to its browser-produced monogram, so a
regular/integrated tab switch cannot flash the previous origin's brand. Consent refusal performs no
activation, therefore the regular browser shell never depends on consent as an escape route.

Edge-to-edge ownership remains at `BrowserScreen`: `WindowInsets.safeDrawing` is consumed once around
the active surface, and the dock does not draw Android Back/Home/Recents or add another system inset.
System/predictive Back continues through `OnBackPressedDispatcher` to the active controller. The
shell uses 16 dp horizontal spacing so six enforced 48 dp targets fit a 320 dp host exactly.

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

The sheet's site-authored half crosses the boundary only through `SiteSkinConsentModel.from`. That
projection bounds remote strings and collection counts with `SiteSkinLimits`, uses the guarded theme
colour, and excludes logos, URLs, action payloads and item labels. It stays attributed and separate
from the browser-authored canonical-origin heading; manifest text is never concatenated into Webora's
identity statement.

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
System, predictive and visible browser Back share one browser-owned contract. They consult live
WebView history first, return only the active tab to native Home when history is empty, and delegate
to Android/app-exit behavior only from Home. Visible regular and integrated Back therefore remain
enabled on a first page even though `WebView.canGoBack()` is false. Home is never represented by a
fake URL or inserted into WebView history, and manifests cannot influence this precedence.

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

**The final showcase uses domain semantics, not compatibility artwork.** Its ordered presentation
is Home/`home`, Flowers/`flower`, Cart/`shopping_cart`, Account/`person`, plus the Call/`call`
quick action. The visible label, semantic token, route, match pattern and typed action describe
different parts of one destination and need not share a spelling — Flowers still routes through
`/catalog`, and Account through `/account` — but all five must stay aligned across the canonical
fixture, normalized result, published copy, adoption guide and deployed origin. `grid_view` remains
a supported compatibility token; it is deliberately not what the flower shop demonstrates.

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

### Screenshot evidence integrity (CI-002)

`CI-001` made the hosted screenshot journey possible and immediately produced the failure mode a
semantic assertion cannot catch: **three frames that passed every Compose assertion while covered by
`System UI isn't responding`.** A green job and worthless evidence. The rules that keep that from
recurring are as much a trust boundary as anything in the manifest pipeline, just one layer up — the
question is not what a website may influence, but what the harness may hide from the person looking
at the picture.

**The cause was contention, and the fix is ordering.** The emulator step used to boot a device and
then run a full Gradle build — 72 tasks, 8m39s in run `31491580516` — on the same 4-vCPU runner.
Android's ANR timers are wall-clock and do not care that the delay came from the host. Both APKs are
now assembled *before* the emulator launches, and `scripts/android-screenshot-ci.sh` refuses to run
when either is missing. That precondition is the load-bearing half: without it a regressed pre-build
step is invisible, because `connectedDebugAndroidTest` would simply rebuild inside the emulator step
and still go green.

**`sys.boot_completed=1` is not a screenshot-ready signal.** It means the boot broadcast fired.
`scripts/android-emulator-ready.sh` requires four observable conditions on three consecutive samples
under a deadline, and writes every sample it took to the artifact. `readiness_verdict` is a pure
shell function of four strings precisely so a checkout with no `/dev/kvm` can still test the gate
that decides when an emulator may be photographed.

**The harness may clear exactly one obstruction, and it is an allow-list of one process.**
`ScreenEvidencePolicy.focusVerdict` classifies `dumpsys window`'s focused window into `OwnedByApp`,
`DismissableSystemAnr` or `Blocked`. Only `Application Not Responding: com.android.systemui` is
dismissable, and only by pressing `Wait`. A System UI *crash*, an ANR in any other process, an
unrecognised window, a null focus, an unparseable dump and two disagreeing focus lines are all
`Blocked`. There is deliberately no `else` branch that dismisses. The obvious "close whatever dialog
is in the way" would also clear a Webora crash dialog and photograph the screen behind it — silently,
with the job still green.

The decision reads only what the OS supplies. AOSP builds those titles from a **process name**
(`"Application Not Responding: " + processName`), so no translated string, page content, dialog text
or manifest field reaches the classification. Do not re-key it on anything a website can influence.

**Ownership is a whole-token package match, not a `package/activity` prefix.** A dialog or popup
window is not guaranteed to be titled that way, and the consent frame is captured with a dialog
focused — the strict shape would fail the journey the guard protects. A bare `contains` was rejected
too: it accepts `com.evil.app.webora.browser.debug`. Both halves have a test.

**`src/screenshotPolicy/java` is shared into `test` and `androidTest` and into no variant.** In
`androidTest` alone the decision would live where `./gradlew test` cannot reach it, and managed
checkouts have no emulator — the test would exist and never have run. In `main` it would ship harness
policy inside the browser. As with `debugRelease`'s `src/release/java`, AGP 9 needs both
`java.srcDir` and `kotlin.srcDir`.

**Readiness records the focused window and never classifies it.** That knowledge has one owner, and
the shell self-test asserts the non-duplication directly: a System UI ANR dialog in `mCurrentFocus`
is a *ready* device. Adding classification to the shell would create a second copy free to disagree
with the first, and nothing would notice until they did.

### Screenshot review experience (DEVX-002)

`CI-002` made the frames trustworthy and left them unreviewable: three canonical PNGs behind a
staging directory, inside one ZIP that also carried an HTML test-report tree, with a job summary that
said `png_count=3` and told you to go find the artifact. `DEVX-002` splits that into a **screenshots
artifact containing nothing but images** — the frames flattened to the root plus one `preview.png`
contact sheet — and a **diagnostics artifact** carrying logcat, instrumentation, readiness samples
and `CI-002`'s `focus-*` / `interference-*` / `window-*` files.

**Convenience is not integrity, and this ticket only buys the first.** `CI-002` decides whether a
frame was allowed to exist; `DEVX-002` decides what happens to it afterwards. A contact sheet
composed from contaminated frames is contaminated evidence that is now easy to glance at and approve,
which is worse than the same evidence being awkward. Nothing here may ever be presented as making a
frame trustworthy.

**A tile's caption derives only from that tile's own filename.** `composeContactSheet` takes a
directory and nothing else — there is no parameter for a title, a caption or a label, so there is no
argument through which workflow, page or manifest text could reach the image. The frames depict
manifest-driven UI; nothing manifest-driven may caption Webora's own evidence. Adding such a
parameter is the violation this shape exists to prevent, not merely a smell.

**The composer is total or it throws, and the workflow checks its arithmetic anyway.** An unreadable
frame is never skipped, because a sheet one tile short still reads as a complete journey to whoever
opens it. On top of that the CLI prints `tiles=N` and the workflow fails the run when that disagrees
with the `png_count` the run collected. A failed compose prints **no** `tiles=` line rather than
`tiles=0` — an absent count and a real count must not look alike to the shell comparing them, and
`tiles=0` would agree with `png_count=0` and pass a check that should never have been reached.

**Order is filename order, which is journey order by construction.** The capturing test names frames
`01-`, `02-`, `03-`; sorting the discovered files means there is no second list of frame names to
fall out of step with the first.

**`:evidence-sheet` is a Gradle module for the same reason `ScreenEvidencePolicy` is in a shared
source set.** A composer living in `scripts/android-screenshot-ci.sh` or in `androidTest` would be
verified by nothing on a developer machine — the gate never compiles `androidTest` at all, which is
how a compile error in `BrowserFontScaleTest` once survived a green `scripts/pre-commit-check.sh`. As
a JVM module, `./gradlew test` picks it up with no wiring and root-applied detekt gates it. It takes
no third-party dependency: `javax.imageio` ships a PNG reader and writer, and `java.awt` draws
headless. **Assert ink pixels, not file existence** — a host with no usable font writes a
structurally perfect PNG with invisible labels, and only pixel counting tells the two apart.

**The two uploads are deliberately asymmetric.** Diagnostics use `if-no-files-found: error` and
screenshots use `warn`: a run that dies before capturing anything is the run someone most needs to
read, and it must not also fail for having no pictures. Composition runs *outside* the emulator step,
because `CI-002` established that work on the runner while the device is alive is what starved
`system_server` into an ANR.

**Artifact names carry the commit SHA because a stale artifact looks exactly like a current one.**
This is field-tested: three frames covered by `System UI isn't responding` were read as current
evidence when they came from run #5 (`328bd08d`), which predates every commit of `CI-002`. Making
evidence easier to open makes opening the wrong one easier too. Check the SHA against the commit you
mean to judge.

### Capture waits for pixels, not semantics (CI-003)

`CI-002` stopped the harness photographing a screen Webora does not own. `DEVX-002`'s first contact
sheet then showed the same lie through a different mechanism: **SiteSkin chrome over an empty page,
on a green job where every assertion passed.**

They passed because every wait in the journey is a **semantics** assertion, and semantics precede
pixels. `assertIsDisplayed()` claims a node has non-zero bounds inside the window — a *layout* fact,
not a drawn one. `waitForIdle()` waits for Compose, and the page is a `WebView`, an Android view
whose paint Compose does not track. A populated semantics tree over a blank surface satisfies all of
it.

**The measurement, from hosted run 11:** `PASSED differing=0.7530 after 696ms`. The page renders and
occupies 75% of its region; the harness was photographing seven-tenths of a second too early. The
integrated top bar was **unpainted, never absent** — deducible before the fix, because
`assertIsDisplayed()` had been passing on `SITESKIN_SECURITY_TAG` all along, which a node missing
from composition cannot do.

**Ownership first, content second.** `requireAppOwnsScreen` runs before `captureWhenRendered`.
Reversing them polls the pixels of a screen Webora does not own.

**The rule is pure and takes samples, never a `Bitmap`.** `RenderedContentPolicy` lives beside
`ScreenEvidencePolicy` in `src/screenshotPolicy/java` and consumes an `IntArray` of ARGB values. An
Android type in its signature would drag the decision into a source set `./gradlew test` cannot
compile — the mistake that let a compile error in `BrowserFontScaleTest` survive a green
`scripts/pre-commit-check.sh`. The guard samples and records; the policy decides.

**Modal colour, not brightness.** The reference integration's pages are near-white (`#FFF7FA`) and so
is an undrawn surface. `MINIMUM_DIFFERING_FRACTION` is 1% and is a **liveness** bar, not a
content-quality bar: the failure being caught is *nothing drawn*, and a rendered page is far above
it. Two tests pin the boundary from both sides, so raising the constant to quiet a flaky run breaks a
test rather than passing unnoticed.

**Measure the page, never the chrome — and this is where it went wrong once.** `SiteSkinQuickActions`
is composed *inside* the very `Box` tagged `BROWSER_CONTENT_TAG`, so the first version measured
Webora's own floating button as page content and passed instantly over a blank page. The journey now
passes chrome bounds from the semantics tree as excluded rects. **One chrome button is inside the
measured region, not two** — the quick action, at `126×126` against `1080×1757`, or 0.84%.

`CI-003`'s review claimed the `SiteSkin inspector` overlay was in there too, and `DEVX-003` disproved
it by removing the overlay and changing nothing: runs 11 and 12 report `differing=0.7530481592174976`
**bit-identically**, with the affordance and without it. It had been sitting below `y=2127`, where the
region ends and the SiteSkin bottom navigation begins. The claim came from composition structure — a
full-screen sibling overlay *can* land inside the rectangle — and structure is where the region
boundary is invisible. Measure the rects; a `rendered-*.txt` from a run that still has the thing in
it is the cheap way, and the reason to record the fraction on success.

The rule the near-miss leaves behind is unchanged: anything composed into that `Box`, or drawn over
it, needs excluding or keeping out — the margin here was 42 pixels of luck, not of design.

**Record the measurement on success, not only on failure.** `rendered-<label>.txt` carries the
winning fraction and elapsed time. A passing check that records nothing cannot be distinguished from
one that barely passed for the wrong reason — which is exactly how the blank frame survived, with
artifact byte counts the only available signal and useless for the question.

**The threshold was never raised to make a run pass.** The symptom would have gone away; the region
would still have been measuring chrome.

### The frame saved must be the frame validated (CI-005)

`CI-002` refuses to photograph a screen Webora does not own. `CI-003` refuses to photograph a page
that has not drawn. Hosted run 14 satisfied both checks and shipped
`03-siteskin-integrated.png` **with `System UI isn't responding` over a blank page**, on a green job.

**The obstruction was not a contaminant of the measurement — it was the measurement.**

```
attempt=1 elapsed=1013ms differing=0.0023084800967043298 modal=#ffffffff samples=119126
attempt=2 elapsed=1943ms differing=0.0023084800967043298 modal=#ffffffff
attempt=3 elapsed=2961ms differing=0.0023084800967043298 modal=#ffffffff
PASSED differing=0.3047865285496029 after 4143ms
```

Three seconds of a 99.77% uniform white region, then 30% the moment the dialog arrived. The dialog's
window is `1024×514` = 27.4% of the `1080×1778` region; a genuinely rendered page had measured
`0.7530`. `RenderedContentPolicy` asks *is this region non-uniform*, which is the right question for
liveness, and a dialog is gloriously non-uniform. The policy was not wrong; it was being asked about
a region something else owned.

**Ownership was a point check in front of a polling capture.** `focus-03-siteskin-integrated.txt`
records `mCurrentFocus=…MainActivity` — clean — because `requireAppOwnsScreen` ran *before*
`captureWhenRendered` began re-screenshotting for 4.1 seconds. `CI-003` wrote "ownership first,
content second" and got the order right; it did not notice that **the frame validated and the frame
saved are different frames**.

`candidateVerdict(focus, content)` composes the two existing policies and accepts only
`OwnedByApp` + `Rendered`. `ContentVerdict.Rendered` appears in exactly one accepting row, and it is
the row where Webora also owns the screen — the defect expressed as data. Two cases, `Accept` and
`Retry`: the capture deadline already owns the decision to give up, and a `Reject` would be a second
place to end the run.

**A dismissable ANR is a `Retry` here and never a dismissal.** Clearing obstructions belongs to
`requireAppOwnsScreen`, with its own budget and its own `interference-*` records. A capture loop that
pressed buttons would be a second, unbudgeted dismissal path running while the harness is supposed to
be observing.

**The dump is read only when content passes.** The loop runs up to forty times; `CI-004` found run
13's System UI ANR was raised by `SystemUIAuxiliaryDumpService` — System UI's own dump service — so a
harness that polls dumps harder may provoke what it is detecting. Blank polls `continue` before the
dump, and the run pays one extra dump per captured frame.

**Ownership is read from the OS and never inferred from pixels.** Detecting a dialog by how it looks
is the heuristic `CI-002` refused; pixels cannot separate a System UI ANR from a Webora crash dialog
from a page rendering a grey rounded card, and a website controls the third.

**The constraint to check any future diff against: this may only ever make the harness refuse more.**
If a change here could make a frame acceptable that is rejected today, it is the wrong change. That
is also why `MINIMUM_DIFFERING_FRACTION` was not touched — the blank page measured `0.0023` and the
obstruction `0.3048`, so no threshold separates them.

The record closes the gap that made this expensive to diagnose: `owner=` on the accepted frame,
`REJECTED <reason>` per discarded candidate, and `RENDERED BUT CONTESTED` as an ending distinct from
`NEVER RENDERED`. Run 14's file recorded four attempts and could not say the frame it kept had a
dialog over it.

### The one permitted dismissal had to actually execute (CI-004)

`CI-002` gave the harness an allow-list of one obstruction and one button. Hosted run **13**
(`31697590198`, `d830cac8`) proved it could identify that obstruction and then **not press it**,
twice, byte-identically, producing **zero frames**:

```
Refusing to capture 01-home: Application Not Responding: com.android.systemui is dismissable
but 0 Wait affordances were found among android:id/button2, android:id/aerr_wait.
```

**The defect in one sentence: the verdict meaning *this is clearable* was the only one that could not
be retried, and the verdict meaning *this is unrecoverable* was retried patiently for twenty
seconds.** `requireAppOwnsScreen` polls a `Blocked` screen forty times; `dismissSystemAnr` inspected
the accessibility tree once and threw. Three conditions read as that zero — no searchable tree, a
null root, `FLAG_REPORT_VIEW_IDS` not in effect — and none of them means the dialog has no `Wait`
button. The guard's own KDoc had already named the third as *"a recoverable run failing as an
unrecoverable one"* and guarded exactly one route to it.

**Only the zero case became patient.** Two or more candidates still fails immediately and is never
resolved by choosing; making ambiguity retryable would be a worse bug than the one being fixed, and
it has its own negative control. `AnrDismissalPolicy` joins `focusVerdict` and `RenderedContentPolicy`
in `src/screenshotPolicy/java` — it was the last capture-time decision that was not a pure function,
which is why it reached `main`.

**Patience is not the fix, and the evidence is what says so.** The diagnostics artifact showed the
ANR dialog had been on screen for **63 seconds** before the test process started. A deterministic
zero absorbed by a 20-second deadline is forty identical failures and then the same failed run. So
**reachability leads**: the observation enumerates the windows the automation can see and searches
the focused one — the same notion as the `mCurrentFocus` line the classification came from — falling
back to `rootInActiveWindow`. Run **14** on the same commit recorded `pressed=android:id/aerr_wait`,
`click_accepted=true`, twice, which eliminates "the dialog has no Wait button" and leaves
reachability as the difference between the two runs.

**The plan's own identification rule was wrong, and the first run with the new record said so.** It
named matching the classified window title. Run **21** (`31771876172`, `8e9d8860`) reports what
`AccessibilityWindowInfo.getTitle()` actually returns for that dialog:

```
searched_windows=[id=11 type=3 title=System UI isn't responding active=true focused=true matched=focused]
```

`System UI isn't responding` is the **translated user-facing** string; `focusVerdict` classifies
`Application Not Responding: com.android.systemui`, built by AOSP from a process name. The title rule
matched nothing on either frame and `matched=focused` carried the whole fix. It was deleted rather
than repaired — repairing it means comparing against a localised string, which is exactly what
`CI-002` refused. **Both surviving rules read structural facts, never text**, and `inspect()` takes no
title at all so there is no parameter through which text could return. The record naming which rule
fired is what made a wrong mechanism visible in one run instead of surviving as a rule nobody noticed
never matches.

**Run 22 then showed the fallback carrying a frame, which is why there are two rules and not one.**
Frames 01 and 02 found the dialog by input focus; frame 03 found it by `rootInActiveWindow`, because
the dialog's window was **absent from `uiAutomation.windows` entirely** — the enumeration listed only
an unfocused system window and Webora's own — while the active-window read reached it at
`id=15 pkg=android` and pressed it. That is run 13's shape from the other side, and either rule alone
would have failed a frame in that run. It also confirms the package allow-list from the field: the
AOSP dialog's root really does report `android`.

**What the hosted runs do not prove.** Both reached the dialog on `poll=1` with
`interactive_windows=true`, so the patience half was never exercised and `rootInActiveWindow` may
have sufficed on some frames. The retry is justified by run 13's recorded failure and pinned by unit
tests — not by a green run. Instrumented evidence, never a gate claim, as `CI-002`, `CI-003` and
`CI-005` each recorded theirs.

**`view_ids_present` says `not gathered (the dialog was cleared)` on a press, never `[]`.** The walk
runs only on the refusal path it exists for, and an empty list must not be confused with a list
nobody collected — `DEVX-002`'s `tiles=0` lesson, one artifact along.

**Enumerating windows is where this could have gone badly, and the package check is what stops it.**
`android:id/button2` is the negative button of *every* `AlertDialog`, Webora's consent dialog
included. A search across all windows that fell back to "whatever has a button2" would press a Webora
dialog whenever the system dialog's tree was unreachable — silently, on a green job. That is `CI-002`'s
refusal arriving through a mechanism `CI-002` never had, because `rootInActiveWindow` was one tree.
Every searched root must report a package in a closed set of two OS-supplied names, and that check
lives in `AnrDismissalPolicy` with the negative controls — a security control added in `androidTest`
would have been a fresh instance of the very defect this ticket removes. **A new window
identification rule is only ever an identification rule; it may never be a reason to press.**

**`MAX_DISMISSALS` counts presses, not looks.** `"survived N dismissals"` is false if nothing was
pressed, so a "not yet" costs no budget. Conflating the two would reintroduce this bug with a larger
constant: two looks instead of one. And `SETTLE_TIMEOUT_MILLIS` was not raised — raising the deadline
instead of fixing the lookup is the shortcut `CI-003` refused for its threshold.

**Every zero carries a different reason, and `dismissal-<label>.txt` records what was actually
there.** The old message named the ids the harness *looked for* and never the ids that were
*present*, which is why two hosted runs and a log download produced no more information than the
first assertion. `CI-005`'s standing constraint still binds: this may only ever make the harness
refuse **later**, never make it press something it would refuse today.

**`./gradlew detekt` does not analyse `androidTest`** — verified by appending a 140-character line to
an instrumented file and watching the gate stay green, while `:app:detektDebugAndroidTest` reports it
at once. That is `CI-003`'s "the gate never compiles `androidTest`" one rule further along. Not fixed
here; wiring the variant task in surfaces pre-existing findings across the whole instrumented suite
and needs its own ticket.

### Browser-owned design tokens (UX-002)

`SiteSkinTheme` gave *websites* six colour roles, a dark projection and a WCAG guard;
`MainActivity.kt:38` gave Webora `MaterialTheme {}` and Material's baseline purple. `ADR-013` chose
Direction A and this builds it. The two layers are now mirror images, and the asymmetry between them
is the whole design:

| | `SiteSkinColorScheme` | `WeboraColorScheme` |
|---|---|---|
| Input | a trusted `SiteSkinConfiguration` | **nothing** |
| Guard | runtime, over every pair it forms | a JVM test, over every pair that exists |
| Who changes a value | the website, within the guard | a commit |

**The browser palette has no runtime guard, and that is the stronger position.** `SiteSkinTheme`
corrects a website's colours because they arrive from the network and can fail. A compiled token
below its target is a bug to fix; correcting it at runtime would let it ship silently repaired with
nothing red anywhere. Do not add `guardContainer` to the browser side.

**`C2` — no manifest value reaches a browser token — is tested from both sides, and the negative
controls proved both are needed.** A runtime sweep projects real manifests and asserts no browser
token moved (plus that the SiteSkin scheme *did*, so a sweep that stopped exercising anything fails).
A source scan asserts nothing under `design/` imports or names the website side. A reference to
`SiteSkinTheme` from the palette file fails only the scans; a `var` token written from
`SiteSkinTheme.from` with no reference in `design/` at all fails only the sweep. The sharpest version
of this leak is not a wrong background — it is a manifest-derived colour on the identity chip, which
is `HARDEN-002`'s impersonation surface arriving through colour instead of text.

**"No Material default" is a closure assertion, not a promise to pass 48 arguments.** Every colour in
the derived `ColorScheme` must be a value `WeboraColorScheme` declares; same for `Typography` sizes
and `Shapes` corners. `ColorScheme`'s primary constructor turns out to have **no default values**, so
`ColorScheme(...)` is used rather than `lightColorScheme(...)` and omitting a role is a compile
error — but the test is what catches the likelier mistake, a literal, which is a colour nothing has
measured. Two Material `Shapes` roles are `internal` and unsettable by any public API; they are
excluded structurally (Kotlin mangles `internal` JVM names with `$module`), never by adding 32 and
48 dp to `WeboraRadius` to make a test pass.

**Completeness is reflected, not listed.** Every declared colour role must appear in the contrast
table or in a named decorative exemption **with a written reason**. A hand-listed table is correct the
day it is written; this one covers roles that do not exist yet. `divider` and `scrim` are the only
exemptions.

**The identity chip's 1.27:1 / 1.29:1 separation from ground is asserted as a decision, not a
defect.** `ADR-013` ruled it correct — a status display is not an interactive control under WCAG
1.4.11, and identity is carried by text at 10.49:1 plus a glyph and a word. The test pins it so
"fixing" it is a deliberate reversal.

**Icons are ten bundled vectors, and the budget is a separate assertion from the inventory.** The set
is what Direction A draws, not `C6`'s pre-selection list of eight — that named `stop`, which A does
not draw, and omitted `search`, `more` and `warning`, which it does. Stroke-only and one colour each,
because `Icon` tints the whole painter: a mixed fill/stroke icon tints to one colour and loses the
distinction it was drawn with. `UX-005` raises the budget deliberately when the SiteSkin semantic
icons replace `SiteSkinChrome.kt:135`'s Unicode glyphs; a second ad-hoc icon mechanism is the thing
`DEVELOPER_PLAN.md` sequences that ticket behind this one to prevent. No Gradle dependency was added,
and a website cannot add a file to `res/drawable`.

**`values-night/themes.xml` is about the window, not about Compose.** `isSystemInDarkTheme()` already
returned the system setting; `android:Theme.Material.Light` is light in every configuration, so the
first frame and the system-bar regions disagreed with the palette.

**A gate hole this ticket walked into, now closed.** `BrowserSurfaceConventionsTest`'s wrapper
exemption was a plain `contains("fun WeboraButton(")` over the file text. `WeboraIconButton`'s KDoc
explains that the raw Material import is allowed only in the file containing that declaration — and
thereby exempted its own file from the rule it was describing, letting an `IconButton` import outside
the permitted file pass a green build. The exemption is now a line-anchored declaration match, with a
test pinning both directions. The only way to trip it was to add a second wrapper, which nothing had
done since `A11Y-001` wrote the rule.

`WeboraChrome` deliberately declares **no** touch-target token. `MINIMUM_TOUCH_TARGET` has one name
and one owner; a second name would sit exactly where someone would reach to shrink a target so a
design fits.

Surface structure is untouched: `UX-003` rebuilds the chrome and `UX-004` Home, onboarding and
settings, both consuming this layer rather than re-deciding it. One handoff is recorded in
`docs/plan/UX-002.md`: Direction A draws unselected dock slots at 38% opacity, which reads as
disabled and can fall below 3:1 whatever token is underneath — `UX-003` should treat unselected
navigation state as a colour role, not an alpha multiplier.

### Regular browser chrome keeps identity outside the address (UX-003)

Direction A is now the regular browser's actual structure: a 52 dp editable address pill, a
separate tonal identity chip and a 60 dp floating navigation dock. The split is the security
mechanism, not a styling preference. `BrowserChrome` receives `BrowserState.addressText` for the
field and independently derives `SecurityPresentation` from the committed `BrowserMode`; an edit
toward another origin must leave the visible and tagged committed-origin chip unchanged.

The negative control is deliberately a disagreement. A happy-path assertion where address and
identity both say `example.com` cannot tell a committed-origin implementation from one that copies
editable text. `BrowserChromeTest` edits the field to `attacker.test` and still requires
`Secure · example.com`; `BrowserChromeContractTest` separately rejects putting
`BROWSER_SECURITY_TAG` on the field. Keep both halves: runtime behaviour and source structure fail
under different regressions.

Regular navigation stays outside `BROWSER_CONTENT_TAG`. That tag is the screenshot harness's page
measurement rectangle, so moving the dock into it would let browser-owned pixels count as rendered
website content. The source contract carries an intentionally broken nested example as its negative
control.

Direction A's sketch used 38% opacity for unselected dock slots. The implementation does not:
enabled controls use the opaque `onSurfaceVariant` browser role, while only genuinely unavailable
history commands use disabled state. An opacity convention for enabled navigation would read as
disabled and could fall below the 3:1 non-text target whatever colour sat underneath it.

The sketch's reload/stop slot remains reload. `BrowserWebViewController` has no stop contract, and a
stop glyph wired to reload is a false affordance. Adding stop later starts with a real controller
capability and state transition, not with changing the icon.

`BrowserErrorPage` shares the browser token/icon system and keeps its old recovery contracts:
bounded registrable-domain text, kind-specific browser copy, retry enabled only with a retry URL,
and stable retry/home tags. `BrowserStatusRegion` remains a persistent sibling before the content
rectangle; loading/completion are polite and failure is assertive.

### The inspector lives in the menus (DEVX-003)

`DEVX-001`'s panel was correct and its **affordance** was not: a floating `SiteSkin inspector` pill
drawn over every screen in every debug frame, putting a developer tool in the product evidence people
judge Webora by. That alone was reason enough, and it is the reason that survived.

The second reason did not. `CI-003` handed this ticket a measurement hole — the overlay's pixels
supposedly landing inside `BROWSER_CONTENT_TAG` and helping a blank page clear the rendered check —
and removing the overlay proved there was no hole: the differing fraction is bit-identical with the
affordance and without it. **The ticket's own evidence refuted its second premise, which is the
outcome to want from evidence.** Do not restate the two-buttons-clear-1% argument; it was arithmetic
about a rectangle the affordance was never in.

**A screenshot mode was refused, and this is the load-bearing decision.** Hiding the affordance while
the harness captures is the obvious shortcut, and it is disqualified on exactly the grounds `CI-002`
refused a dismiss-whatever-is-in-the-way loop: a frame is evidence *because nothing arranged the
screen for the camera*. A harness that suppresses real UI for the photograph produces a picture of a
build nobody can run. The affordance is absent from the frames because it is not composed there.

**Two interactions, in both modes, which is what chose the menus.** `Settings` is itself two deep in
each mode — regular reaches it through `AddressBar`'s dropdown, integrated through `SKIN-003`'s
closed browser section — so an entry inside `PrivacySettingsScreen` would have cost three, in the
very mode a site owner debugging a manifest is in. The entry instead joins the menu that already
exists on each surface.

**One expression, two readers, and the first negative control proved it was needed.**
`browserMenuCommands()` builds the offered list from `SITESKIN_INSPECTOR_AVAILABLE`, never
`BuildConfig.DEBUG`, and a release build must not draw an entry whose handler does nothing — filter
the list, do not no-op the branch. Regular mode renders that same list rather than a hardcoded pair,
so the two modes cannot drift, and `browserMenuLabel` keeps one command from acquiring two names.

The constant is read *inside* the function, so make availability a **parameter** with the constant as
its default: AGP 9.1 creates only `testDebugUnitTest`, where the constant is always `true`, so a test
that cannot pass `false` cannot tell the correct implementation from `BrowserMenuCommand.entries`.
That is the same thin-wrapper-over-pure-function shape the repository uses for Android-touching code,
for the same reason.

Release variants stay unreachable by two independent mechanisms: the command is absent from the
offered list, and the release `SiteSkinInspectorHost` ignores its visibility flag. Neither leans on
the other, and `assertInspectorAbsentFromReleaseVariants` still checks compiled output for the
panel's absence **and** the stub's presence.

### Refreshed home, onboarding, and settings (UX-004)

The remaining browser-owned entry and settings surfaces consume `WeboraTheme` while preserving their
existing state/callback seams. Home still resolves typed and compiled suggestion destinations at the
navigation boundary. Onboarding remains a saveable three-page scrollable flow. Privacy settings uses
one toggleable row for global SiteSkin state and renders each complete `SiteOrigin.canonical` as
wrapping text beside a compact reset action whose accessible description includes that same origin.
Manifest text, colours, icons, and actions have no path into these surfaces.

### Tabs are browser-owned isolation boundaries (BROWSE-006)

`BrowserSession` is a non-empty ordered set of at most eight independently identified tabs. Create,
select, close, final-tab replacement and addressed state updates belong to that pure reducer; UI and
WebView callbacks must carry a tab id rather than updating whichever tab happens to be active when
they arrive. This is a security boundary: a retained background renderer can navigate after a tab
switch, and its observation must never replace the selected tab's URL, `BrowserMode`, navigation
capability or identity chrome.

Each live tab owns one `BrowserWebViewController` and retained hardened `WebView`. Selection
detaches/reattaches without loading so live back/forward history stays with the tab. Close destroys
that renderer, and disposing `BrowserScreen` destroys every remainder. Browsing-data clearing fans
out across all live controllers.

Saved state is deliberately weaker than live state. The versioned snapshot stores only browser tab
ids/order/selection and either Home or one bounded committed HTTP(S) URL. It never stores a
`SiteSkinConfiguration`, manifest, page/form content, editable address, error, pending native
action, asset or WebView bundle. Every restored page starts in regular mode and traverses discovery,
consent, validation and exact-origin activation again. Malformed, duplicate, unsupported-scheme and
over-limit input is bounded or dropped; no valid entries becomes one fresh Home tab.

The switcher is the same browser-authored surface from Home, regular and integrated modes. Its
labels derive only from Home/Page fallbacks or the browser-observed registrable domain—not manifest
branding or editable address text—and the disabled eight-tab limit is visible rather than an
eviction or silent refusal. `UX-011` may move the entry point into a persistent shell but must reuse
this model and must not create a second tab authority.

### One glyph stood for five failures (NET-004)

`NET-003` makes a browser-generated monogram the correct output of every non-cancellation
brand-asset failure, and for the user it is: an unreachable logo must never break browsing. The cost
only became visible on the reference integration, where a declared, correctly served `512 × 512` PNG
rendered as a `B` in hosted runs 11 and 15. One glyph stood for "no logo declared", "the CDN answered
404", "the bytes are not the type the header claimed", "the image is over the cap", "the decoder gave
up" and "it has not arrived yet" — six different things to fix behind one symptom.

**The tool built for this could not answer it, and finding that out was the first result.**
`DEVX-001`'s inspector reported `Brand asset: MONOGRAM` and stopped; `BrandAssetFetchResult.Rejected`
was a bare object while the manifest path had carried `FetchRejection` since that same ticket. So
`BrandAssetRejection` (eight transport reasons) and `BrandAssetStage` (nine pipeline stages) exist to
close the asymmetry, and `BrandAssetLoaderTest`'s matrix is the proof: before this ticket every one
of its seven refusals asserted the same `Monogram("B")` and no test could tell any two apart.

**The measurement is the ticket.** Hosted run 16 (`31725858080`, `f982f1a4`) recorded
`stage=TRANSPORT_UNAVAILABLE` in 891 ms with no rejection and no status — a decision, not a race, and
none of the four causes the backlog had listed. The logcat named it to the second: the emulator's
Wi-Fi left `CONNECTED` at `17:41:33.893`, in the same second Allow was clicked, while the manifest
fetch ten seconds earlier had gone out over a working network.

**The refusal was correct; its permanence was not.** `BrowserScreen` keys the load on the trusted
configuration instance, and `BrowserState.forObservedOrigin` deliberately returns the *same*
`Integrated` instance for every same-origin page start — so the key never changes, the effect never
re-runs, and nothing asks again. The network returned 6.4 s later and that origin showed a monogram
for the rest of the visit with the logo one request away. That is what a site owner's users get on
any connection that blinks at the wrong moment.

So `TRANSPORT_UNAVAILABLE` is retried — three attempts, 1 s then 2 s apart — and **nothing else is**.
The exclusions carry the argument and each has a test: a rejection means the server answered and the
browser declined, so retrying hammers a site whose logo legitimately 404s and changes nothing; the
same bytes decode the same way; an undeclared logo has nothing to request. `NET-003`'s caps,
allow-list, same-origin recheck and monogram fallback are untouched, and its tests pass unedited —
this changes how many times the browser asks, never what it will accept. Runs 17 and 18 (`ea57ef5d`) then
recorded `stage=DECODED httpStatus=200 pixels=512x512 attempts=1`, and run 18's frame shows the
reference integration's flower in the 40 dp slot beside `Secure · denrzv.github.io`. `attempts=1` in
both: neither run's network blinked, so the retry is justified by run 16's recorded failure and
pinned by unit tests — not by a hosted run that never exercised it.

**The recorder is process-scoped now, which is what its own KDoc always claimed.** It was built
inside a `remember`, so a configuration change discarded whatever a developer was mid-way through
reading — and, more to the point here, instrumentation could not see what the running browser had
decided. The screenshot journey reads *that* recorder rather than running a second loader of its own;
a second copy is a second thing that can disagree.

**The reason vocabulary is closed, and a `BrandAssetTrace` can hold only numbers and closed enums.**
A reflective test enforces it, so a field added later is covered without anyone remembering. A website
can cause a stage to be *selected*; it can neither supply nor phrase one. The 40 dp slot stays
decorative in the semantics tree — the outcome must never become an accessibility-visible property of
it, which is why the harness reads the recorder instead.

`BrandAssetCoordinator` is gone. It had a test and no callers while `BrowserScreen` reimplemented it
inline, so `NET-003`'s superseded-work guard ran where the gate cannot reach it and the tested class
ran nowhere. `publishesBrandAsset` is that guard as a pure function; the trace is recorded whether or
not it publishes, because a load dropped for being superseded is exactly what a developer needs to
see and the guard is what would hide it. Note that `===` and `==` are the same comparison there today
— `SiteSkinConfiguration` has no `equals` — so the operator's negative control is deleting the guard,
not relaxing it. Both facts are written down where someone will hit them.

**The journey waits for the pipeline to decide, and asserts after the capture, not before.** Waiting
for a state the app reaches on its own is not staging it — `DEVX-003`'s refusal of a screenshot mode
still stands, and this can only make the run refuse more, per `CI-005`. Asserting after the frame is
saved means a failing run still publishes the picture of what went wrong.

### The pill was a control value sitting on a container role (UX-009)

Hosted runs **14** and **15** photographed the first-use consent dialog as a vertical stadium with
`Allow https://denrzv.github.io to customise Webora?` clipped to `ow`. `ADR-011`'s enforcement point
and `HARDEN-002`'s canonical origin, losing characters, on the screen a user reads before granting a
website control of the chrome.

**It took four facts, and no single one of them looks like a bug:**

1. `WeboraRadius.PILL` is `999 dp` — correct, and `UX-002` says why.
2. `WEBORA_SHAPES` put it on Material's `extraLarge` role.
3. `DialogTokens.ContainerShape` is `CornerExtraLarge`, so **every** `AlertDialog` reads that role.
4. `CornerBasedShape.createOutline` does not *reject* an over-large corner. It scales each adjacent
   pair by `minDimension / sum`, so 999 dp on a 280 dp dialog resolves to exactly 140 — half the
   width, the largest radius the box admits, which is the one that rounds its sides away entirely.

Then `Surface` clips its content to that shape, and the heading is not overlapped but *removed*.

**The old assertion could not have caught it, and the reason generalises.** `every shape corner comes
from the compiled radii` asks where a value came from; `999 dp` is a declared token, so the defect
satisfied it. Fitness is a different question from provenance, and the answer changes with the box:
999 dp is a good number until something 280 dp wide reads it. The new assertion therefore runs the
real `createOutline` at Material's `DialogMinWidth` and bounds the *resolved* radius at a quarter of
the width — inside the degenerate half, since at half a side is already fully round. A second,
structural assertion names the cause directly, so a returning pill fails as itself rather than as a
number that happens to be too big.

`EXTRA_LARGE` is `28 dp` and deliberately not an alias of `LARGE`'s 20: a scale whose top two roles
are the same number invites the next reader to collapse them, and the collapse lands back on the role
`AlertDialog` has to read. **A control that wants a pill names `WeboraRadius.PILL` itself** — the
address field on Home now does, as `BrowserChrome` already did.

**The instrumented suite was green throughout and is not the gate.** Clipping happens in the parent's
draw while the semantics tree keeps the node's full text and unclipped bounds, so
`assertIsDisplayed()` passes over a heading that is not there — `CI-003`'s "semantics precede pixels"
arriving one layer up, in a suite rather than a harness. `SiteSkinConsentDialogTest`'s new case
documents `HARDEN-002`'s requirement and says in its own comment that it cannot detect the
regression. It also does not force 360 dp: an `AlertDialog` occupies a separate window that a
composition size override does not size, and the screenshot journey's emulator is that width anyway.

The blast radius was every dialog in the app — consent, external-URL, external-navigation,
clear-browsing-data and the debug inspector — because a default reaches everything that does not
override it. Fixing the role fixed all five; a `shape =` argument on the consent dialog would have
fixed the frame and left the collision live.

### Expressive SiteSkin geometry is browser policy (UX-013)

M9's richer composition does not widen the manifest surface. `ExpressiveSiteSkinPresentation` is
the one app-layer projection: it reuses the already contrast-guarded `SiteSkinColorScheme`, selects
light/dark from an explicit browser/platform input, and maps an explicit accessibility input into a
closed standard/reduced motion policy. It contains no dimension, shape, URL, asset, navigation,
action, callback, composable, or animation value. `SiteSkinColorScheme` remains the complete
website-influenceable visual vocabulary; `WeboraTheme` remains separately compiled.

The curved header and floating dock own their curve, depth, minimum size, gutters, rounding, dock
inset, and fixed slot order. A caller may provide local composable content, but cannot provide those
geometry decisions through the presentation model. The dock's painted surface is inset inside a
full-width ownership container rather than relying on UX-015 to remember caller padding—the
floating separation is policy, not decoration. The curve reserves its depth below content, and the
dock inherits the one 48 dp browser target contract rather than defining a shrinkable duplicate.

The primitives deliberately do **not** read `WindowInsets.safeDrawing`: `BrowserScreen` consumes it
once for the whole browser, and a second read would double-pad future integrated chrome. They are
also deliberately unwired in this ticket. UX-014 owns filling the header with browser-observed
canonical domain/TLS identity and fixed browser controls; UX-015 owns filling the dock/hub with
browser commands and trusted typed site items. Neither ticket may move identity, callbacks, order,
geometry, inset, or motion duration into manifest-derived state merely because the containers now
accept site colours.

### Expressive integrated top chrome preserves a separate browser identity (UX-014)

Protected integrated mode now fills `ExpressiveSiteSkinHeader` with a fixed browser-authored Back/
brand row followed by an unconditional canonical domain/TLS surface. The outer curve and site text
use only the closed, contrast-guarded `ExpressiveSiteSkinPresentation`; the identity and Back
sub-surfaces use `WeboraTheme` tokens, local icons/labels, observed state, and explicit controller
callbacks. A manifest therefore cannot suppress, relabel, reorder, restyle, enable, or dispatch
either browser contract, even though its validated colours paint the containing header.

`BrowserScreen` constructs this presentation only for the active tab's exhaustive
`PROTECTED_INTEGRATED` projection. Exact-origin departure and tab switching still remove integrated
decoration before the next projection, and accepted brand assets retain their configuration-
identity publication guard and immediate monogram fallback. Loading and error regions remain outside
the header. The header does not read global safe-drawing insets; the browser shell consumes those
once.

Animator duration scale is read through a thin app-layer wrapper. A missing, invalid, non-finite, or
non-positive value conservatively selects the closed reduced-motion policy; a manifest has no input
to that decision. The header currently needs no decorative animation, so both policies compose the
same required slots immediately. Future decoration must consume this policy rather than add its own
platform read or manifest-controlled timing.

Tabs and overflow remain reachable through their existing browser-owned surfaces in UX-014. They
are intentionally not duplicated in the top header: UX-015 owns moving them into the fixed floating
browser dock and projecting validated site navigation into the separate SiteSkin hub.

Reduced motion is a closed policy before it is an animation. UX-013 introduces no fake transition
just to exercise it; the first downstream ticket adding a real decorative transition must map
`REDUCED` to an immediate state change. Security identity, origin teardown, controls, and input
availability are never animation participants.

### The integrated dock is browser-owned; site commands live in a typed hub (UX-015)

Protected integrated mode's floating dock has exactly five compiled commands in fixed order: Back,
Forward, brand hub, Tabs, and More. Their local icons, localized labels, enabled state, 48 dp targets,
Webora-token sub-surfaces, geometry, order, and callbacks are browser policy. Site navigation count,
labels, colours, or actions cannot remove, imitate, reorder, restyle, or dispatch them. Back and
Forward read only the selected tab's observed history and call its current controller; Tabs reuses
the one browser tab switcher.

The brand control and More open one browser-owned SiteSkin hub. The hub projects the current trusted,
bounded `SiteSkinChromeModel` into separately headed navigation and quick-action groups and then a
distinct Webora-controls section. Rows emit the original trusted `NavigationItem` to the existing
`ActionResolver`; no raw URI, intent, controller, tab id, or generic command is stored or introduced.
Only navigation rows publish selected/not-selected semantics—quick actions and browser commands are
actions, not routes, and must not claim selection state.

Hub state is visibility only. Page generation, active tab/configuration change, SiteSkin
deactivation, or loss of `PROTECTED_INTEGRATED` authorization closes or removes it before another
projection can dispatch. Regular and Home continue to use `BrowserNavigationShell`; the old
integrated persistent bottom navigation and over-content quick-action launcher are no longer part of
the protected projection.

### The Bloom live journey is the cross-repository integration contract (DEMO-005)

The canonical instrumentation story now exercises the deployed site as a normal black-box website:
Home → consent → expressive storefront → Happy Days product → browser Back/Forward → native hub →
regular origin. Remote page text may locate the accessible product link, but it never proves trust;
browser-owned expressive identity/dock tags before and after history traversal and their required
absence after exact-origin departure remain the authority. UiAutomator is instrumentation-only and
must not be replaced with a production JavaScript bridge or a Webora-specific DOM hook.

The checked-in Bloom fixture, normalized expected result, specification example, route corpus, live
selectors, and `denrzv/bloom-flowers` source manifest are one coordinated integration artifact. The
completed M9 vocabulary is Home, Catalog, Cart, Profile plus Call. The pinned byte digest is a
negative control: a cross-repository vocabulary change must update and validate all representations,
not merely make a hosted test accept whichever labels happen to be live. CI-009 still owns expanded
screenshots, contact-sheet totality, and two consecutive cold accepted runs; DEMO-005 owns whether
the journey and repositories tell the same story.

### Ordinary browsing is canonical evidence too (CI-007)

The hosted journey does not stop when Bloom Flowers reaches integrated mode. It leaves through the
fixed browser-owned Back affordance, submits the reserved `example.com` destination through Webora's
visible address input, and captures `04-regular-browsing.png`. The frame is accepted only after the
regular security identity and navigation shell exist and integrated identity, bottom navigation,
and quick actions are all absent. That layer-set assertion is the evidence that `UX-012` removed the
prior integration; page title, prose, decoration, and manifest data are never acceptance signals.

### Hosted transition prerequisites fail at their action seam (CI-008)

The canonical journey asserts a browser-owned transition control's displayed and enabled semantics
immediately before invoking it. A click followed by a destination timeout is not equivalent: it
hides whether the action was unavailable or whether the resulting transition failed. Such
prerequisite assertions may only make evidence refuse earlier; they must not inject controller
state, manufacture Home, weaken capture policy, or replace hosted runtime evidence.

Frame 04 uses the same `ScreenEvidenceGuard` ownership/rendered policy as the integrated page. It
does not add a screenshot mode, direct controller injection, new dismissal, deadline, pixel
threshold, or exclusion. The collector and contact-sheet production remain cardinality-independent:
tests pin the current four-frame inventory and order, while the workflow compares the number of
actual PNGs with the number of tiles it composed. Captions still come only from filenames, and
diagnostics remain in the separate artifact.

### The regular browser dock uses six equal browser-owned slots (UX-016)

Home and ordinary browsing share one neutral Webora pill whose fixed order remains Back, Forward,
Reload, Home, Tabs, More. The inner row fills the pill and each command is centered in one equal
weighted slot; the first and last slots meet the dock edges, producing symmetric outer geometry
without per-icon padding. The slot wrappers do not replace or shrink `WeboraIconButton`, so the one
48 dp browser target contract remains the interaction owner.

This distribution is compiled browser policy. Page content, current origin, SiteSkin configuration,
navigation items, labels, colours, or action counts cannot influence slot count, order, weight,
enabled state, callbacks, shell padding, or system-inset handling. Protected integrated mode retains
its separate expressive dock and native hub. Compose geometry evidence pins equal widths and center
spacing plus both dock edges at 320 dp; presence assertions alone are not evidence for distribution.

### Expressive evidence is an ordered browser-owned contract (CI-009, pending hosted acceptance)

The M9 journey's canonical inventory is eight chronological frames: native Home and consent;
rendered Bloom storefront and Happy Days product; storefront restored by browser Back; product
restored by browser Forward; the native SiteSkin hub; and ordinary `example.com` after exact-origin
teardown. Zero-padded filenames supply contact-sheet order and captions. JVM tests pin that complete
inventory while production collection/composition remain cardinality-independent.

Remote accessible text can locate and synchronize a normal web link, but cannot authorize a frame.
Product/history captures wait for the clickable Happy Days link to disappear or return so persistent
chrome and stale pixels cannot masquerade as the destination. Acceptance still requires closed
browser identity/header/dock tags, displayed/enabled browser actions, native hub tag and browser
section headings, final regular-layer mutual exclusion, and the unchanged full-device ownership,
rendered-content, and candidate-verdict path.

The implementation and all locally available gates are complete, but CI-009 is not accepted yet:
two consecutive cold hosted runs of the final commit must publish and pass human inspection of all
eight frames and their total contact sheet. A managed checkout without a push-capable remote or
GitHub authentication cannot replace that evidence with compiled instrumentation.

### The integrated SiteSkin dock is one branded browser boundary (UX-017)

Protected integrated mode's five-command dock remains compiled browser policy—Back, Forward, Brand
Hub, Tabs, More—but its one continuous pill now uses the guarded SiteSkin `secondary`/`onSecondary`
roles without neutral per-command tiles masking the brand. The center Hub is the sole asset-bearing
command: a fixed 52 dp browser-owned primary circle renders only the current configuration's existing
bounded `BrandAsset`, with stable monogram/local-flower fallback. Its pixels are decorative; the
localized browser action semantics and callback remain on the enclosing control. Back, Forward,
Tabs, and More still accept only bundled drawables.

Do not add a second asset loader, raw resource lookup, component-specific manifest color, or generic
command model to extend this treatment. Site rows continue through the closed semantic icon map and
typed `NavigationItem` dispatch. The weighted WebView region and dock are measured `Column` siblings,
so the browser reserves the actual dock height; the outer shell consumes `safeDrawing` once. Adding
DOM padding, a fixed Bloom offset, or a second inset read would violate that ownership and likely
double-reserve space. Exact-origin/configuration teardown makes the branded dock structurally
unreachable and the existing asset publication identity guard prevents late identity leakage.
