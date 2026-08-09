# BROWSE-004 — Regular-mode chrome, security indicator, and error pages

Status: PRD_READY

## Problem

Regular browsing currently exposes a raw address field and navigation buttons but does not give users a durable, browser-owned statement of who serves the page or a useful recovery surface when a main-frame load fails.

## Scope

- Render polished regular-mode top chrome with address entry and back, forward, reload, home, and overflow actions.
- Always show an HTTPS/TLS indicator and the browser-observed registrable domain for a valid loaded origin.
- Keep security identity derived exclusively from the committed main-frame URL, never page content.
- Provide a browser-owned overflow menu with reachable page and app actions.
- Replace failed main-frame loads with an in-app error surface that preserves the failing destination and supports retry or Home.

## Out of scope

SiteSkin theming, certificate inspection beyond the HTTPS transport signal, external intents, downloads/uploads, history/favourites persistence, settings implementation, subresource-error UI, and custom network interception.

## Acceptance criteria

1. Regular mode always renders browser-owned chrome with address entry and usable back, forward, reload, Home, and overflow controls.
2. A committed HTTPS page displays a lock/TLS affordance and its browser-observed registrable domain; invalid, non-HTTPS, or uncommitted input cannot claim a secure identity.
3. The overflow remains browser-owned and exposes stable actions without allowing page or manifest content to alter its labels or behavior.
4. A main-frame WebView error produces a browser-owned error page with a safe summary, the destination domain when available, Retry, and Home; subresource failures do not replace the page.
5. Successful navigation clears the prior error, and Retry reissues only the last browser-observed failing HTTP(S) URL.
6. JVM tests cover security presentation and error-state transitions, including negative controls for non-HTTPS identity and subresource failures.
7. Compose semantics expose meaningful labels and Android test sources compile.
8. `bash scripts/pre-commit-check.sh` passes.
