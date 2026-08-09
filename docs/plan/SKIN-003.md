# SKIN-003: Implementation plan
Status: PLAN_APPROVED

## Overview

Add one pure presentation model that defensively projects trusted `SiteSkinConfiguration`
collections into fixed bottom-navigation, quick-action, and menu sections. Add standalone Compose
components that emit typed item selections and own only transient expanded/dismissed state. Use the
existing `NavMatcher` with the browser-observed page URL for active navigation. Actual integrated
mode composition, action resolution/dispatch, and WebView/Android effects remain for `SKIN-004`.

## Flow

- Discovery and validation are unchanged. Core remains the only remote-input trust boundary and
  supplies normalized, first-N collections with symbolic allow-listed icons.
- A pure factory receives trusted configuration plus the browser-observed current page URL, applies
  defense-in-depth first-N caps (5 navigation, 5 quick actions, 20 menu), and stores stable ids,
  bounded labels/icons, and the original trusted item for typed selection.
- `NavMatcher` derives one active navigation id or none. No UI heuristic selects the first item.
- Compose renders absent empty surfaces, equal-width single-line bottom items, a FAB that expands a
  bounded quick-action surface, and a modal menu with separate site and browser-owned sections.
- Components collapse/dismiss after selection and emit only the trusted item or a closed
  browser-command enum. They do not resolve actions, navigate, or launch Android capabilities.

## Data

- `SiteSkinChromeModel` is immutable and created from `SiteSkinConfiguration`, never DTOs or raw
  manifest strings. Each `SiteSkinItemModel` retains its trusted `NavigationItem` and exposes id,
  label, and normalized symbolic icon for rendering.
- `BrowserMenuCommand` is a closed app-owned enum for page/security information and settings. It is
  not representable by remote menu entries and is always rendered in a fixed section.
- Stable id equality, rather than `NavigationItem` object identity, drives selected state.
- No storage or cache is added. Expanded FAB/menu state is local ephemeral Compose state.

## Security

- Origin boundary: all website-controlled values come through an origin-bound trusted
  configuration. The current page URL is browser observed and is used only by `NavMatcher`; UI code
  does not parse or compare origins.
- Icon rendering uses a closed local symbolic mapping with a generic fallback and never performs
  resource, file, content, or network lookup from an icon string.
- Typed callbacks preserve the trusted-model boundary. There is no callback taking arbitrary URI,
  intent, package, component, flags, extras, MIME type, or permission.
- The menu structurally appends a separately labelled browser section after site items. Remote
  entries cannot suppress, rename, replace, or reorder those closed commands.
- Empty collections remove only their optional surface. Oversized inputs are capped again without
  throwing; the WebView and browser mode are unaffected.
- Negative controls temporarily remove caps/active matching/browser command insertion to prove the
  focused tests fail before restoring protections.

## File-by-file plan

### New: `app/src/main/java/app/webora/browser/siteskin/SiteSkinChromeModel.kt`
Define the immutable pure presentation projection, fixed caps, active-id derivation through
`NavMatcher`, typed item model, and closed browser menu commands.

### New: `app/src/main/java/app/webora/browser/siteskin/SiteSkinChrome.kt`
Render bottom navigation, quick-action FAB/expanded menu, and modal side menu with stable semantics,
minimum touch targets, ellipsized labels, closed icon mapping, and structurally separate browser
commands.

### New: `app/src/test/java/app/webora/browser/siteskin/SiteSkinChromeModelTest.kt`
Build trusted configurations through `SiteSkinValidator`; cover limits, order, empty collections,
active/no-match behavior, typed item preservation, and immutable browser commands.

### New: `app/src/androidTest/java/app/webora/browser/siteskin/SiteSkinChromeTest.kt`
Exercise Compose semantics, exact rendered caps, selected state, quick-action expansion/dismissal,
menu section separation, long-label ellipsis geometry, and minimum target sizing.

### Modified: `app/src/main/res/values/strings.xml`
Add browser-authored quick-action, menu-section, page/security information, settings, and generic
icon accessibility wording.

### Modified: `CLAUDE.md`
Record the stable SiteSkin navigation presentation boundary and deferred runtime dispatch/wiring.

## Tests

- `./gradlew :app:testDebugUnitTest --tests app.webora.browser.siteskin.SiteSkinChromeModelTest`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:compileDebugAndroidTestKotlin`
- `./gradlew detekt`
- `./gradlew :app:assembleDebug`
- `bash scripts/pre-commit-check.sh` before every task commit.
- Negative controls: remove the first-N projection, force first-item selection on no match, and omit
  fixed browser commands; verify focused tests fail independently, then restore each protection.
- Run device instrumentation and capture a screenshot only if connected Android hardware exists;
  do not provision a software-only emulator in managed cloud.

## Rollout / versioning

No schema, trusted-core model, persistence, network, activation state, or regular-browser behavior
changes. These are reusable components until `SKIN-004` composes them for an origin-bound integrated
mode and connects typed selections to `ActionResolver` and browser-owned dispatch.

## Open questions

None.
