# QA Report: PRIV-001
Status: QA_PASSED

## Scope
Global and per-origin SiteSkin privacy controls, confirmed browsing-data deletion, privacy
documentation, and regression coverage for the SiteSkin/browser trust boundary.

## Test scenarios
| # | Scenario | Method | Result |
|---|---|---|---|
| 1 | Global setting defaults enabled and persists off | JVM `PrivacySettingsStoreTest` | PASS |
| 2 | Global off rejects an accepted/current/allowed candidate | JVM negative-control `SiteSkinRuntimeTest` | PASS |
| 3 | Turning off preserves page and drops integrated chrome | JVM `BrowserStateTest` | PASS |
| 4 | Decisions isolate scheme, host, and port; list/remove/clear safely | JVM `SiteConsentStoreTest` | PASS |
| 5 | Clear runs every adapter and distinguishes execution failure from no cookies | JVM `BrowsingDataCleanerTest` | PASS |
| 6 | Manifest cache clear removes entries and active index | JVM `ManifestCacheTest` | PASS |
| 7 | Settings controls and explicit clear confirmation | Android Compose test compilation | PASS (runtime unavailable) |
| 8 | Full unit/security/lint gate | `bash scripts/pre-commit-check.sh` | PASS |
| 9 | APK dex/package and instrumentation-test compilation | Gradle assemble/compile command | PASS |

## Edge cases
- invalid manifest → regular browser mode: PASS — unchanged validator/disposition rejection paths
  remain covered by the full gate; global off adds an earlier Ignore path.
- origin change / redirect: PASS — exact-origin/generation guards remain covered; settings decode
  accepts only canonical round-tripping origins.
- offline with cached manifest: PASS — cache behavior is unchanged except explicit browser-owned
  clearing, covered by `ManifestCacheTest`.
- oversized or malformed payload: PASS — full conformance suite passes, including the restored
  intentionally malformed JSON negative fixture.
- accessibility (TalkBack, font scale): PARTIAL — controls have browser-authored visible labels and
  standard Material semantics, and Compose tests compile. Runtime TalkBack/font-scale evaluation is
  deferred to `A11Y-001`; no connected Android device is available in this environment.
- no cookies present: PASS — callback `false` is treated as a completed adapter call, not failure.
- one deletion adapter throws: PASS — remaining adapters still execute and UI receives incomplete.
- malformed stored preference: PASS — omitted from display without crash.

## Result
Status: QA_PASSED
Notes: No device is connected (`adb devices` returned an empty device list), so runtime
instrumentation and a screenshot were unavailable. Instrumentation sources compile successfully;
this environment limitation does not block the JVM-backed privacy policy or APK validation.
