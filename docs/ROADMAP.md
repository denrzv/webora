# Roadmap

Milestone tracker. The reasoning behind it is in [`DEVELOPMENT_PLAN.md`](DEVELOPMENT_PLAN.md);
this file records what is done.

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

- [ ] `DIST-001` Debug APK on a GitHub Release, with install instructions

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
