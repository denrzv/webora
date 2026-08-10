# PRIV-001: Implementation plan
Status: PLAN_APPROVED

## Overview
Add browser-owned privacy preferences and settings around the existing SiteSkin runtime, then add a
closed browsing-data deletion service and document the resulting privacy posture.
## Flow
- Browser startup reads a local global SiteSkin preference (default enabled).
- When enabled, page starts retain the existing asynchronous discovery/validation flow. When
  disabled, discovery is cancelled/skipped, pending consent is dismissed, and integrated mode is
  projected back to regular mode. Outcomes also recheck enablement before prompting/activation.
- Settings enumerates recognized consent entries as full canonical origins. Removing an entry makes
  that origin undecided without affecting any other origin.
- Confirmed data clearing invokes a closed cleaner: asynchronous cookies, Web Storage, live WebView
  cache/form data, manifest cache, and consent decisions. Browser preferences remain intact.
## Data
- Manifest DTO/configuration trust boundaries are unchanged. Preferences are browser state, never
  manifest input.
- Consent keys remain reversible Base64url encodings of `SiteOrigin.canonical`; enumeration accepts
  only keys that decode, parse, and round-trip canonically and values matching the closed decision
  enum.
- The global Boolean uses a separate browser preference file so clear browsing data cannot erase it.
## Security
- Exact canonical origins remain the only per-site keys and the only origin strings shown.
- Settings actions are a closed browser command set; no URI/action fields come from the website.
- Global-off is enforced at discovery start and publication, and deactivates current chrome.
- A deletion failure is surfaced as incomplete rather than a false success. No telemetry is added.
## File-by-file plan
### New
- `app/.../privacy/PrivacySettingsStore.kt`: persistent global setting with a pure preference seam.
- `app/.../privacy/BrowsingDataCleaner.kt`: closed asynchronous deletion orchestration and Android
  platform adapter.
- `app/.../browser/PrivacySettingsScreen.kt`: browser-owned settings UI/model.
- JVM tests for stores, global runtime policy, settings models, and deletion orchestration.
- `docs/privacy/DATA_SAFETY.md`: implementation-backed Play Data safety mapping.

### Modified
- `SiteConsentStore.kt`: safe listing, individual removal, and clearing.
- `ManifestCache.kt`/discovery coordinator: explicit cache clear operation.
- `BrowserState.kt`: lossless integrated-to-regular deactivation.
- `BrowserScreen.kt`: settings navigation, global guard, and clear-data confirmation/result.
- `BrowserWebViewController.kt`: command for live-WebView cache/form deletion.
- `strings.xml`, `CLAUDE.md`, and `docs/ROADMAP.md`.
## Tests
- Unit: preference defaults/persistence; consent enumeration, invalid-entry rejection, origin
  isolation/removal/clear; global-off candidate disposition and state deactivation; cleaner ordering,
  completion, failure; cache clear.
- Instrumentation: settings semantics and confirmation UI compiled and, when a device is connected,
  exercised. Managed cloud without a device reports runtime instrumentation/screenshots unavailable.
- Gate: `bash scripts/pre-commit-check.sh` before each task commit.
## Rollout / versioning
No schema/protocol migration. Existing consent entries retain their encoding. The global switch
defaults enabled to preserve existing behavior until the user opts out.
## Open questions
None.
