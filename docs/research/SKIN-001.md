# SKIN-001: Research
Status: RESEARCH_READY

## Question
How the app can project trusted, canonical SiteSkin branding into a narrow light/dark Compose theme
without reopening manifest parsing, weakening the core contrast contract, or giving a site control
of browser-owned security presentation.

## Origins involved
- The currently committed main-frame HTTPS `SiteOrigin` is the serving origin and remains part of
  `BrowserMode.Integrated`; `SiteSkinConfiguration.origin` was exact-origin validated by core.
- No new origin is contacted. Logo transport remains `NET-003`; its asset URL and redirects are
  exact-origin bound there. Theme derivation consumes colour values only and performs no I/O.

## Manifest-controlled surface
- The four optional, canonical `#RRGGBB` fields in trusted `BrandingConfiguration`: primary,
  secondary, background, and text.
- Only a closed set of future SiteSkin surface roles: branded primary/accent containers, integrated
  surface background, and their content colours. Omitted roles receive compiled defaults.
- Dark mode may be derived from the brand inputs, but the algorithm and contrast thresholds are
  entirely browser-owned and deterministic.

## Browser-owned remainder
- The regular browser theme, system bars, typography, shapes, spacing, component placement, error
  UI, confirmation surfaces, menus, and all interaction semantics.
- Registrable domain and TLS indicator remain browser-owned and non-suppressible under ADR-006.
  This ticket creates no top bar and must not expose a generic site-controlled Material
  `ColorScheme` that could later recolour those affordances accidentally.
- Core validation and the private trusted-model constructor remain the trust gate. The app mapper
  accepts `SiteSkinConfiguration`, not DTOs, JSON, or standalone raw colour parameters.

## Relevant code
| Path | Why it matters |
|---|---|
| `siteskin-core/src/main/kotlin/dev/siteskin/core/model/SiteSkinConfiguration.kt` | Public trusted input exposes optional canonical branding after validation while preventing app-side construction. |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/validate/ColorPolicy.kt` | Normative core correction uses WCAG luminance with 4.5:1 body and 3:1 UI targets, but is intentionally internal to core. |
| `siteskin-core/src/main/kotlin/dev/siteskin/core/SecurityValidator.kt` | Corrects supplied primary/secondary/background against supplied or default text before creating the trusted model. |
| `app/src/main/java/app/webora/browser/MainActivity.kt` | Applies only the ordinary browser `MaterialTheme`; SiteSkin theming must not replace this globally. |
| `app/src/main/java/app/webora/browser/browser/BrowserMode.kt` | `Integrated` is the eventual activation seam and already pairs observed origin with trusted configuration. |
| `app/src/main/java/app/webora/browser/siteskin/` | Existing app trust-boundary adapters consume `SiteSkinConfiguration`; the theme mapper belongs alongside them and needs no Android framework wrapper. |
| `app/src/test/java/app/webora/browser/siteskin/` | JVM tests can validate Compose `Color` values without Robolectric, matching the repository's pure-function convention. |

## Prior art
- `docs/ROADMAP.md` and `docs/BACKLOG.md`: trusted branding, dark derivation, and pre-first-paint
  contrast enforcement are the complete ticket scope.
- `spec/SPEC.md` §§5, 6, and 12: branding is optional; canonical colours are expanded; domain/TLS
  styling is forbidden; core applies 4.5:1 body and 3:1 UI correction before trusted output.
- `ADR-006`: validate/correct, then theme, then render; identity chrome cannot be site-restyled.
- `CORE-004`: `SiteSkinConfiguration` is the only trusted input, and core owns normative remote
  input normalization. The app must not duplicate DTO/security validation.
- `ADR-008`/`BROWSE-002`: integrated presentation is represented by a sealed mode containing the
  trusted configuration, although activation itself is deferred to `SKIN-004`.
- Compose Material 3 is already an app dependency, including `androidx.compose.ui.graphics.Color`;
  no dependency or Android SDK API is needed for a pure mapper.

## Risks
- Core corrects only supplied pairs. App defaults and newly derived dark roles create new pairings,
  so the app must guard every final role pair rather than assume upstream correction covers them.
- Exposing full `MaterialTheme.colorScheme` as the domain model would make future browser-owned
  surfaces easy to recolour by accident → define a narrow immutable `SiteSkinColorScheme` first;
  later UI tickets explicitly map only SiteSkin components.
- RGB channel stepping can distort hue and duplicate core policy → derive dark neutral surfaces with
  browser-owned constants, retain validated brand colours where compliant, and use one deterministic
  app guard for final pairings with direct WCAG tests.
- Compose `Color` supports alpha and wide representations, while trusted manifest values do not →
  parse only core's canonical opaque `#RRGGBB` at the single boundary and never accept arbitrary
  `Color` from website-facing callers.
- No runtime SiteSkin screen exists yet → validate by JVM unit tests and APK compilation; a
  screenshot would not show a perceptible change and is deferred to the rendering ticket.

## Open questions
- Exact default palette and dark derivation constants must be fixed in the plan and pinned in tests.
- Decide whether the mapper should expose its WCAG ratio helper internally for precise tests; it
  must not widen core's public API solely for Android presentation.
