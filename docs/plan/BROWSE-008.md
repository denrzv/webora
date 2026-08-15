# BROWSE-008: Implementation plan
Status: PLAN_APPROVED

## Flow

1. A pure browser Back policy reports whether the current mode can consume Back: every non-Home
   tab can, irrespective of observed WebView history; Home cannot.
2. The shared Back action asks the active tab's controller to navigate live WebView history first.
3. If the controller cannot go back and the active state is not Home, reset only that tab to a new
   Home `BrowserState`.
4. Visible regular and integrated Back controls receive this availability and action. Android
   system/predictive Back registers the same action only while it can be consumed.
5. From Home, no callback consumes Back, so the platform owns exit behavior.

## Trust and origin boundary

The manifest controls none of this flow. WebView history is browser-observed runtime state; Home is
a browser-owned sealed mode. The fallback performs no URL parsing, navigation, network operation,
or cross-origin inheritance. Resetting the active state removes its trusted SiteSkin configuration
from presentation while retained controllers and states belonging to other tabs are untouched.

## File-by-file changes

- Add `BrowserBack.kt` with a small sealed/enum decision and pure executor that takes callbacks for
  live history and Home transition, enabling fast negative-control tests without Android.
- Modify `BrowserScreen.kt` to construct one current-state Back action, wire it to the system
  dispatcher, and pass it through `RegularBrowser` to both chrome variants.
- Add `BrowserBackTest.kt`; extend session and Compose tests for tab isolation and enabled
  first-page controls. Compile Android tests because this checkout has no device/KVM.
- Update normative architecture notes and ticket tracking after review and QA.

## Tests

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:compileDebugAndroidTestKotlin`
- `./gradlew detekt`
- `bash scripts/pre-commit-check.sh` before each task commit.

## Rollout

No persistence or schema migration. Existing restored first-page tabs gain Home fallback based on
their sealed mode. No feature flag is needed.

## Open questions

None. The backlog acceptance defines history → Home → platform precedence.
