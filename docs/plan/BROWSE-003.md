# BROWSE-003 implementation plan

Status: PLAN_APPROVED

## Flow and trust boundary

At activity creation, a thin local preference store reports whether general product onboarding was completed. A pure launch decision selects onboarding or a Home-mode `BrowserState`. Onboarding completion writes only a browser-owned Boolean and reveals Home. It is distinct from ADR-011's future per-origin SiteSkin consent.

Home renders entirely from browser-owned state. Typed text and selected suggestions enter the existing `AddressResolver`; only a resolved HTTP(S) command can transition into the hardened renderer. Suggested integrations are created by a pure catalogue validator that accepts only absolute HTTPS URLs without credentials. Websites and manifests cannot add, reorder, label, theme, or provide artwork for Home entries.

## Changes

1. Add pure onboarding/launch state plus a thin `SharedPreferences` completion store.
2. Add a browser-owned, HTTPS-only suggested-site catalogue and tests proving unsafe entries fail closed.
3. Add accessible Compose onboarding and Home surfaces, with address entry, empty recent/favourite states, and safe suggestion callbacks.
4. Make the activity launch decision and browser screen lifecycle respect Home mode, without creating/loading WebView until navigation.
5. Compile Android tests and document/review/QA the completed behavior.

## File-by-file plan

- New `browser/HomeModels.kt`: validated suggested entries and default immutable catalogue.
- New `browser/OnboardingState.kt`: pure first-launch decision and pages.
- New `browser/OnboardingStore.kt`: Android preferences wrapper.
- New `browser/HomeScreen.kt` and `browser/OnboardingScreen.kt`: local Compose surfaces.
- Modify `browser/BrowserScreen.kt`, `BrowserState.kt`, and `MainActivity.kt`: Home navigation and startup composition.
- Modify strings and add JVM/instrumentation tests.

## Security and privacy checks

Negative tests must prove non-HTTPS, credential-bearing, malformed, and relative suggested targets cannot enter the catalogue. Home submissions reuse existing denied-scheme tests and resolver. Onboarding persists no identifiers, URLs, or analytics. Home must perform no network work before an explicit navigation.

## Validation

Run focused app unit tests and lint for every code change. Before each task commit run `bash scripts/pre-commit-check.sh`. Compile Android instrumentation sources. If no device or KVM is available, report runtime instrumentation and screenshot capture as an environment limitation.
