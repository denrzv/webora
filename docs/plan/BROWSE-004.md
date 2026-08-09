# BROWSE-004 implementation plan

Status: PLAN_APPROVED

## Flow and trust boundary

Main-frame callbacks produce a closed WebView observation model. A pure reducer commits URL/origin state, derives a `SecurityPresentation` only from the parsed committed HTTPS origin, and records a bounded browser-owned load failure. Address edits never alter identity. Main-frame start clears stale failure state; subresource errors are ignored by the state channel.

Compose renders regular chrome from that state. Retry uses the stored failing HTTP(S) destination through the controller; Home discards the renderer by returning to `BrowserMode.Home`. Overflow commands are a closed browser-owned enum/callback set. No page or manifest can style, label, suppress, or dispatch these capabilities.

## Changes

1. Add pure security-presentation and load-failure models, extend browser observations/reducer, and cover secure/insecure and error transition negative controls.
2. Extend the hardened WebView client observation seam for main-frame errors; build regular chrome, overflow, and error recovery UI with localized accessibility strings and compile Android tests.
3. Review, QA, update architecture/roadmap, validate, and capture a screenshot when a runtime target is available.

## File-by-file plan

- New `browser/SecurityPresentation.kt`: closed browser-owned TLS/domain presentation.
- Modify `browser/BrowserState.kt`: failure model and pure lifecycle reducer.
- Modify `web/HardenedWebViewClient.kt`, `HardenedWebView.kt`, and observation types: main-frame error reporting only.
- Refactor `browser/BrowserScreen.kt`: regular top chrome, overflow, and error page.
- Modify `strings.xml`: browser-owned visible/accessibility copy.
- Add JVM and instrumentation tests for security, errors, and semantics.
- Update `CLAUDE.md`, `docs/ROADMAP.md`, and reports during closeout.

## Security and privacy checks

Tests must prove typed text cannot set displayed identity, HTTP never displays TLS-secure state, malformed origins fail closed, and subresource errors do not enter the reducer. Error copy contains no framework description or full path/query. No telemetry, history, certificate data, or browsing data is persisted.

## Validation

Run focused app unit tests and lint for each code change, compile instrumentation sources, and run `bash scripts/pre-commit-check.sh` before each task commit. Runtime instrumentation/screenshots require a connected device; without one, record the managed-cloud limitation.
