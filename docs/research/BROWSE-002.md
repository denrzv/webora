# BROWSE-002 research

Status: RESEARCH_READY

## Existing seams

BROWSE-001 provides `HardenedWebView`, which owns creation and immutable renderer policy. Its current API accepts a fixed URL and installs its own `HardenedWebViewClient`; it exposes no history or page callbacks. `MainActivity` directly renders that host at `https://example.com`. The app already depends on Compose Material 3 and `:siteskin-core`.

`SiteOrigin` and `SiteSkinConfiguration` are trusted core types. ADR-008 specifies the closed Home / Regular / Integrated hierarchy. BROWSE-003 owns real Home content, so this ticket may define Home state without using it as the launch destination.

## Origins and browser-owned boundary

The typed address and callback URL are untrusted text. Only absolute HTTP(S) destinations or host-like input promoted to HTTPS may reach the WebView. Search terms are encoded into a browser-owned HTTPS endpoint. Page callbacks may update observed Regular origin and history metadata, but cannot request Integrated mode. A future validated SiteSkin pipeline is the only producer of Integrated state.

The website controls document history and the current page URL within the renderer. The browser controls URL interpretation, address chrome, mode transitions, command dispatch, back precedence, and every renderer security setting. Manifest data influences nothing in this ticket.

## Proposed files and tests

- `browser/BrowserMode.kt`: sealed state required by ADR-008.
- `browser/BrowserState.kt`: immutable UI state, commands, pure reducer/origin observation.
- `browser/AddressResolver.kt`: pure allow-listed URL/search interpretation.
- `browser/BrowserScreen.kt`: Compose chrome and WebView integration.
- `web/HardenedWebView.kt` and client: expose browser-owned controller/callback seams without weakening hardening.
- `MainActivity.kt`: activity composition and predictive/system back delegation.
- JVM tests for resolver and state reducer; instrumentation compilation through the repository gate.

## Risks

WebView callbacks may arrive after a new command, so observed state must remain descriptive rather than treated as authority for security. Redirects can change origin and must update Regular mode. Back handling must avoid stale `canGoBack` state by consulting the controller at dispatch. URL parsing must reject credentials, non-HTTP(S) schemes, malformed hosts, and control characters.
