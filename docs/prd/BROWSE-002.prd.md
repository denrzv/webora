# BROWSE-002 — Browser state machine, URL bar, navigation

Status: PRD_READY

## Problem

The hardened WebView host renders one fixed URL but Webora does not yet model browser state or offer user navigation. The browser needs an explicit, browser-owned state machine and ordinary controls before SiteSkin can safely transition chrome.

## Scope

- Model Home, Regular, and Integrated as a sealed `BrowserMode` hierarchy per ADR-008.
- Add browser state for the displayed page, editable address text, loading status, and back/forward availability.
- Treat address input as an HTTPS URL when it has a host-like form and otherwise as a search query.
- Add a URL/search bar and back, forward, and reload controls around the hardened WebView.
- Synchronize controls from WebView callbacks without letting page content select browser mode.
- Integrate system and predictive back with WebView history before leaving the activity.

## Out of scope

Home content, SiteSkin discovery/activation, manifest chrome, external intents, downloads/uploads, error pages, persistence, tabs, and autocomplete.

## Acceptance criteria

1. Browser mode is a sealed hierarchy in which Integrated always carries a trusted origin and configuration and Regular may carry an observed origin.
2. User input with an explicit HTTP(S) scheme or host-like form navigates safely; other input uses a browser-owned HTTPS search endpoint, and unsupported schemes do not enter the renderer.
3. The browser displays an editable URL/search bar and back, forward, and reload controls with availability derived from the WebView.
4. Page callbacks update the displayed URL, loading state, history controls, and Regular-mode origin while never creating Integrated mode.
5. System/predictive back consumes WebView history when available and otherwise delegates to normal activity back behavior.
6. JVM tests cover state transitions and URL/search resolution, including malformed and denied-scheme negative cases.
7. Android instrumentation sources for browser navigation compile; runtime execution is reported as an environment limitation when no device is connected.
8. `bash scripts/pre-commit-check.sh` passes.
