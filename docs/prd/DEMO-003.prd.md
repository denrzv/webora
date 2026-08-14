# DEMO-003: Bloom Flowers visual fidelity & protocol showcase
Status: PRD_READY

## Context / Problem

Bloom Flowers is Webora's single live reference integration, but it currently proves primarily that
SiteSkin works rather than showing the finished protocol vocabulary coherently. The completed
semantic-icon, consent-layout, inspector-isolation, and screenshot-evidence work now makes it
possible for the reference fixture, its documentation, and the canonical Pixel 6 journey to serve
as reproducible product-quality evidence instead of a collection of placeholders.

## Goals
- Make the checked-in and live Bloom Flowers manifest deliberately exercise meaningful semantic
  icons for Home, Flowers/Catalog, Cart, Account, and the Call-shop quick action.
- Keep fixture labels, routes, match patterns, actions, consent preview, and the deployed site in
  agreement.
- Document the manifest choices as readable reference material a site owner can reproduce.
- Capture clean Home → consent → integrated Pixel 6 evidence of the completed experience.

## Non-goals
- Adding another demo origin or reviving `DEMO-002`.
- Expanding the SiteSkin schema, semantic icon allow-list, action vocabulary, or browser chrome.
- Adding a framework or arbitrary remote/browser-specific visual assets to Bloom Flowers.
- Weakening origin binding, consent, browser-owned identity chrome, or screenshot ownership checks.

## User stories
- As a site owner, I can copy a small, documented manifest whose labels, icons, routes, and action
  semantics correspond to a real deployed site.
- As a reviewer, I can inspect clean canonical evidence showing the polished consent and integrated
  SiteSkin experience without debug or operating-system contamination.
- As a user, I see recognizable navigation and call icons, an unambiguous selected destination, and
  a quick action that describes what it will do.

## Acceptance criteria
1. The checked-in reference manifest and the manifest served from `https://denrzv.github.io` use the
   final semantic icon vocabulary for Home, Flowers/Catalog, Cart, Account, and Call-shop, with
   labels, destinations, patterns, and actions matching the deployed site.
2. `siteskin-lint https://denrzv.github.io` accepts the live manifest without errors.
3. `INTEGRATION.md` explains the selected semantic icons and route/match choices well enough for a
   site owner to reproduce the pattern without arbitrary browser-specific assets.
4. The canonical Pixel 6 / API 33 Home → consent → integrated journey produces three uncontested
   frames; the consent frame retains the `UX-007` hierarchy and the integrated frame shows meaningful
   vectors, a selected navigation item, a coherent quick action, and no inspector overlay.
5. The existing protocol and app tests continue to prove bounded manifest influence, browser-owned
   identity, typed action dispatch, active-route matching, and graceful regular-browser fallback.
6. `DEMO-002` remains descoped and no additional origin, framework, schema capability, or remote
   native-chrome asset mechanism is introduced.
7. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: all site input continues through the existing validator and exact-origin trust
  boundary; domain/TLS chrome, consent decisions, icon assets, and effect dispatch remain browser-owned.
- Reliability/fallback: invalid or unavailable live manifests still degrade to regular browsing and
  screenshot capture remains fail-closed when another window owns the frame.
- Performance: the static fixture adds no framework, runtime dependency, or blocking discovery work.
- Accessibility: every semantic icon remains decorative beside a bounded text label; action names,
  selected state, 48 dp targets, and 200% font-scale behavior remain the accessibility contract.

## Risks
- The live Pages content can drift from the checked-in fixture; validation must compare the live
  manifest and rendered routes rather than treating repository intent as deployment evidence.
- A visually attractive fixture could accidentally imply new trust; implementation must use only
  already-validated semantic tokens and typed actions while preserving browser-owned attribution.
- Hosted emulator noise can invalidate screenshots despite passing behavioral assertions; canonical
  evidence must retain the existing uncontested-frame checks and readiness gate.

## Open questions
None. Research must locate the authoritative fixture/deployment path and confirm the current live
origin before the plan commits to files or hosted evidence steps.
