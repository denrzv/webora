# BROWSE-001: WebView host and hardening
Status: PRD_READY

## Problem

Webora needs a real system-WebView rendering host before browser navigation or SiteSkin chrome can
exist. A default WebView exposes capabilities the MVP does not need, including local-file access and
mixed-content behavior, so the host must apply a browser-owned security policy every time it creates
a renderer.

## Scope

- Add the Compose-owned WebView host used by later browser-foundation tickets.
- Enable JavaScript for ordinary modern websites.
- Disable file and content access, file-URL cross-origin access, and mixed content.
- Enable AndroidX WebKit Safe Browsing when the installed WebView supports it.
- Reject `file:` top-level navigation and expose no JavaScript interface.
- Keep all policy fixed in app code; page or manifest data cannot change it.
- Reconcile completed `CORE-002`, `CORE-003`, and `CORE-004` roadmap markers.

## Out of scope

URL-bar state, history controls, downloads/uploads, external-intent dispatch, SiteSkin discovery,
manifest-driven chrome, and a JavaScript bridge.

## Acceptance criteria

1. A reusable Compose host creates a system `WebView` and renders a supplied web URL.
2. Every created WebView enables JavaScript and disables file access, content access, file-URL access,
   universal file-URL access, and mixed content.
3. Safe Browsing is enabled through AndroidX WebKit when the installed provider supports it.
4. Top-level `file:` navigation is rejected, while HTTP and HTTPS navigation remain WebView-owned.
5. Production code contains no `addJavascriptInterface` call and the host exposes no bridge object.
6. Pure JVM tests assert every policy value and navigation decision; an Android test asserts the
   policy on real `WebSettings` without Robolectric.
7. `docs/ROADMAP.md` records all completed M1 core tickets and BROWSE-001 when validation completes.
8. `bash scripts/pre-commit-check.sh` passes.
