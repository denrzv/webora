# SKIN-002: SiteSkin top bar
Status: PRD_READY

## Context / Problem
Validated SiteSkin branding and the browser's theme projection exist, but integrated mode has no
top bar that can present that branding without surrendering the browser's identity and transport
signals. The top bar must combine a bounded site logo and title with security chrome whose content,
visibility, typography, and layout remain browser-owned.

## Goals
- Render a reusable SiteSkin top bar from trusted configuration, projected theme, brand asset
  result, and browser-observed origin/TLS state.
- Always show the registrable domain and TLS indicator in an immovable browser-owned region.
- Confine decoded logos to a fixed slot and provide the existing monogram fallback without allowing
  intrinsic image dimensions to alter the bar.
- Keep presentation state pure and unit-testable without Robolectric; leave Android wrappers and
  Compose rendering thin.
- Preserve accessible semantics, minimum touch targets, truncation, and contrast at large font scale.

## Non-goals
- SiteSkin activation, consent, origin-change deactivation, or redirect policy (`SKIN-004`).
- Bottom navigation, quick actions, side menu, or their actions (`SKIN-003`).
- Fetching, validating, caching, or decoding manifests and brand assets (`NET-001`–`003`).
- Allowing manifests to hide, replace, restyle, or provide the domain, TLS state, overflow action,
  security sheet, or settings entry points.

## User stories
- As a user, I can see a site's trusted title and bounded logo while still seeing which registrable
  domain controls the page.
- As a user, I can distinguish a secure HTTPS page through a browser-owned TLS indicator.
- As a user relying on accessibility services or large text, I can identify the brand and security
  information without overlap or loss of meaning.

## Acceptance criteria
1. The SiteSkin top bar renders a trusted site title and either a successfully decoded logo or the
   browser-generated monogram fallback inside a fixed-size logo slot.
2. The top bar always renders the browser-observed registrable domain and TLS indicator; no
   manifest-derived input can suppress, replace, or reorder that security region.
3. A negative-control unit test fails if the domain or TLS indicator is removed from the pure top-bar
   presentation model.
4. An oversized or extreme-aspect-ratio logo remains clipped/scaled within its slot and cannot
   resize or overlap the title or security region.
5. Long titles and domains truncate without overlap while security semantics remain available to
   accessibility services.
6. SiteSkin colours are accepted only through `SiteSkinTheme`; raw manifest DTOs, JSON, and colour
   strings do not cross the UI boundary.
7. The top bar can be previewed or tested in both light and dark projected schemes, and ordinary
   browser chrome remains unchanged.
8. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: the registrable domain and TLS state are always visible and browser-owned; the
  component emits no telemetry and logs no URLs.
- Reliability/fallback: missing or failed brand imagery uses the monogram and never hides the bar or
  breaks page rendering.
- Performance: top-bar state derivation is synchronous pure work; image fetch/decode stays outside
  composition and no network work is introduced.
- Accessibility: brand and security content have distinct semantics, controls meet 48 dp targets,
  and information is not conveyed by colour alone.

## Risks
- A visually dominant brand region could crowd out or obscure the domain. Mitigate with fixed region
  ownership, bounded dimensions, weights, ellipsis, and tests at long inputs.
- Reusing manifest title text in combined accessibility descriptions could imply that the site owns
  the security signal. Mitigate with separate browser-authored descriptions.
- A top bar coupled directly to runtime activation would make `SKIN-004` harder to validate.
  Mitigate by exposing an explicit render model/component without changing browser mode here.

## Open questions
- None. ADR-006 fixes the security chrome ownership and the existing brand-asset and theme contracts
  fix the trusted inputs.
