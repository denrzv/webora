# Roadmap

Milestone tracker. The reasoning behind it is in [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md);
the recommended implementation order is in [`DEVELOPER_PLAN.md`](DEVELOPER_PLAN.md). This file
records what is done.

Legend: `[ ]` not started · `[~]` in progress · `[x]` done

## M0 — Foundation

- [x] `FOUND-001` AIDD scaffolding, templates, commands, governance docs
- [x] `FOUND-002` Gradle: `:app` + `:siteskin-core` + `:siteskin-lint`, SDK 36, JDK 25 / jvmTarget 21
- [x] `FOUND-003` Gates, CI, SessionStart hook
- [x] `FOUND-004` ADR-001..012

## M1 — SiteSkin API (spec first, then TDD against it)

- [x] `SPEC-001` Manifest v1.0 spec, JSON Schema, conformance fixture corpus
- [x] `SPEC-002` Versioning and compatibility policy
- [x] `CORE-001` Origin model and URL resolution
- [x] `CORE-002` DTOs and parsing
- [x] `CORE-003` Schema validation
- [x] `CORE-004` Security validation and normalization
- [x] `CORE-005` Action model and resolution
- [x] `CORE-006` Navigation active-state matching
- [x] `SPEC-003` `siteskin-lint` CLI — **last in M1**, it wraps what `CORE-001..006` build

## M2 — Browser foundation

- [x] `BROWSE-001` WebView host and hardening
- [x] `BROWSE-002` Browser state machine, URL bar, navigation
- [x] `BROWSE-003` Home screen and onboarding
- [x] `BROWSE-004` Regular-mode chrome, security indicator, error pages
- [x] `BROWSE-005` External navigation, downloads, uploads

## M3 — SiteSkin runtime

- [x] `NET-001` Manifest fetcher
- [x] `NET-002` Manifest cache
- [x] `NET-003` Brand asset pipeline
- [x] `SKIN-001` Dynamic theming and contrast guard
- [x] `SKIN-002` SiteSkin top bar
- [x] `SKIN-003` Bottom navigation, quick actions, menu
- [x] `SKIN-004` Mode transitions and origin-change deactivation

## M4 — Hardening, privacy, demo

- [x] `HARDEN-001` Adversarial manifest corpus
- [x] `HARDEN-002` Brand-impersonation controls
- [x] `PRIV-001` Privacy controls and per-site toggles
- [x] `A11Y-001` Accessibility
- [x] `DEVX-001` SiteSkin Integration Inspector
- [x] `DEMO-001` Bloom Flowers reference integration

## M5 — Distribution

- [x] `DIST-001` Debug APK on a GitHub Release, with install instructions
- [x] `CI-001` Manual GitHub-hosted Android screenshots against the live integration

## M6 — Design refresh

Browser-owned surfaces only. Opened after the `DIST-001` APK was run on an emulator: the browser
gives *websites* a six-role colour system with a contrast guard and gives itself a bare
`MaterialTheme {}`. Evidence in [`design/AUDIT.md`](design/AUDIT.md); the candidate directions are
in [`design/directions/`](design/directions/index.html).

- [x] `UX-001` Design direction sketches and selection — deliverable is a decision, plus `ADR-013`
- [x] `UX-006` Attributed, bounded SiteSkin preview in first-use consent
- [ ] `UX-002` Design system foundations — `WeboraTheme` tokens, browser-owned vector icon set, dark theme
- [ ] `UX-003` Browser-owned chrome rebuild — address bar, navigation controls, menu, error page
- [ ] `UX-004` Home, onboarding and settings surfaces

## M7 — Visual quality, evidence & integration polish

Opened from the first successful live Pixel 6 / API 33 screenshot journey. The workflow proved the
integration end to end, but the evidence also made several quality defects impossible to ignore:
System UI ANR overlays contaminated otherwise-passing captures, screenshots were cumbersome to
review, the debug inspector dominated the demo frame, SiteSkin navigation still used placeholder
Unicode glyphs, and the consent actions were visually weak on a phone-sized viewport.

Evidence/DX track — can proceed without waiting for all M6 surfaces:

- [x] `CI-002` Deterministic clean Android screenshot capture — eliminate known System UI
  contamination without hiding Webora failures
- [x] `DEVX-002` Screenshot review experience — separate human/diagnostic artifacts and add one
  labelled contact-sheet preview
- [x] `CI-003` Capture must wait for rendered content — pixel check on the page region, not semantics
- [x] `DEVX-003` Inspector isolation and canonical evidence mode — the affordance moved into the two
  browser menus, one per mode, and left canonical composition. `CI-003`'s residual hole is closed:
  `SiteSkinQuickActions` is now the only non-page chrome inside the measured region, and it was
  already excluded

Product-polish track:

- [ ] `UX-005` SiteSkin integrated chrome & semantic icon set — **revived** after live evidence;
  replace placeholder glyphs with browser-owned vector icons and meaningful quick actions
- [ ] `UX-008` Browser navigation controls in integrated mode — an active skin currently leaves no
  visible back control; found by `CI-003`'s run 11 once the frame rendered
- [ ] `UX-007` Adaptive SiteSkin consent action hierarchy — clear primary/secondary/persistent-deny
  actions across narrow widths and large font scales
- [ ] `DEMO-003` Bloom Flowers visual fidelity & protocol showcase — exercise the polished icon and
  action vocabulary in the live reference integration

`UX-005` depends on `UX-002`; `UX-007` builds on the already-complete `UX-006`; `DEMO-003` is the
final product-facing evidence after `UX-005`, `UX-007`, and `DEVX-003`. The detailed parallel order
and gates are in [`DEVELOPER_PLAN.md`](DEVELOPER_PLAN.md).

### Why `UX-005` is no longer descoped

`UX-005` was parked while the browser itself had no design system. That sequencing was correct, but
its original risk is now visible in real evidence: the Bloom Flowers demo — the product's headline
integrated surface — shows `⌂ ▦ ▣ ● ☎` placeholders and an undifferentiated quick-action affordance.
The SiteSkin manifest already supplies semantic icon names; trusted chrome must continue mapping
those names to **browser-owned** assets rather than accepting arbitrary site-provided icons. M7
revives the ticket once `UX-002` supplies that asset foundation.

## Descoped

Not abandoned — parked, with the reasoning kept in [`BACKLOG.md`](BACKLOG.md) so reviving any of
them does not start from nothing. `SCOPE-001` records the decisions.

- `DEMO-002` PixelPlay, Daily Journal, Example News — one demo is enough for now. The browser
  behaviour they would have demonstrated live (skin swap, skin drop on origin change) is implemented
  and unit-tested under `SKIN-004`; what lapses is the demonstration, not the capability.
- `PLAY-001` Compliance sweep · `PLAY-002` Release signing, R8 keeps, versioning ·
  `PLAY-003` Store listing, Data safety, internal testing track — distribution is an APK handed to
  friends, not a store listing. **`targetSdk 36` is not descoped**: it shipped in `FOUND-002` and is
  in the build today.

---

## Definition of done for the first demonstrable prototype

From concept §62. All must hold:

- app installs and launches; arbitrary HTTPS browsing works
- regular websites remain fully usable
- manifests are discovered, and invalid ones fail safely
- Bloom Flowers renders with its own branding
- a site without a manifest stays in regular mode
- native bottom navigation opens the correct routes
- an origin change deactivates SiteSkin mode — covered by `SKIN-004`'s tests rather than by a second
  demo origin, which is descoped with `DEMO-002`
- **no manifest can hide the domain or the TLS indicator** (`ADR-006`)
- core validation and navigation logic is covered by automated tests

M7 adds a visual-quality bar on top of that functional prototype: canonical evidence must be clean,
cheap to review, free of debug/OS contamination, and representative of the intended SiteSkin UX.
