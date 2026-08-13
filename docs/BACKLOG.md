# Backlog — ticket scopes

Every ticket in the roadmap, with the scope and the acceptance criteria that are already decided.

**These are not PRDs.** `/idea <TICKET>` instantiates the real PRD from the template when the ticket
starts, using the entry below as its input. Pre-writing full PRDs for future work would produce
artifacts that are stale before anyone reads them — the AIDD flow deliberately writes the PRD at
ticket start, when the preceding tickets have already taught you something.

Every ticket's acceptance criteria end with the same final item, per repo convention:
`bash scripts/pre-commit-check.sh` passes.

---

## M1 — SiteSkin API

**`SPEC-001` SiteSkin Manifest v1.0.** ✅ **Done.** Published the normative specification, JSON
Schema, machine-readable diagnostic registry, and conformance corpus. The corpus is wired into
`:siteskin-core:test`, and the full repository guardrail plus `:app:assembleDebug` have now been
verified on an Android-SDK-equipped checkout.

**`SPEC-002` Versioning and compatibility policy.** ✅ **Done.** Accept `1.x`, reject `2.x`. Unknown
fields ignored with `SS-W-FIELD-UNKNOWN`. Documented deprecation path and what constitutes a
breaking change. Fixtures for `1.0`, `1.1`, `2.0`, missing version, malformed version.

> **What actually shipped**, since the scope moved once the ticket met the corpus. The `1.0`, `1.1`
> and `2.0` fixtures this entry asks for already existed — `SPEC-001` delivered them as
> `valid/minimal.json`, `valid/forward-compat-1.1.json` and `invalid/version-major-2.json`. Rather
> than duplicate them, `SPEC-002` added `invalid/version-missing.json` and
> `invalid/version-malformed.json` (the two genuinely absent), plus two the entry did not anticipate:
> `invalid/version-major-2-alien.json`, a `2.0` document the `1.0` schema rejects outright, which is
> the only fixture that can prove the version layer runs *before* the schema; and
> `invalid/unknown-field-1.0.json`, which pins that the unknown-field policy is version-independent
> rather than a courtesy extended to future minors.
>
> The boundary itself moved out of the document corpus entirely, into `spec/versions.json` — a
> 17-row decision table over every spelling at the edge. Version handling is a decision on a scalar,
> and near-identical manifests differing in one field would have cost more and tested less.
>
> Two defects were found and fixed inside the ticket, both in the *published schema*: `01.0` and
> `1.0` were two spellings of one version, and — the sharper one — every `^…$` pattern in the schema
> accepted a **trailing newline**, because `java.util.regex`'s `$` matches before a final line
> terminator while the ECMA-262 semantics JSON Schema specifies do not. `"schemaVersion": "1.0\n"`
> validated. Both are recorded as breaking changes in `SPEC.md` §4.5, taken deliberately inside the
> free-change window that section defines and closes.

**`CORE-002` ✅ DTOs and parsing.** kotlinx.serialization DTOs mirroring the schema. Byte-size guard
*before* parse. Unknown fields ignored with a warning. Acceptance: every `valid/` fixture parses;
malformed JSON yields `SS-E-PARSE`; a 129 KB payload yields `SS-E-SIZE-EXCEEDED` without being fully
read. Parse success must not produce a trusted type — the DTO is inert.

**`CORE-003` ✅ Schema validation.** Parsed JSON → `ManifestValidationResult(errors, warnings)`
carrying the spec's diagnostic codes. The production validator executes the version table and every
parsable corpus document, short-circuiting unsupported majors before structural validation. Its
layer-specific seam accepts `JsonElement`; the completed `CORE-002` byte parser, DTO mapping, and
unknown-field discovery feed the shared total-validation pipeline. Security allow-lists deliberately
remain `CORE-004`.

**`CORE-004` ✅ Security validation and normalization.** The heart of the trust boundary. Origin
binding for every URL, scheme allow-list, icon-name allow-list, asset same-origin check, colour
parsing with WCAG AA contrast correction, limit clamping with truncation warnings. Produces
`SiteSkinConfiguration`, constructible **only** here. Acceptance: cross-origin `internal_url` is
rejected; each denied scheme is rejected; a hostile colour pair is corrected and flagged; over-limit
collections truncate. Needs a negative control per protection.

**`CORE-005` ✅ Action model and resolution.** Nine allow-listed types → sealed `ResolvedAction`.
Unknown type drops the item and keeps the manifest (`ADR-007`). Acceptance: each type resolves to
the right typed result; unknown type drops exactly one item; `phone` resolves to a dial intent
description, never a call.

**`CORE-006` ✅ Navigation active-state matching.** Exact path, then longest glob (`/cart/**`).
Deterministic tie-breaking. Acceptance: exact beats glob; longest glob wins; no match yields no
selection rather than a default.

**`SPEC-003` ✅ `siteskin-lint` CLI.** Fetches a live origin's manifest and validates it with the same
`:siteskin-core` code path the browser uses. Exit 0 = will activate. Prints diagnostic codes.
Acceptance: running it against `bloom-flowers` exits 0; against each `invalid/` fixture served
locally, it exits non-zero with the expected code. Must share the validator with the browser — a
second implementation is the failure this ticket exists to prevent.

> **Moved to the end of M1**, from its original position between `SPEC-002` and `CORE-002`. The
> `SPEC-*` prefix grouped it with the spec tickets; its dependencies put it after the `CORE-*` ones.
> "Exits non-zero with the expected code against each `invalid/` fixture" is a claim about all five
> validation layers, and the corpus splits 1 transport / 1 parse / 2 version / 3 schema / 23
> security. Everything past `version` is `CORE-003` and `CORE-004` work. Starting the CLI first buys
> nothing that starting it last does not, and it creates the one outcome the ticket's own scope note
> forbids: a lint that answers questions the browser's validator has not yet been taught, from code
> that is therefore not the browser's validator.
>
> The dependency is on the *validator*, not on the CLI's own concerns — argument handling, the
> exit-code contract, diagnostic rendering and the fixture-server harness are all still `SPEC-003`'s
> to build, and none of them is blocked by anything except the thing they are built to exercise.

---

## M2 — Browser foundation

**`BROWSE-001` WebView host and hardening.** JS on; `setAllowFileAccess(false)`,
`setAllowFileAccessFromFileURLs(false)`, `setAllowUniversalAccessFromFileURLs(false)`; SafeBrowsing
via androidx.webkit; mixed content blocked; **no `addJavascriptInterface`** (`ADR-005`). Acceptance:
a `file://` URL cannot be loaded from a web page; each setting has an asserting test.

**`BROWSE-002` Browser state machine and navigation.** Sealed `BrowserMode` (`ADR-008`), URL/search
bar, back/forward/reload, predictive back. Acceptance: mode transitions are total and tested;
back exits the app only from the first page.

**`BROWSE-003` Home and onboarding.** Mockup screens 1–2: recents, favourites, suggested
integrations, onboarding carousel.

**`BROWSE-004` Regular-mode chrome.** Security indicator, overflow menu, error pages, and the
"not integrated with SiteSkin" hint (mockup screen 6). Acceptance: the indicator reflects real TLS
state and cannot be influenced by page content.

**`BROWSE-005` External navigation, downloads, uploads.** Confirmation before leaving an origin;
`DownloadManager` for downloads; SAF for uploads; non-http schemes confirmed before dispatch.
Acceptance: no storage permission is requested; an `intent:` URL from a page does not auto-dispatch.

---

## M3 — SiteSkin runtime

**`NET-001` Manifest fetcher.** OkHttp implementation of `ManifestSource`. HTTPS-only, 128 KB cap,
timeouts, same-origin redirects max 2, concurrent with page load (`ADR-009`). Acceptance via
MockWebServer: cross-origin redirect refused; oversized body aborted mid-stream; page render never
awaits the fetch.

**`NET-002` Manifest cache.** Keyed `origin + schemaVersion`. ETag/Last-Modified, TTL
`min(Cache-Control, 24h)`, offline reuse. Acceptance: a cached manifest is **never** returned for a
different origin — with a negative control.

**`NET-003` Brand asset pipeline.** Same-origin only, PNG/WebP only (**no SVG**), byte and dimension
caps, off-main-thread decode, monogram fallback. Acceptance: an SVG logo is refused; an oversized
image is refused before full decode; failure yields the monogram, never a crash or blank bar.

**`SKIN-001` Dynamic theming.** M3 `ColorScheme` from validated branding, dark derivation, contrast
guard applied before first paint. Acceptance: a manifest whose text and background match is
corrected to AA before render.

**`SKIN-002` SiteSkin top bar.** Mockup screens 3–5, with the **non-suppressible** registrable
domain and TLS indicator (`ADR-006`). Acceptance: no manifest field can remove them — negative
control required; logo confined to its slot regardless of intrinsic size.

**`SKIN-003` Bottom navigation, quick actions, menu.** Up to 5 nav items, quick-action FAB, side
menu. Acceptance: over-limit manifests render exactly 5; long labels truncate without overlap at
maximum font scale.

**`SKIN-004` Mode transitions and origin change.** Activation, deactivation on origin change, skin
swap between two integrated origins. Acceptance: the concept's §46 steps 14–15 — Bloom → PixelPlay
swaps skins, Bloom → News drops to regular — as instrumentation tests against the demo origins.

---

## M4 — Hardening, privacy, demo

**`HARDEN-001` Adversarial corpus.** `javascript:`/`file:`/`content:`/`intent:`/`data:` schemes,
oversized and deeply-nested payloads, redirect loops, IDN homographs, duplicate ids, over-limit
collections, hostile colours. Acceptance: every case has a negative control proving the test detects
removal of the protection.

**`HARDEN-002` Brand-impersonation controls.** Implements `ADR-006` + `ADR-011` end to end:
non-suppressible domain, bounded logo slot, first-use consent sheet, per-site persistence.
Acceptance: a manifest impersonating another brand still shows its true registrable domain; consent
is required before any chrome change; "Never for this site" persists across restart.

**`PRIV-001` Privacy controls.** Zero telemetry by default. Global and per-site SiteSkin toggles.
Clear browsing data. Data-safety form mapping. Acceptance: a network capture during a full browse
session shows no request to any Webora-controlled host.

**`A11Y-001` Accessibility.** TalkBack labels, font scaling to 200%, 48dp targets, contrast, no
reliance on colour alone. Acceptance: the skinned chrome is fully navigable by TalkBack, and
manifest-supplied colours never reduce contrast below AA.

**`DEVX-001` SiteSkin Integration Inspector.** Debug-only panel: current origin, manifest URL, HTTP
status, cache state, schema version, validation result, warnings, applied theme, active nav item,
rejected actions (concept §31). Acceptance: absent from release builds — asserted, not assumed.

**`DEMO-001` Bloom Flowers.** Full reference site in `denrzv/bloom-flowers` plus `INTEGRATION.md`.
Acceptance: `siteskin-lint` exits 0 against the deployed origin; the app renders mockup screen 3.
**Done** — served from `https://denrzv.github.io`, lint exit 0 against it.

---

## M5 — Distribution

**`DIST-001` Debug APK on a GitHub Release.** Build `:app:assembleDebug`, attach the APK to a
GitHub Release, and write install instructions covering the "install from unknown sources" prompt
Android shows for a sideloaded build. Acceptance: a friend with the link installs it and Bloom
Flowers renders with its branded chrome.

Deliberately **not** in scope, and this is the point of choosing a debug build: no signing config,
no keystore handling, no R8 keep verification, no store assets, no Data safety form. The debug
variant also carries the `DEVX-001` SiteSkin Inspector, which is useful when demonstrating and is
absent from the release variants by construction.

---

## M6 — Design refresh

Opened after the `DIST-001` debug APK was run on an emulator. The finding that justifies a milestone
rather than a cleanup ticket is in [`design/AUDIT.md`](design/AUDIT.md) §1.6: **a site that publishes
a manifest gets a better-specified visual system than the browser rendering it.** `SiteSkinTheme`
gives websites six colour roles, a dark projection and a WCAG guard; `MainActivity.kt:38` gives
Webora a bare `MaterialTheme {}` and the default baseline purple.

Scope is **browser-owned surfaces first** — chrome, Home, onboarding, errors, settings. SiteSkin
integrated chrome follows in M7 as `UX-005` once `UX-002` provides the shared browser-owned icon
foundation.

The audit's §3 lists seven constraints every ticket here inherits. Two are worth repeating because
they are build-breaking rather than advisory: `BrowserSurfaceConventionsTest`'s `RAW_BUTTON_IMPORT`
bans **any** `androidx.compose.material3.\w*Button` import outside the file declaring
`fun WeboraButton(`, so a `WeboraIconButton` must land in `BrowserAccessibility.kt` and nowhere
else; and `NAMED_LITERAL` bans a hard-coded `contentDescription`, so every icon-only control needs
its `strings.xml` entry in the same commit that introduces it.

**`UX-001` Design direction sketches and selection.** Three distinct directions rendered as 360 dp
mockups of the four surfaces, light and dark, each annotated with its palette, geometry, icon budget
and — the part that can eliminate a direction — its mechanism for keeping the registrable domain and
TLS state browser-owned under `ADR-006`. Deliverable is a **decision**, not code: a named direction
with its amendments, and `ADR-013` recording the browser-owned token layer and why the browser's
palette is compiled rather than derived from anything remote. Acceptance: every direction states a
`C1` mechanism concrete enough to implement; the selection and its amendments are recorded in
`docs/adr/ADR-013-browser-owned-design-tokens.md`; `bash scripts/pre-commit-check.sh` passes.

> The candidate set already exists at `docs/design/directions/index.html`, written ahead of the
> ticket because it lives under `docs/` and is therefore outside the workflow gate — and because a
> milestone about the product's appearance should not be scoped in prose. It is `UX-001`'s input,
> not its conclusion.

**`UX-002` Design system foundations.** `WeboraTheme` — a browser-owned token layer for colour
(light and dark), typography, shape, spacing and elevation — replacing the bare `MaterialTheme {}`
at `MainActivity.kt:38`. Roughly eight Material Symbols bundled as **vector drawables in
`res/drawable`** rather than adding `material-icons-extended`, which is large and deprecated
upstream; `res/` currently contains no `drawable/` directory at all. `WeboraIconButton` added
beside the existing wrappers. `themes.xml` gains a `values-night` counterpart so the system bars
follow the same setting the Compose palette does. Acceptance: no browser surface reads a Material
default; `contrastRatio()` — lifted out of `SiteSkinTheme.kt:89` to somewhere both palettes reach —
asserts every browser colour pair at 4.5:1 body and 3:1 non-text **in a JVM test**; a test proves no
manifest value can reach a browser token, with a negative control; `bash scripts/pre-commit-check.sh`
passes.

> The `C2` invariant — browser tokens are not manifest-influenceable — is currently maintained only
> by there being no browser palette at all. Creating one is exactly when it stops being free, which
> is why the test lands in the same ticket as the palette rather than after it.

**`UX-003` Browser-owned chrome rebuild.** The address bar, navigation controls, overflow menu,
progress and status region, and the error page — `BrowserScreen.kt:493-534` and `:537`. The five
filled text-labelled buttons in a `FlowRow` become the selected direction's control set.
Acceptance: `BrowserSecurityIdentity` still derives from the committed `SiteOrigin` and still
publishes `BROWSER_SECURITY_TAG`, with a negative control proving the test detects its removal; the
persistent live region survives, still assertive on failure and polite on progress; every control
is ≥ 48 dp at 200% font scale without truncating a load-bearing string;
`bash scripts/pre-commit-check.sh` passes.

**`UX-004` Home, onboarding and settings.** `HomeScreen`, `OnboardingScreen` and
`PrivacySettingsScreen` against the `UX-002` tokens. The settings screen's bare
`Text` + `Switch` pair (`PrivacySettingsScreen.kt:35-43`) becomes a list row, and the per-origin
resets stop being full-width filled buttons whose label is an entire canonical origin — while
keeping the origin *in* the accessible name, which is why it was put there. Acceptance: the
`stateDescription` on the global toggle survives the restyle; the complete canonical origin remains
readable per `HARDEN-002`; `docs/accessibility/CONFORMANCE.md` is updated for every surface that
changed; `bash scripts/pre-commit-check.sh` passes.

---

## M7 — Visual quality, evidence & integration polish

Opened from the first successful live Pixel 6 / API 33 screenshot journey. Functionally, the journey
passed: emulator booted, Bloom Flowers activated, consent completed, and the instrumentation test
finished green. Visually, the evidence exposed problems that semantic assertions did not: Android
System UI ANR overlays covered all canonical frames, review required drilling into an artifact ZIP,
the debug inspector obscured the integrated composition, navigation used placeholder glyphs, and
the consent action layout looked accidental at phone width.

M7 treats **reviewability and visual intent as product quality**, while preserving every existing
trust-boundary rule.

### `CI-002` — Deterministic clean Android screenshot capture

**Priority:** P0  
**Depends on:** `CI-001`  
**Goal:** make a green screenshot run produce clean product evidence rather than screenshots that
happen to contain the right Compose nodes underneath an OS dialog.

**Scope**
- Determine why the hosted API 33 emulator presents `System UI isn't responding` after boot and
  distinguish startup/resource noise from an actual Webora ANR.
- Add an explicit post-boot visual-readiness/settling strategy; `sys.boot_completed=1` alone is not a
  sufficient screenshot-ready signal.
- If the known system-owned ANR dialog can still occur, detect and dismiss **only that known System
  UI dialog** before canonical captures, preferring `Wait` over killing System UI where possible.
- Preserve logcat and enough diagnostics to prove what was dismissed.
- Do not add generic popup dismissal that could hide a Webora crash, ANR, permission prompt, TLS
  warning, or consent regression.

**Acceptance**
- A fresh cold-hosted workflow run returns canonical Home, consent, and integrated screenshots with
  no `System UI isn't responding` overlay.
- Repeated clean runs do not depend on a manually chosen fixed delay alone; readiness has an
  observable condition or a bounded settle policy with diagnostics.
- A deliberately induced Webora failure still makes the workflow red rather than being dismissed.
- `bash scripts/pre-commit-check.sh` passes.

### `DEVX-002` — Screenshot review experience

**Priority:** P1  
**Depends on:** `CI-001`; may proceed in parallel with `CI-002`  
**Goal:** reduce screenshot review from archive archaeology to one obvious visual artifact.

**Scope**
- Upload a human-facing screenshots artifact containing only canonical visual evidence.
- Place `01-home.png`, `02-siteskin-consent.png`, and `03-siteskin-integrated.png` at the artifact
  root rather than behind implementation-specific output directories.
- Generate a labelled `preview.png` contact sheet showing the complete journey in order.
- Upload logcat, instrumentation output, raw connected-test additional output, and result metadata as
  a separate diagnostics artifact.
- Summarize screenshot count, commit/run identity, and the two artifact names in the job summary.
- Do not introduce external image hosting or another SaaS only to make previews clickable.

**Acceptance**
- A reviewer downloads one screenshots artifact, opens one `preview.png`, and can judge the complete
  journey immediately.
- Individual full-resolution PNGs are adjacent to the preview at the artifact root.
- Failure diagnostics remain available without cluttering the human-facing bundle.
- `bash scripts/pre-commit-check.sh` passes.

### `DEVX-003` — Inspector isolation and canonical evidence mode

**Priority:** P1  
**Depends on:** `DEVX-001`  
**Goal:** keep the SiteSkin Inspector useful without making a developer overlay part of Webora's
canonical product presentation.

**Scope**
- Remove the persistent `SiteSkin inspector` floating action from canonical screenshots and normal
  demo composition.
- Keep the inspector debug-only and reachable through an explicit developer affordance: an overflow
  item, dedicated debug action, or deterministic visual-evidence mode is acceptable; silently
  deleting the inspector is not.
- Preserve the existing compile-time assertion that inspector implementation is absent from release
  variants.
- Ensure the mechanism cannot be controlled by a SiteSkin manifest.
- **Carried in from `CI-003`, and disproved by doing it.** `CI-003` claimed removing the overlay
  would also close a hole in the rendered-content check: the inspector floating action supposedly sat
  *inside* the measured page region unexcluded, at roughly 0.84% of it, so two chrome buttons
  together could clear the 1% liveness threshold over a blank page. Removal showed otherwise — runs
  11 and 12 report `differing=0.7530481592174976` bit-identically, with the affordance and without
  it. The overlay sat below `y=2127`, where the region ends and the SiteSkin bottom navigation
  begins. One chrome button is in that region, the quick action, and it was already excluded.
  The presentation argument was the whole case, and it was sufficient.

**Acceptance**
- The integrated canonical screenshot contains no inspector overlay.
- With the overlay gone, a blank page in the integrated frame fails `CI-003`'s rendered check rather
  than passing on chrome. Verifiable by inspection of what remains inside `BROWSER_CONTENT_TAG`.
- A developer in a debug build can still reach the inspector in no more than two deliberate
  interactions.
- Release variants remain inspector-free and the existing negative gate still detects leakage.
- `bash scripts/pre-commit-check.sh` passes.

### `UX-008` — Browser navigation controls in integrated mode

**Priority:** P2  
**Depends on:** `UX-003`  
**Goal:** give the user a visible way back while a SiteSkin skin is active.

**Found by:** `CI-003`'s hosted run 11, once the integrated frame finally rendered and could be read.
The screenshot is the evidence: `03-siteskin-integrated.png` at `7629c620`.

**The observation.** In integrated mode `SiteSkinTopBar` *replaces* the `AddressBar` outright
(`BrowserScreen.kt`, `RegularBrowser`), and `SKIN-002` specifies that bar as logo, title, subtitle,
registrable domain and TLS state. Nothing in that list is a navigation control, so an active skin
leaves **no visible back, forward or reload** — only system back, which `BROWSE-002` handles but
which is invisible and unavailable to anyone browsing with gestures disabled or on a device where
the gesture is unreliable.

This is a gap rather than a decision: no ADR or ticket ever says integrated mode should drop the
browser's own navigation, and `SKIN-002` did not say where it goes because the top bar was designed
as a standalone component before it reached the runtime screen in `SKIN-004`.

**Scope**
- Decide where browser navigation lives while a skin is active, and put it there.
- Keep it browser-owned: a manifest must not be able to hide, relabel, reorder or restyle it, for
  the same reason `ADR-006` protects the domain and TLS indicator.
- Do not spend the site's chrome budget on it — the point of integrated mode is that the site's
  navigation is the primary one.

**Acceptance**
- A back affordance is visible and reachable in integrated mode without relying on system back.
- No manifest field can suppress or restyle it; a negative control proves it.
- `SKIN-002`'s security identity row keeps its position and its browser-authored semantics node.
- `bash scripts/pre-commit-check.sh` passes.

### `UX-005` — SiteSkin integrated chrome & semantic icon set *(revived)*

**Priority:** P0  
**Depends on:** `UX-002`  
**Goal:** replace prototype glyphs with an intentional icon system while keeping trusted chrome
browser-owned.

This ticket was previously descoped because browser-owned surfaces had no design system at all. Live
Pixel 6 evidence now shows the cost of leaving it parked: the headline Bloom Flowers screen still
renders `⌂`, `▦`, `▣`, `●`, `☎`, while the quick-action affordance reads as a generic `+` rather
than a meaningful action. `UX-002` remains the prerequisite; once its vector/icon foundation lands,
this ticket is the next visible quality step.

**Scope**
- Replace Unicode placeholder rendering in `SiteSkinIcon` with bundled browser-owned vector icons.
- Keep the manifest contract semantic: a site supplies an icon **name**, never an arbitrary URL,
  drawable, font glyph, or remote asset for trusted browser chrome.
- Define/document a small supported semantic vocabulary covering at least Home, catalog/storefront,
  a flower or equivalent domain-specific catalog cue, cart, account/person, call, search/menu, and
  deterministic fallback.
- If new semantic names are added, update the validator allow-list, spec/fixtures/documentation as
  required without unnecessarily bumping the schema version: the schema intentionally allows
  forward-compatible icon-name strings and the trusted allow-list remains authoritative.
- Render bottom navigation with real icon + label states, including a clear selected state.
- Give quick actions meaningful icons rather than a generic `+` when their semantic action is known.
- Preserve TalkBack labels, selected semantics, >=48 dp touch targets, contrast, and 200% font-scale
  behaviour.

**Acceptance**
- No canonical SiteSkin navigation or supported quick action uses the old placeholder glyph set.
- Bloom Flowers demonstrates at least Home, catalog/flowers, Cart, Account, and Call with meaningful
  browser-owned icons.
- An unknown manifest icon name still produces a deterministic safe fallback plus the existing
  diagnostic; it cannot load site-supplied trusted-chrome artwork.
- A negative control proves a manifest cannot address an arbitrary drawable/resource.
- Pixel 6 visual evidence shows a deliberate selected nav state and no icon/label collision.
- `bash scripts/pre-commit-check.sh` passes.

### `UX-007` — Adaptive SiteSkin consent action hierarchy

**Priority:** P1  
**Depends on:** `UX-006`  
**Goal:** make the trust decision visually legible and balanced on real phone widths without
weakening the protected consent copy.

**Scope**
- Establish a clear hierarchy: `Allow` is primary, `Not now` is secondary, and `Never for this site`
  is the persistent-denial action.
- Use an adaptive action layout that can stack or reflow deliberately on narrow widths rather than
  leaving one action isolated above an accidental-looking row.
- Keep the exact protected origin, explanation of what the site may customise, and browser/security
  ownership message outside SiteSkin-controlled styling.
- Exercise long/localised action labels and 200% font scale.
- Preserve current persistence semantics for Allow / Not now / Never.

**Acceptance**
- Pixel 6 / API 33 consent evidence has an intentional visual hierarchy with no clipping or awkward
  split caused by width.
- At 200% font scale all three actions remain readable, reachable, and >=48 dp where applicable.
- Tests still prove the protected origin shown to the user is the committed origin and cannot be
  rewritten by manifest data.
- Consent behaviour/persistence is unchanged apart from presentation.
- `bash scripts/pre-commit-check.sh` passes.

### `DEMO-003` — Bloom Flowers visual fidelity & protocol showcase

**Priority:** P1  
**Depends on:** `DEMO-001`, `UX-005`, `UX-007`, `DEVX-003`; clean evidence supplied by `CI-002` and
`DEVX-002`  
**Goal:** make the one reference integration demonstrate the quality of the protocol, not merely its
functional existence.

**Scope**
- Update the Bloom Flowers live manifest/reference fixture to exercise the final semantic icon
  vocabulary selected by `UX-005`.
- Use meaningful navigation semantics for Home, Flowers/Catalog, Cart, and Account, plus a Call-shop
  quick action with its matching browser-owned icon.
- Keep the site implementation readable as reference material; do not add framework complexity or
  arbitrary browser-specific visual assets merely to make screenshots attractive.
- Verify labels, routes, active matching, quick action, consent preview, and live origin continue to
  describe the actual deployed site.
- Treat the clean Home → consent → integrated Pixel 6 screenshot journey as visual acceptance
  evidence.

**Acceptance**
- `siteskin-lint https://denrzv.github.io` remains green.
- The integrated screenshot contains meaningful vector icons, a clear selected nav item, a coherent
  quick action, no inspector overlay, and no OS contamination.
- The consent screenshot satisfies `UX-007` and the integrated screenshot satisfies `UX-005`.
- `INTEGRATION.md` documents the semantic icon choices so a site owner can reproduce the pattern.
- `DEMO-002` remains descoped; this ticket improves the existing reference origin rather than adding
  unrelated demos.
- `bash scripts/pre-commit-check.sh` passes.

---

## Descoped

Parked with their reasoning, not deleted — `SCOPE-001` records the decisions. Each entry keeps its
original definition so reviving it does not start from nothing.

**`DEMO-002` PixelPlay, Daily Journal, Example News.** Three more origins, the last deliberately
without a manifest as the negative control. Acceptance was: all four served over HTTPS on **distinct
origins**, enabling `SKIN-004`'s transition tests.
- *Why not now:* one demo is enough to show the protocol working end to end.
- *What lapses:* only the live demonstration of skin **swap** (Bloom → PixelPlay) and skin **drop**
  (Bloom → News), concept §46 steps 14–15. The behaviour itself is implemented and unit-tested in
  `SKIN-004`; no capability is lost.
- *What would revive it:* a domain with per-demo subdomains. A single GitHub Pages user site cannot
  supply distinct origins, so this cannot be revived on the current hosting.

**`PLAY-001` Compliance sweep.** targetSdk 36; AAB; permissions minimized; edge-to-edge; predictive
back; 16 KB page sizes; Policy 4.3 minimum-functionality evidence prepared (Webora is a
general-purpose browser, and the reviewer note should say why in one paragraph). Acceptance was: a
completed checklist with evidence per line.
- *Why not now:* distribution is an APK handed to friends; there is no listing to comply with.
- **`targetSdk 36` is not descoped.** It shipped in `FOUND-002`'s first commit precisely so it would
  not become a cleanup ticket, and it is in the build today.
- *Worth keeping:* the Policy 4.3 analysis. Reviewers pattern-match "WebView app" and reject; the
  answer is that Webora is a general-purpose browser with arbitrary URL entry, history, downloads
  and a default-browser role, and the SiteSkin engine is substantial native functionality.

**`PLAY-002` Release signing and R8.** Implement the signing config — env → gradle property →
`local.properties`. Verify R8 keeps against a real release build; confirm kotlinx.serialization
serializers survive minification. Acceptance was: a signed AAB installs and runs, and SiteSkin mode
still activates in the minified build. Missing credentials must fail the release build loudly, not
silently fall back to the debug key.
- *Why not now:* `DIST-001` ships a debug APK, which needs none of it.
- *Live risk if revived:* kotlinx.serialization serializers are the thing minification breaks
  quietly — the manifest would stop parsing in the release build only.

**`PLAY-003` Store listing.** Listing copy, screenshots, feature graphic, launcher icons, Data
safety form, privacy policy hosted on the product domain, internal testing track. Acceptance was:
Play Console pre-launch report clean; the privacy policy URL resolves from both the listing and
inside the app.
- *Why not now:* no store listing, and no product domain to host a privacy policy on — `webora.app`
  is taken.
- *Already done and worth keeping:* `docs/privacy/DATA_SAFETY.md` from `PRIV-001` is the
  implementation-backed mapping a Data safety form would be filled from.

**Impersonation risk is not descoped with any of this.** `ADR-006`'s non-suppressible domain and TLS
indicator, and `HARDEN-002`'s controls, were justified partly by Play's Deceptive Behavior policy —
but they are load-bearing security regardless of how the app is distributed, and an APK handed to
friends does not relax them.
