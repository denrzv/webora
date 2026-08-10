# Review: PRIV-001
Date: 2026-08-10
Status: RESOLVED

## Summary
The implementation preserves the origin boundary and adds useful pure seams, but two app-state
issues must be corrected before QA.

## Architecture
| Concern | Assessment |
|---|---|
| Core boundary | PASS — privacy behavior stays in the app module. |
| Browser ownership | PASS — manifests cannot influence controls or deletion scope. |
| Async completion | NEEDS FIX — cookie callback meaning is interpreted incorrectly. |

## Security
| Property | Assessment |
|---|---|
| Exact-origin consent | PASS — canonical keys round-trip before display. |
| Global fail-closed switch | PASS — start, publication, and active-state paths are guarded. |
| Honest deletion result | NEEDS FIX — `false` from `removeAllCookies` means no cookies were removed, not failure. |

## Findings

### FINDING-1 · Medium · state consistency
**File:** `app/src/main/java/app/webora/browser/browser/BrowserScreen.kt`

The settings list snapshots consent decisions before new consent dialog actions. An Allow/Never
decision made later in the same composition is absent until process recreation. Refresh the list
after saves, and deactivate integrated chrome after clearing its supporting consent data.

### FINDING-2 · Medium · truthful completion
**File:** `app/src/main/java/app/webora/browser/privacy/BrowsingDataCleaner.kt`

Android's cookie callback Boolean reports whether cookies were removed, so `false` is also the
successful no-cookies case. Model adapter execution failure separately, continue all deletion
steps, and report incomplete only when an adapter throws/fails to execute.

## Not findings
- Preserving the global switch and onboarding is intentional: they are app preferences, not
  browsing/site data, and the confirmation copy states this.
- Base64 consent keys are not treated as trust: decoded strings must parse and canonical-round-trip.
- Settings shows the complete origin rather than a friendly site name so decisions retain the same
  scheme/host/port boundary as persistence.
- Restoring the malformed JSON fixture is unrelated code repair but mandatory: its prior conversion
  to valid JSON broke the existing conformance negative control and pre-commit gate.

## Test coverage
| File | Tests | Coverage |
|---|---|---|
| `SiteConsentStore.kt` | `SiteConsentStoreTest` | persistence, isolation, listing, invalid key, removal, clear |
| `SiteSkinRuntime.kt` | `SiteSkinRuntimeTest` | origin/generation/global-off publication guards |
| `BrowsingDataCleaner.kt` | `BrowsingDataCleanerTest` | adapter coverage and completion |
| settings UI | `PrivacySettingsScreenTest` | controls and explicit confirmation (compiled) |

## Verdict
RESOLVED by `dc6bd4f` (TASK-FIX-1) and TASK-FIX-2.
