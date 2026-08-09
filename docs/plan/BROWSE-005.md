# BROWSE-005 implementation plan

Status: PLAN_APPROVED

## Flow and trust boundary

A pure app-layer policy converts page-controlled strings into closed requests: renderer navigation, a pending `ExternalNavigation` (`Email`, `Telephone`, or `Map`), an eligible `DownloadRequest`, or a bounded upload MIME filter. These values carry no Android component, package, flags, arbitrary extras, or permission request.

`HardenedWebViewClient` emits supported external navigation instead of launching it. Compose renders browser-owned destination/scheme copy and Confirm/Cancel actions. Only Confirm reaches an Android adapter that constructs an allow-listed intent and checks for a handler. Downloads cross a validated HTTP(S)-only adapter into `DownloadManager`. File chooser hints cross a normalization policy into an `ACTION_OPEN_DOCUMENT` launcher, with callback lifecycle owned by the browser.

The page may request these capabilities but cannot silently exercise them or expand their shape. SiteSkin manifests are not involved. Browser mode and committed origin remain unchanged by cancellation or unavailable handlers.

## Changes

1. Add pure external-navigation and transfer policies with focused negative-control JVM tests; extend the WebView client to emit only safe supported top-level external requests.
2. Add thin Android download, external-intent, and file-chooser adapters; wire browser-owned confirmation and WebView callbacks through Activity/Compose, add strings and compile Android tests.
3. Review, QA, update normative architecture/roadmap, validate, and capture a screenshot only when a runtime target is available.

## File-by-file plan

- New `browser/ExternalNavigation.kt`: closed URI classification and safe display model.
- New `web/TransferPolicy.kt`: pure download and MIME/selected-URI validation.
- Modify `web/HardenedWebViewClient.kt`: emit supported top-level external navigation while denying all other schemes.
- New `web/AndroidCapabilityAdapters.kt`: typed intent construction, handler check, DownloadManager request, and upload callback coordinator.
- Modify `web/HardenedWebView.kt`: install download listener and WebChromeClient file chooser seam.
- Modify `browser/BrowserScreen.kt` and `MainActivity.kt`: confirmation state, browser-owned dialogs/messages, activity-result and service composition.
- Modify `strings.xml`: browser-owned confirmation and outcome copy.
- Add JVM tests and Android test compile coverage for all policies and negative cases.
- Update `CLAUDE.md`, `docs/ROADMAP.md`, and reports during closeout.

## Security and privacy checks

Tests must prove an arbitrary or malformed scheme cannot emit or launch an intent; external launch cannot occur before confirmation; downloads reject non-HTTP(S); MIME hints cannot create an arbitrary picker contract; and non-content upload results are rejected. No dangerous permission is added. URLs, filenames, selected URIs, and browsing state are not logged or persisted.

## Validation

Run focused app unit tests and lint for each code change, compile instrumentation sources and debug APK, and run `bash scripts/pre-commit-check.sh` before each task commit. Runtime instrumentation/screenshots require a connected device; without one, record the managed-cloud limitation.
