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

## M8 — Native browser UX & mode continuity

Opened after reviewing the clean M7 Home and SiteSkin frames side by side. The regular browser is not
missing all navigation: `RegularBrowser` already renders `BrowserNavigationDock` with Back, Forward,
Reload, Home and More on non-integrated pages. The gap is product continuity and proof. `HomeScreen`
has no persistent browser shell, its Recent sites and Favourites sections are still placeholders,
there is no tab/session model, and the canonical screenshot journey never shows an ordinary website.

**System navigation is explicitly outside this milestone's ownership.** Android may expose a gesture
handle or the classic Back/Home/Recents buttons depending on device settings. Webora must respect
those insets and back semantics, but must not draw a fake copy of Android navigation. M8 owns the
browser layer above it: browser navigation, tabs, local browsing data and deterministic chrome
handoff when SiteSkin activates or deactivates.

### `BROWSE-006` — Multi-tab browsing and session model

**Priority:** P0  
**Depends on:** `BROWSE-002`; starts after `DEMO-003` closes M7  
**Goal:** give Webora the independent browsing contexts users expect from a general-purpose browser,
without allowing SiteSkin state or security identity to leak between tabs.

**Scope**
- Introduce a browser-owned tab/session model with one independent browsing state per tab: URL,
  WebView history/navigation capability, load state and `BrowserMode` belong to that tab.
- Support create, close, switch and active-tab selection through a browser-owned tab switcher.
- A new tab starts at Webora Home. Closing the active tab selects a deterministic neighbour; closing
  the last tab returns to one fresh Home tab rather than exiting into an undefined state.
- Keep tab count bounded and make the limit explicit in the UI rather than silently refusing or
  evicting a tab; the PRD may choose the exact cap after measuring the WebView memory trade-off.
- Preserve enough session metadata across Activity/process recreation that returning to Webora does
  not collapse multiple tabs into one. Do not persist page pixels or SiteSkin-controlled chrome.
- SiteSkin activation, consent state and security identity are resolved independently for the active
  tab; switching tabs never copies an integrated mode into another origin.

**Acceptance**
- Two tabs can hold different origins and independent back/forward histories, and switching between
  them restores the correct URL and navigation capability.
- One tab may be SiteSkin-integrated while another remains regular; switching between them never
  leaks SiteSkin chrome, domain/TLS identity or active navigation state.
- Create/close/switch behaviour is deterministic and covered by state tests, including closing the
  active and final tabs.
- The selected tab/session set survives Activity recreation without changing origins or modes.
- `bash scripts/pre-commit-check.sh` passes.

### `UX-011` — Persistent browser-owned navigation shell ✅

**Priority:** P0  
**Depends on:** `BROWSE-006`, `UX-003`, `UX-008`  
**Goal:** make Home and ordinary browsing feel like one native browser experience rather than two
screens where browser controls appear only after a page happens to be open.

**Scope**
- Reuse and evolve the existing `BrowserNavigationDock`; do **not** create a second navigation
  implementation for Home.
- Keep a browser-owned shell visible on Home/new-tab and regular browsing surfaces. Back/Forward may
  be disabled when there is no page history; Reload is meaningful only for a loaded page.
- Add the real tab-switcher entry point from `BROWSE-006` and retain Home and overflow access.
- Keep the editable address/search surface and browser-authored security identity coherent with the
  bottom shell rather than presenting two competing navigation systems.
- Treat Android system navigation as an external platform layer: consume gesture/three-button
  navigation insets correctly, integrate with predictive/system back, and never render fake
  Back/Home/Recents controls.
- Preserve >=48 dp touch targets, accessible names, state descriptions where useful, and usable
  layout at 200% font scale.
- A manifest cannot hide, relabel, reorder or restyle this shell in Home or regular mode.

**Acceptance**
- Home/new-tab and a normal non-integrated HTTPS page both expose discoverable browser-owned
  navigation without requiring the user to rely on Android gestures or three-button navigation.
- Gesture-navigation and three-button-navigation configurations do not overlap, clip or visually
  duplicate the browser shell.
- The shell exposes a working tab switcher, Home and overflow; Back/Forward/Reload state reflects the
  active tab rather than a global stale value.
- A negative control proves manifest input cannot suppress or style regular browser navigation.
- Pixel-sized and 200%-font tests retain minimum touch targets and accessible names.
- `bash scripts/pre-commit-check.sh` passes.

### `BROWSE-007` — Recents, history and favourites ✅

**Priority:** P1  
**Depends on:** `BROWSE-006`  
**Goal:** replace Home's current Recent sites / Favourites placeholders with useful, local browser
data while preserving Webora's zero-telemetry privacy stance.

**Scope**
- Record local main-frame browsing history with canonical URL/origin, display title when available and
  visit time; deduplicate the Home recents presentation without erasing the underlying history.
- Add explicit add/remove-favourite behaviour and persistent favourites keyed by canonical URL rather
  than page-controlled display labels.
- Populate the existing Home Recent sites and Favourites sections from these stores, with useful
  empty states only when they are actually empty.
- Integrate the new stores with `Clear browsing data`: history/recents must be clearable, and the UX
  must make the treatment of favourites explicit rather than deleting them accidentally.
- Keep all records local by default. No sync account, analytics endpoint, recommendation service or
  Webora-controlled network request is introduced by this ticket.
- Do not let an untrusted page inject arbitrary actions into browser history/favourite UI; navigation
  still resolves through the browser's existing URL/origin rules.

**Acceptance**
- Visiting ordinary and integrated sites produces real Recent sites entries on Home with deterministic
  ordering and duplicate handling.
- A favourite persists across restart, can be removed explicitly, and opens the exact stored URL.
- Clear-browsing-data tests prove history/recents follow the documented clear semantics and
  favourites follow the separately documented choice.
- A network capture of browsing/history/favourite operations contains no new Webora-controlled host.
- `bash scripts/pre-commit-check.sh` passes.

### `UX-012` — Mode-aware chrome handoff

**Priority:** P0  
**Depends on:** `UX-011`, `UX-005`, `UX-008`, `SKIN-004`  
**Goal:** make the transition between ordinary browser chrome and SiteSkin chrome feel like a
predictable enhancement of the browser, not a mode switch that hides navigation unexpectedly.

**Scope**
- Define and test the user-visible chrome state for Home → regular site → integrated site → regular
  site → Home, including consent accepted, dismissed and permanently denied paths.
- In Home and regular mode, show the browser-owned navigation shell from `UX-011`.
- When an integration activates, SiteSkin bottom navigation may take over the bottom product slot,
  but browser-owned security identity and an escape/back affordance remain visible and manifest-
  independent as established by `ADR-006` and `UX-008`.
- Leaving the integrated origin, declining/denying consent, or switching to a regular tab restores
  ordinary browser chrome deterministically; no SiteSkin navigation/action may survive the mode that
  authorised it.
- Switching tabs restores each tab's own mode and chrome without transiently showing the previous
  tab's site navigation or security identity.
- Preserve Android system-back/predictive-back semantics independently of the visible browser shell.

**Acceptance**
- A state/transition test covers Home → regular → integrated → regular and proves each state exposes
  exactly the intended browser/site chrome layer.
- Declining SiteSkin consent leaves a fully usable regular browser shell; consent is never required
  to recover browser navigation.
- Changing to a non-integrated origin removes SiteSkin bottom navigation and quick actions before the
  regular browser frame is accepted.
- Switching between integrated and regular tabs never flashes or retains the wrong origin, security
  identity or navigation controls.
- A negative control proves manifest input cannot suppress the browser-owned escape/security layer.
- `bash scripts/pre-commit-check.sh` passes.

### `CI-007` — Canonical regular-browsing evidence

**Priority:** P1  
**Depends on:** `UX-011`, `UX-012`; reuses the clean-device guarantees from `CI-006`  
**Goal:** make ordinary web browsing a first-class visual acceptance path instead of inferring its UX
from code while canonical evidence covers only Home, consent and SiteSkin mode.

**Scope**
- Extend the hosted screenshot journey with one canonical frame of a stable, non-integrated HTTPS
  page. Select a deterministic origin/fixture during ticket planning; do not make the assertion
  depend on volatile third-party page decoration.
- The accepted regular frame must show browser-owned address/security identity and the regular
  navigation shell, with no SiteSkin bottom navigation or quick action.
- Prefer a journey that exits Bloom Flowers to the non-integrated origin so the same evidence also
  exercises `UX-012`'s integrated → regular handoff.
- Keep `CI-005` frame-ownership/rendered validation and `CI-006` device-readiness policy unchanged;
  regular evidence must be uncontested by the same standards as existing canonical frames.
- Add the ordinary frame to the human-facing contact sheet and keep diagnostics separate.

**Acceptance**
- A hosted cold run produces the existing M7 canonical frames plus one clean regular-browsing frame.
- The regular frame visibly contains Webora's browser navigation and security identity and visibly
  does **not** contain SiteSkin navigation/actions.
- Evidence from the transition proves the integrated chrome was removed rather than merely hidden
  under a new page.
- The contact sheet and job summary report the new canonical count without dropping any existing
  frame or diagnostics.
- `bash scripts/pre-commit-check.sh` passes.

### `CI-008` — Pin the hosted Back-to-Home evidence contract

**Priority:** P0
**Depends on:** `BROWSE-008`, `CI-007`
**Goal:** make the canonical journey diagnose the browser-owned Back prerequisite at the action
seam instead of reporting a later address-field timeout when the prerequisite regresses.

**Scope**
- Require the integrated Back affordance to be displayed and enabled immediately before the
  existing visible click.
- Keep the user-visible Home/address transition and every screenshot acceptance policy unchanged.
- Treat two cold hosted four-frame runs as downstream `CI-007` rollout evidence, not as evidence a
  managed-cloud checkout can manufacture locally.

**Acceptance**
- Android instrumentation compiles with the enabled-state assertion before the existing click.
- A removal negative control proves the prerequisite check is not decorative.
- Review and QA confirm that the assertion can only make the journey refuse earlier and cannot
  permit a previously rejected screenshot.
- `bash scripts/pre-commit-check.sh` passes.

### `DEMO-004` — Browser-first reference walkthrough

**Priority:** P2  
**Depends on:** `BROWSE-006`, `BROWSE-007`, `UX-012`, `CI-007`  
**Goal:** make the finished prototype explain itself as a browser first and SiteSkin as an optional,
consented enhancement rather than making the integrated demo look like the entire product.

**Scope**
- Document one concise reference journey: Home/new tab → ordinary HTTPS browsing → tab switch or
  return to Home → Bloom Flowers consent → integrated mode → back/switch to ordinary browsing.
- Use the real M8 browser shell, tabs, recents/favourites and canonical evidence; do not add a fake
  demo-only navigation path or a second browser implementation.
- Explain the distinction between Android system navigation, Webora browser navigation and SiteSkin
  site navigation so screenshots are not misread as Webora attempting to replace OS controls.
- Keep `DEMO-002` descoped: this walkthrough may use a stable ordinary HTTPS site but does not require
  maintaining another custom SiteSkin origin.
- Update install/demo documentation and the screenshot narrative/contact sheet references as needed.

**Acceptance**
- A first-time reviewer can follow the documented flow and see both ordinary and integrated browsing
  without needing hidden gestures or developer-only controls.
- The walkthrough demonstrates at least two tabs or equivalent session switching, a real Recent or
  Favourite entry, the regular browser shell, SiteSkin consent/integration, and deterministic return
  to regular chrome.
- Hosted visual evidence contains the ordinary and integrated states described by the walkthrough and
  remains free of OS/debug contamination.
- Documentation states clearly that Android system navigation is OS-owned and that SiteSkin may
  replace only the site's product-navigation slot, never browser security/escape controls.
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

---

### `UX-009` — Consent dialog renders as a circle and clips its own heading

**Status:** CLOSED — fixed and visually confirmed. Hosted runs **28** (`31804437572`) and **29**
(`31807060517`) supplied the previously missing confirmation: both produced a clean canonical
`02-siteskin-consent.png` after the CI-006 readiness gate settled, with no OS contamination and no
return of the stadium/clipping defect. Run **19** remains the failed first confirmation attempt. See
`reports/qa/UX-009.md`.

**The mechanism, and none of its four steps looks like a bug on its own.** `WEBORA_SHAPES` put
`WeboraRadius.PILL` (999 dp) on Material's `extraLarge` role; `DialogTokens.ContainerShape` is
`CornerExtraLarge`, so every `AlertDialog` in the app read it; `CornerBasedShape.createOutline`
scales an over-large corner to fit rather than rejecting it, resolving 999 dp to exactly 140 on a
280 dp dialog — half the width, which rounds the sides away entirely; and `Surface` clips its
content to the result, so the heading was removed rather than overlapped.

**The fix is at the role, not at the call site.** `EXTRA_LARGE = 28.dp` is the container radius and
the pill stays for the controls that name it directly — the address field on Home now does, as
`BrowserChrome` already did. That fixed all five dialogs at once (consent, external-URL,
external-navigation, clear-browsing-data, inspector); a `shape =` argument on the consent dialog
would have fixed the photographed frame and left the collision live.

**The gate is `WeboraThemeTest.a container role never rounds a dialog into a stadium`**, which lays
every settable `Shapes` role out through the real `createOutline` and bounds the *resolved* radius.
`every shape corner comes from the compiled radii` stayed green throughout the defect and still does
under the negative control — it asks where a value came from, and 999 dp is a declared token. So does
the whole instrumented suite: clipping happens in the parent's draw while semantics keep the full
text, which is `CI-003`'s lesson one layer up.

**Priority:** P1  
**Depends on:** `UX-002`, `UX-007`  
**Goal:** the first-use consent sheet is a dialog, not an ellipse.

**Found by:** hosted screenshot run **14** (`31706775744`, `d830cac8`), frame
`02-siteskin-consent.png`. **Reproduced on run 15** (`31714792338`, `f1e81640`) over a page that had
fully rendered, so it is not a paint-timing artefact — unlike `UX-010`, which was.

**The observation.** The consent surface draws as a large circle/stadium rather than a rounded
rectangle. Its content overflows the shape: the browser-authored heading
`Allow https://denrzv.github.io to customise Webora?` is clipped on the left to `ow`, so the first
word of Webora's own identity statement is missing from the frame.

`UX-007`'s action hierarchy underneath is **correct** — filled *Allow*, outlined *Not now*, text
*Never for this site*, full width, vertically stacked. This is a shape token applied to the dialog
container, not a layout regression.

**Why it is P1 rather than cosmetic.** `HARDEN-002` requires the complete canonical origin to be
displayed so the visible grant matches the `SiteOrigin` persistence key, and `ADR-011` makes this
dialog the enforcement point for the whole trust boundary. A clipped heading is a security-relevant
surface losing text, and this is the screen a user reads before granting a site control of the
chrome.

**Scope**
- Find the radius applied to the dialog container and give it a value from `WeboraRadius` that
  produces a dialog.
- The heading must render in full at 360 dp, and a test should fail if it cannot.
- Check the same token is not producing the effect elsewhere; a circle this large suggests a value
  intended for a different component.

**Acceptance**
- The consent dialog is a rounded rectangle and no browser-authored text is clipped.
- The complete canonical origin remains readable per `HARDEN-002`.
- `UX-007`'s action order, emphasis and full-width stack are unchanged.
- `bash scripts/pre-commit-check.sh` passes.

---

### `UX-010` — Integrated mode shows no SiteSkin top bar in hosted evidence

**Status: CLOSED, not a defect.** Hosted run **15** (`31714792338`, `f1e81640`) renders the complete
bar — back control, monogram, `Bloom Flowers`, `Fresh flowers delivered today`, and
`Secure · denrzv.github.io`. The run-14 frame was captured before the surface had painted, over a
page that had not drawn either, under a dialog dimming everything. The ticket's own "what is not yet
known" section allowed for exactly this and asked for a frame captured after paint; that frame now
exists and the bar is in it. `ADR-006`'s non-suppressible domain and TLS state are visible, and
`UX-008`'s browser-owned Back control is beside them. Kept rather than deleted, because "we looked
and it was fine" is worth as much as a fix to the next reader.

**Priority:** P1  
**Depends on:** `SKIN-002`, `UX-005`  
**Goal:** find out why the branded bar is absent from the integrated frame, and fix it.

**Found by:** hosted screenshot run **14** (`31706775744`, `d830cac8`), frame
`03-siteskin-integrated.png`.

**The observation.** SiteSkin is clearly active — the bottom navigation renders `Home / Catalog /
Cart / Profile` with `UX-005`'s vector icons, and the quick action is composed. But the region above
the page is empty: no logo, no title, no subtitle, and **no registrable domain or TLS state**.
Run 12's frame at `140d206e` showed all of it.

**Why it is P1.** `ADR-006` and `HARDEN-002` make the domain and TLS indicator non-suppressible in
SiteSkin mode — that is the sharpest security property in the design. A frame where site navigation
renders and browser identity does not is exactly the shape those rules exist to forbid, whatever the
cause turns out to be.

**What is not yet known.** The frame was captured while the page had not painted (see `CI-005`), and
the whole screen is dimmed by a system dialog's `DIM_BEHIND`. So this may be a paint-timing artefact
rather than a composition defect. `CI-003`'s `rendered-*.txt` cannot answer it: the measured region
starts at `y=349`, and the top bar sits above that, outside `BROWSER_CONTENT_TAG`.

**Scope**
- Reproduce with a frame captured after the page has painted — which needs `CI-005` first, or a run
  that does not meet an ANR.
- If the bar is genuinely not composed, find which of activation, brand asset, or theme projection
  fails and fix it.
- If it is paint timing, the finding belongs to the harness and this ticket closes as not-a-defect
  with the evidence recorded.

**Acceptance**
- A hosted integrated frame shows the branded bar including registrable domain and TLS state.
- The outcome is recorded either way; "could not reproduce" closes it only with a clean frame
  attached.
- `bash scripts/pre-commit-check.sh` passes.


---

### `NET-004` — The reference integration's logo never reaches the top bar

**Status:** CLOSED — fixed. **Priority:** P1
**Depends on:** `NET-003`, `SKIN-002`
**Goal:** render the manifest's declared logo, or find out why the pipeline refuses it.

**Found by:** hosted screenshot run **15** (`31714792338`, `f1e81640`), frame
`03-siteskin-integrated.png` — the top bar showed the browser-generated `B` monogram, as run **11**
had too.

**Answered by:** hosted run **16** (`31725858080`, `f982f1a4`), from
`diagnostics/brand-asset-03-siteskin-integrated.txt`:

```
stage=TRANSPORT_UNAVAILABLE   rejection=null   httpStatus=null   elapsedMillis=891
```

No answer arrived — a decision in 891 ms, not a race, and none of the four candidates the entry had
listed. The logcat named the cause to the second: the emulator's Wi-Fi left `CONNECTED` at
`17:41:33.893`, in the same second Allow was clicked, while the manifest fetch ten seconds earlier
had gone out over a working network.

**The refusal was correct. Its permanence was the defect.** The load is keyed on the trusted
configuration instance, which `BrowserState.forObservedOrigin` keeps across every same-origin page
start, so the network came back 6.4 s later and nothing ever asked again.

**Fixed** by retrying `TRANSPORT_UNAVAILABLE` and only that — three attempts, 1 s then 2 s apart.
A rejection is not retried (the server answered), a decode failure is not (the same bytes decode the
same way), an undeclared logo is not (nothing to request). Runs **17** and **18** (`ea57ef5d`) then recorded
`stage=DECODED httpStatus=200 pixels=512x512 attempts=1`, and run **18**'s
`03-siteskin-integrated.png` shows the flower in the 40 dp slot beside
`Secure · denrzv.github.io`.

**Also shipped, and the reason the question was answerable at all:** `BrandAssetRejection` and
`BrandAssetStage`, a brand-asset section in the debug inspector, and the outcome written into the
screenshot job's diagnostics artifact. Before this, `Brand asset: MONOGRAM` was the whole story a site
owner could get.

`NET-003`'s caps, allow-list, same-origin recheck and monogram fallback are unchanged, proven by its
tests passing unedited.

---

### `CI-006` — The emulator ANRs its way out of frame 03

**Priority:** P1  
**Depends on:** `CI-005`  
**Goal:** get the hosted journey a device quiet enough to photograph, or bound the failure so a green
run still means something.

**Found by:** `NET-004`'s runs **16** (`31725858080`) and **17** (`31727597681`), both ending
`RENDERED BUT CONTESTED` on `03-siteskin-integrated` with every capture attempt rejected as
`Application Not Responding: com.android.systemui owned the screen when the frame was taken`. Run
**13** hit the same thing, which is what opened `CI-004`.

**`CI-005`'s guard is working — that is the point.** The page had rendered and Webora did not own the
screen, so the frame was refused rather than saved with a dialog over it. Nothing here argues for
weakening it; a run that cannot photograph a clean screen must fail.

**The device is starving, not just System UI.** Run 17's logcat shows five processes ANR in sixteen
seconds — `com.android.systemui` at `17:58:17.548`, `com.android.phone` at `17:58:18.289`,
`LatinIME` at `17:58:20.477`, `com.google.android.as` at `17:58:22.161`,
`googlequicksearchbox:interactor` at `17:58:33.729` — each preceded by
`Timeout executing service`. `CI-004`'s observation that System UI's own
`SystemUIAuxiliaryDumpService` times out first still holds (`17:57:54.483`), but it is one symptom of
a device-wide stall rather than the cause.

**What makes it worth a ticket rather than a re-run:** it is now four runs out of nine, it costs
twelve minutes each time, and the journey is the only evidence path for tickets whose acceptance is a
frame. `NET-004` needed three runs to land one clean frame; runs 16 and 17 were both refused, and run 18
passed on the first capture attempt at 478 ms — so the difference is the device, not the harness.

**Scope**
- Establish what the emulator is doing between boot-ready and frame 03 — GMS package churn and dexopt
  are both visible in the window, and `scripts/android-emulator-ready.sh` declares ready well before
  either settles.
- Consider whether readiness should require the device to be *quiet*, not merely focused: no pending
  `Timeout executing service`, dexopt finished, package churn over.
- Do not lengthen the capture deadline as the fix. Twenty seconds of a contested screen is already
  twenty pieces of evidence that the device is not photographable.
- Do not dismiss the dialog from the capture loop. `CI-005` decided that, and this changes nothing
  about it.

**Acceptance**
- Two consecutive hosted runs produce three canonical frames with no contested capture.
- The readiness artifact records whatever new condition is added, sample by sample, as it already
  does for the existing four.
- `bash scripts/pre-commit-check.sh` passes.

---

### `BROWSE-010` — Leaving Home again shows the previous page under a permanent spinner

**Priority:** P2
**Depends on:** `BROWSE-009`
**Goal:** a tab that returns to Home and then navigates again must render the page it navigated to.
**GitHub:** [#106](https://github.com/denrzv/webora/issues/106)

**Found by:** `BROWSE-009`'s `/review`, by reasoning about renderer reuse rather than by a run. It is
**pre-existing** on `main` and not what issue #103 reported; `BROWSE-009` neither causes nor cures it.

**The mechanism.** `BrowserScreen` composes `RegularBrowser` only when the tab is not on Home, so
returning a tab to Home disposes its `AndroidView` while `BrowserWebViewController` deliberately
retains the `WebView` — `BROWSE-006` requires live back/forward history to survive. Navigating that
tab out of Home remounts the host, and `HardenedWebView`'s factory reads:

```kotlin
if (existing == null) loadUrl(initialUrl)
```

`existing` is not null, so the new URL is **never loaded**. The renderer keeps showing the previous
page while `BrowserState.displayedUrl` says the new one and `isLoading` stays `true` with no callback
ever arriving. Reproduction: load a page → Home → type any address.

**Why `BROWSE-009` did not fix it.** The obvious fix — load when the retained renderer's URL differs
from the tab's committed target — changes *when a reload happens*, and "switching back reattaches the
same instance without a reload" is `BROWSE-009`'s own acceptance criterion 2. Getting that wrong
converts a fix into a regression of the live history the retention exists to preserve, and the only
thing that can confirm it is the instrumented suite, which needs a device. `NET-004` records what
happens when a change is justified by reasoning and then blessed by a run that never exercised it.

**Scope**
- Decide the mount-time rule: what makes a retained renderer *stale* for its tab, expressed in
  browser-observed values only.
- Keep tab switching reload-free. A rule that fires on A → B → A has failed, and `BROWSE-009`'s
  `TabRendererIsolationTest` is where that shows up.
- Reconcile with `BROWSE-008`: after a Home round trip the renderer still holds the pre-Home history,
  so Back from the newly loaded page reaches a page the browser state has forgotten. Decide whether
  that is the same defect or a second one before changing either.
- Do not fix this by destroying the renderer on Home. That discards the tab's live history, which is
  the thing `BROWSE-006` retains it for.

**Acceptance**
- Page → Home → new address renders the new address, with loading terminating.
- Tab switching still performs no reload; the instrumented isolation cases stay green.
- `bash scripts/pre-commit-check.sh` passes.

---

### `BROWSE-012` — Back after a Home round trip reaches a page the tab forgot

> Filed by `BROWSE-010` as `BROWSE-011` and renumbered when issue
> [#116](https://github.com/denrzv/webora/issues/116) opened under that id. Ids are coined per
> work theme and registered nowhere, so the tracked issue keeps it and the reservation moves.
> Older records — `reports/qa/BROWSE-010.md`, `docs/tasklist/BROWSE-010.md` — cite the new id.

**Priority:** P2
**Depends on:** `BROWSE-008`, `BROWSE-010`
**Goal:** decide whether returning a tab to Home makes the next navigation a history root, and make
the renderer's back stack and `BROWSE-008`'s Back ordering agree either way.

**Found by:** `BROWSE-010`'s research, while answering that ticket's own criterion 6. Recorded here
rather than fixed there, deliberately.

**The disagreement.** `WebView.loadUrl` appends to the renderer's back stack. After X → Home → Y the
renderer holds `[X, Y]`, while `onHome` reset the tab to `BrowserState()` so its browser state knows
only Y. `BROWSE-008` orders Back as *live renderer history → native Home → platform exit*, and the
renderer reports `canGoBack = true`, so Back from Y reaches X — a page the user cleared by going
Home. In a conventional browser Home is the new-tab page and Back from the first navigation returns
to it.

**Why `BROWSE-010` did not change it.** Three reasons, all of which still hold:

1. **It was unreachable, and `BROWSE-010` did not make it worse.** Y never loaded at all, so there
   was no "Back from Y". That fix *exposes* a pre-existing disagreement between renderer history and
   `BROWSE-008`'s ordering; it does not create one.
2. **Every remedy is device-verifiable only.** `WebView.clearHistory()` is documented to be
   unreliable until the current page has committed, so an implementation needs a "clear after the
   next commit" state machine whose correctness is a framework-timing fact no JVM gate can settle.
   `NET-004` records what happens when a change is justified by reasoning and then blessed by a run
   that never exercised it.
3. **It is a navigation-contract decision.** Whether Home is a history root belongs beside
   `BROWSE-008`'s ordering and its existing instrumented Back cases, not in the renderer host.

**Scope**
- Decide explicitly: Home is a history root, or renderer history legitimately outlives a Home visit.
- If it is a root, implement the reset where the Back contract lives, not by destroying the renderer
  — `BROWSE-006` retains it for live history across tab switches.
- Extend the instrumented Back cases to cover X → Home → Y → Back, on a device.

**Acceptance**
- The chosen contract is written down with its reasoning, and Back after a Home round trip matches it.
- Tab switching still performs no reload and loses no live history.
- `bash scripts/pre-commit-check.sh` passes.

---

### `UX-023` — The integrated brand row cannot hold four things at 200% font scale

**Priority:** P2
**Depends on:** `UX-021`
**Goal:** make the integrated header responsive, so browser identity and the site's title both
survive large font scale instead of one starving the other.

**Found by:** `UX-021`'s review, which proposed raising `SECURITY_CHIP_MAX_WIDTH` so a typical
registrable domain survives 200% scale. Measuring the row showed the cap is not the binding
constraint, so the finding's *symptom* was real and its *fix* was not.

**The arithmetic.** On a 320 dp host the brand row has 280 dp after the 20 dp expressive gutters. Back
(48), the logo (40) and three gaps (28) take 116, leaving **164 dp for the title and the trust chip
together**. `example.co.uk` in the chip needs about 117 dp at 100% scale — under the 160 dp cap, so
the cap never binds — and about 198 dp at 200%, where available width binds at 164 first. Raising the
cap to 200 dp therefore widens the chip by 4 dp and takes the title from 4 dp to 0.

So the cap is inert at both ends of the scale, and tuning it trades the site's name for roughly one
more character of domain. One row cannot hold Back, a logo, a manifest-supplied title and a full
domain at 200%; that is a layout problem, not a constant.

**Scope**
- Decide what the integrated header does at large font scale: wrap the identity chip to its own row,
  drop the subtitle first, or reduce the logo slot — browser identity may never be what yields.
- Keep `ADR-006`'s guarantee that the registrable domain is *visible*, not merely announced; a
  truncation to a few characters is closer to the bare-shield layout `UX-021` rejected.
- Keep the ordering guarantee: the chip is declared after the weighted title and carries no weight,
  so a manifest cannot push it out of the header.

**Acceptance**
- At 200% font scale on a 320 dp host, a 13-character registrable domain renders without ellipsis and
  the title is still present.
- The instrumented floor from `UX-021` survives or is raised, never lowered.
- `bash scripts/pre-commit-check.sh` passes.

### `UX-024` — Three browser commands presented as three unrelated controls ✅

**Priority:** P2
**Source:** GitHub issue [#122](https://github.com/denrzv/webora/issues/122)
**Depends on:** `BROWSE-011`, `UX-015`, `UX-021`, `UX-023`
**Goal:** consolidate integrated Back, Forward and Refresh into one compact browser-owned Navigation
Hub, and give `BROWSE-011`'s 40 dp refresh row back to the page.

**The arithmetic.** `ExpressiveSiteSkinHeader` is `heightIn(min = 96.dp)` with a 20 dp gutter and
20 dp of reserved curve, so a brand row plus `BrowserControlRow` measured 136 dp and now measures the
96 dp floor. The dock's five slots became three: at the 320 dp floor the pill has 280 dp of slot
width, so each slot went 56.0 → 93.3 dp and `BRAND_HUB_TARGET_SIZE` centres with 20.6 dp either side
instead of 2.

**Scope**
- Replace the brand row's standalone Back tile with a hub that opens Back, Forward and Refresh; keep
  the 48 dp footprint so `headerIdentityPlacement`'s `HEADER_FIXED_WIDTH` does not move.
- Delete `BrowserControlRow`; introduce no second visible Refresh anywhere in integrated chrome.
- Drop Back and Forward from the dock so no two controls compete for the same browser command
  semantics.
- Keep the browser and site bouquets as separate item models end to end.

**Acceptance**
- The hub opens exactly three actions in compiled order, each ≥48 dp with its own label and
  browser-observed enabled state; the collapsed control is never disabled.
- Selecting closes before dispatching; an outside tap and Android Back close without navigating.
- No manifest value reaches the hub or a bubble, with a negative control.
- `bash scripts/pre-commit-check.sh` passes.

**Shipped.** All five tasks green; `CI-009`/`CI-010` hosted acceptance is pending, because the
integrated frames photograph a header one row shorter and a three-slot dock.
