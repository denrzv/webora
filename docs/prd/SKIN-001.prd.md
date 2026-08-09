# SKIN-001: Dynamic theming and contrast guard
Status: PRD_READY

## Context / Problem
SiteSkin configurations already cross the core trust boundary with canonical, contrast-corrected
branding colours, but the Android app has no closed presentation model that can safely turn those
values into Compose colours. Integrated chrome therefore cannot adopt a site's identity without
either reparsing remote strings in UI code or allowing an inaccessible colour pairing to reach its
first frame.

## Goals
- Derive an immutable light and dark SiteSkin colour scheme from a validated
  `SiteSkinConfiguration` before any integrated chrome is rendered.
- Preserve the core validator's corrected foreground/background relationships when mapping colours
  to browser-owned Compose roles.
- Provide deterministic browser-owned defaults for omitted branding values.
- Keep all remote-input parsing and theme selection outside composables.

## Non-goals
- Rendering the SiteSkin top bar, bottom navigation, quick actions, or menu (`SKIN-002`/`003`).
- Activating or deactivating integrated mode (`SKIN-004`).
- Changing the SiteSkin schema, core colour-normalisation contract, or brand asset pipeline.
- Allowing a manifest to control system bars, typography, dimensions, security indicators, or the
  ordinary browser theme.

## User stories
- As a user, I see site-branded chrome whose text and controls remain legible in light and dark
  device modes.
- As a site owner, I get a predictable theme derived from my already validated brand colours, with
  safe defaults when optional colours are absent.
- As a security reviewer, I can verify that only a closed set of colour roles is website-controlled
  and that browser security affordances remain outside that set.

## Acceptance criteria
1. A pure Android-app mapping converts trusted canonical branding colours into a closed SiteSkin
   light/dark theme model; composables do not parse manifest colour strings.
2. Every produced foreground/background and icon/container pairing meets the applicable WCAG AA
   ratio before the theme is exposed: 4.5:1 for body text and 3:1 for UI components.
3. A manifest whose supplied text and background match produces a corrected AA-compliant theme
   before render, with a negative-control test that fails if the guard is bypassed.
4. Dark colours are derived deterministically from trusted branding, remain recognisably branded,
   and independently satisfy the same contrast guard rather than relying on the light scheme.
5. Missing optional branding and missing branding as a whole resolve to deterministic
   browser-owned defaults without throwing or producing a blank/transparent role.
6. The mapping accepts only trusted `SiteSkinConfiguration`; it cannot accept a raw manifest DTO,
   JSON, URL, or arbitrary website-defined theme role.
7. Unit tests cover light mode, dark derivation, corrected low-contrast input, omitted values, and
   stable deterministic output.
8. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: Website input controls only bounded colour roles after shared validation; it
  cannot suppress or restyle browser-owned identity and TLS affordances. No telemetry is added.
- Reliability/fallback: All trusted configurations, including absent branding, produce a complete
  usable scheme with no I/O and no exceptional fallback path.
- Performance: Theme construction is synchronous, deterministic pure work suitable for completion
  before first integrated-chrome composition; no network, disk, or bitmap work occurs.
- Accessibility: Text/background contrast is at least 4.5:1 and UI component contrast at least
  3:1 in both light and dark schemes.

## Risks
- Re-correcting individual Compose roles can drift from core's normative colour policy; the plan
  must reuse a single app-level guard and pin expected pairings in tests.
- Naive darkening can collapse brand colours toward indistinguishable black or reduce contrast; dark
  derivation must be deterministic and followed by contrast enforcement.
- Passing a generic Material `ColorScheme` too early could imply that sites control all Material
  roles, including future browser-owned surfaces; expose a narrower SiteSkin-specific model.

## Open questions
- Which trusted branding field seeds each closed role, and which browser-owned defaults fill gaps?
- Should the app reuse a public core contrast primitive or own a small pure Android mapper over the
  already corrected canonical values?
