# SKIN-004: Mode transitions and origin-change deactivation
Status: PRD_READY

## Context / Problem
SiteSkin discovery and all integrated chrome surfaces now exist, but accepted manifests still leave
the browser in regular mode. The app needs one origin-bound activation seam that can apply trusted
configuration only after browser-owned consent, swap configuration when a different origin is
accepted, and immediately remove website-controlled chrome whenever the committed main-frame origin
no longer matches it. Without that seam, the runtime cannot demonstrate the security boundary that
all prior M3 tickets prepared.

## Goals
- Connect accepted discovery results to consent-aware `BrowserMode.Integrated` transitions.
- Keep activation bound to the exact canonical scheme, host, and port observed by the browser.
- Deactivate integrated mode synchronously when main-frame navigation leaves the active origin,
  including redirects, before a replacement manifest can arrive.
- Swap directly to a different trusted skin only after that destination origin independently passes
  discovery and consent.
- Compose the existing integrated top bar, navigation, quick actions, and menu and route their typed
  selections through the closed core resolver and browser-owned effect handlers.
- Preserve ordinary WebView rendering and regular chrome through missing, invalid, denied, stale, or
  superseded discovery outcomes.

## Non-goals
- Reimplementing manifest transport, validation, caching, asset decoding, theme projection, or
  integrated presentation components.
- A global SiteSkin toggle, settings UI, or user-facing management of saved site decisions
  (`PRIV-001`).
- Arbitrary URI or Android intent dispatch, new permissions, or a JavaScript bridge.
- Demo hosting or network-dependent tests against future public demo origins (`DEMO-001/002`).

## User stories
- As a user, I see an integrated site's trusted native chrome only after allowing that exact site.
- As a user, when I follow a link or redirect to another origin, the previous site's branding
  disappears immediately while the page continues loading.
- As a user, an independently accepted integrated destination can replace the previous skin without
  inheriting its consent or configuration.
- As a user, denying or dismissing consent leaves the destination usable as a regular web page.
- As a user, integrated navigation and actions work through browser-owned, allow-listed behaviour.

## Acceptance criteria
1. A trusted discovery result can activate `BrowserMode.Integrated` only when its complete canonical
   origin equals the browser-observed committed origin and browser-owned consent permits activation.
2. The first eligible result exposes Allow, Not now, and Never decisions; Allow permits activation,
   while either refusal leaves regular browsing intact and consent is keyed by full origin.
3. Every observed main-frame origin change deactivates the current skin before discovery for the new
   origin completes; a stale or cancelled result cannot reactivate it.
4. Navigation between two independently allowed integrated origins swaps to the destination's
   trusted configuration, while navigation to an origin with no accepted manifest remains regular.
5. Same-origin page changes retain the active skin, and subdomains, ports, and HTTP/HTTPS changes are
   never treated as same-origin.
6. Existing integrated chrome is composed only from trusted configuration, decoded brand asset,
   projected theme, and browser-derived security presentation; domain and TLS identity remain
   non-suppressible.
7. Typed navigation and action selections pass through `ActionResolver` and closed browser-owned
   handlers; unsupported or inconsistent effects fail closed without disrupting the WebView.
8. Deterministic tests cover activation, same-origin retention, cross-origin skin drop, integrated
   origin swap, each consent outcome, and stale-result rejection, including negative controls for
   origin and generation binding; relevant Android test sources compile.
9. `bash scripts/pre-commit-check.sh` passes.

## NFR
- Security/privacy: manifest acceptance is not consent; both consent and active state are scoped to
  the exact `SiteOrigin`, and remote configuration never controls security affordances or generic
  native effects.
- Reliability/fallback: any mismatch, refusal, cancellation, resolver failure, or discovery failure
  yields regular browsing rather than a broken or incorrectly branded page.
- Performance: page rendering never waits for discovery, consent, asset work, or chrome transition;
  obsolete asynchronous work is cancelled or ignored by a generation check.
- Accessibility: consent and integrated controls use browser-authored labels and roles, remain
  operable at large font scales, and never communicate active/security state through colour alone.

## Risks
- A delayed response for the previous page could apply attacker-controlled branding after an origin
  change; activation must compare both origin and navigation generation at publication time.
- Treating host suffixes or registrable domains as authority would leak consent and skins across
  subdomains; all keys and comparisons must use `SiteOrigin` equality.
- Waiting for discovery before dropping old chrome creates a phishing window during redirects;
  deactivation must happen from the browser-observed navigation callback.
- Wiring presentational callbacks directly to raw URLs or intents would bypass the closed action
  model; resolution and Android effects need separate typed boundaries.

## Open questions
Research must reconcile ADR-011's required first-use persistence with its note assigning the broader
storage/settings layer to `PRIV-001`, and identify the smallest durable consent contract SKIN-004
needs without pulling forward unrelated privacy controls.
