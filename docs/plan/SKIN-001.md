# SKIN-001: Implementation plan
Status: PLAN_APPROVED

## Overview
Add a pure app-layer projector from the already trusted `SiteSkinConfiguration` to a narrow,
immutable pair of SiteSkin colour schemes. The projector parses canonical opaque core colours once,
fills missing roles with compiled browser defaults, derives a dark surface deterministically, and
then applies one WCAG guard to every final foreground/container pairing before returning either
scheme. No composable, state holder, or network component changes in this ticket.

## Flow
- Discovery: unchanged. `NET-001`/`002` obtain and revalidate manifest bytes without blocking page
  rendering.
- Validation: unchanged. `SiteSkinValidator` is still the sole remote-input trust boundary.
- Normalization: core supplies optional canonical opaque `#RRGGBB` branding and has already guarded
  manifest-declared pairings. The app never accepts DTO/JSON colour input.
- UI state: `SiteSkinTheme.from(configuration)` creates both light and dark schemes as pure work
  before future integrated UI composition. `SKIN-002` will select one from device mode; `SKIN-004`
  will own activation. This ticket does not apply a theme globally or render it.

## Data
- Trust boundary: the only public factory parameter is `SiteSkinConfiguration`. The resulting
  `SiteSkinTheme` contains `light` and `dark` `SiteSkinColorScheme` values. Each scheme exposes only
  `primary`, `onPrimary`, `secondary`, `onSecondary`, `background`, and `onBackground` Compose
  colours. It is deliberately not a generic Material `ColorScheme`.
- Defaults: light primary `#3F51B5`, secondary `#5C6BC0`, background `#FFFFFF`, text `#1B1B1F`;
  dark primary/secondary seeds reuse trusted brand seeds, background is a deterministic 20% mix of
  the trusted/default light background over black, and dark content uses `#FFFFFF`.
- Light uses trusted text as content when present. Trusted primary, secondary, and background seed
  their corresponding roles, with defaults per missing field. Dark preserves primary and secondary
  seeds and derives only the neutral surface; the final guard may shift a container toward the
  nearest black/white endpoint required by its browser-chosen foreground.
- Storage/cache keys: none. Projection is deterministic and cheap; callers may `remember` it later
  with the trusted configuration identity, but this ticket adds no cache.

## Security
- Origin binding is already established by the trusted configuration and is not recomputed. Theme
  derivation performs no URL handling or I/O, so it cannot broaden the serving or asset origin.
- The closed six-role model is the allow-list. Manifest input cannot name roles, provide alpha,
  control system/regular-browser colours, or reach typography and browser identity styling.
- Parsing is intentionally strict for core's canonical `#RRGGBB` invariant. Because callers cannot
  construct a trusted configuration outside core, malformed colours are not a recoverable remote
  case. Missing values, including all branding, always use complete deterministic defaults.
- The final app guard calculates WCAG relative luminance in sRGB and adjusts only the container in
  one-channel-step increments toward black or white until 4.5:1 for background/body content or
  3:1 for primary/secondary UI content. It runs for both modes after derivation and before a scheme
  is returned. Browser-owned domain/TLS presentation is not part of the scheme.

## File-by-file plan
### New: `app/src/main/java/app/webora/browser/siteskin/SiteSkinTheme.kt`
Define the narrow immutable theme/scheme model, trusted-configuration factory, canonical colour
parser, deterministic dark-surface mixer, relative-luminance calculation, and final contrast guard.

### New: `app/src/test/java/app/webora/browser/siteskin/SiteSkinThemeTest.kt`
Create trusted configurations through the public validator and cover complete branding, defaults,
partial branding, canonical mapping, deterministic dark derivation, all required pair ratios, and
the hostile matching text/background case. Include a named negative-control assertion.

### Modified: `CLAUDE.md`
After implementation, record the stable SKIN-001 boundary: only trusted configurations enter the
closed theme projector, every output pair is guarded before exposure, and browser security chrome
is excluded.

## Tests
- `./gradlew :app:testDebugUnitTest --tests app.webora.browser.siteskin.SiteSkinThemeTest`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug` to verify Compose colour use remains dexable.
- `bash scripts/pre-commit-check.sh` before every task commit.
- Negative control: temporarily return an unguarded container and verify the matching-colour test
  fails its 4.5:1 assertion; restore the guard and record the result in the tasklist.

## Rollout / versioning
No manifest/schema, core public API, persistence, activation, or visible UI change. This creates the
presentation primitive consumed by later M3 tickets, so no migration or feature flag is required.

## Open questions
- None. Defaults, role scope, dark derivation, correction direction, thresholds, and ownership are
  fixed above so implementation does not invent policy.
