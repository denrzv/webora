# BROWSE-003 — Home screen and onboarding

Status: PRD_READY

## Problem

Webora currently launches directly into a remote page. It needs a browser-owned starting experience that explains the product before first use and gives returning users a useful home surface without requiring a network response.

## Scope

- Show a short, skippable onboarding carousel on first launch.
- Persist onboarding completion locally and never require onboarding again after completion or skip.
- Launch returning users into `BrowserMode.Home`.
- Render a home search/address entry plus browser-owned sections for recent sites, favourites, and suggested SiteSkin integrations.
- Navigate safe home destinations through the existing address resolver and browser controller.
- Provide empty states for recents and favourites until persistence is added by a future ticket.

## Out of scope

Browsing-history and favourite persistence, editing suggestions remotely, SiteSkin discovery or activation, remote artwork, telemetry, tabs, regular-mode security chrome, and external-intent dispatch.

## Acceptance criteria

1. A first launch displays a three-page accessible onboarding flow that can be advanced or skipped and clearly describes ordinary browsing, SiteSkin adaptation, and browser-owned security controls.
2. Completing or skipping onboarding persists a local completion flag; subsequent launches start in Home mode.
3. Home provides address/search entry and sections for recents, favourites, and suggested integrations, with honest empty states where no persisted data source exists.
4. Suggested integrations are an immutable browser-owned allow-list of HTTPS destinations; no website or manifest can change their label or target.
5. Selecting or submitting a home destination uses the existing safe address-resolution path and transitions to regular browsing without weakening WebView hardening.
6. Pure JVM tests cover onboarding completion state, returning-user launch behavior, home navigation, and rejection of unsafe suggested destinations.
7. Compose semantics expose useful headings, labels, and minimum-size controls, and Android test sources compile.
8. `bash scripts/pre-commit-check.sh` passes.
