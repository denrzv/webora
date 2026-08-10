# PRIV-001: Privacy controls and browsing-data deletion
Status: PRD_READY

## Context / Problem
SiteSkin consent decisions already persist per full canonical origin, but users cannot globally
disable SiteSkin, inspect or reverse individual decisions, or clear browser data from browser-owned
UI. The product also needs an auditable zero-default-telemetry posture and a Data safety mapping.
## Goals
1. Provide a persistent, browser-owned global SiteSkin switch that defaults on and prevents
   discovery prompts and activation while off.
2. Provide settings UI to review and remove persisted per-origin Allow/Never decisions.
3. Provide a clear-browsing-data action that clears WebView cookies, Web Storage, HTTP cache, form
   data, manifest cache, and persisted SiteSkin decisions without clearing onboarding/settings.
4. Document the app's zero-telemetry default and map implemented storage/transfers to Play Data
   safety declarations.
## Non-goals
- Incognito mode, history/favourites persistence, remote privacy-policy hosting, or Play Console
  submission.
- Website-controlled privacy settings or a manifest-controlled way to bypass browser preferences.
## User stories
- As a user, I can turn SiteSkin off globally and browse without discovery prompts or themed chrome.
- As a user, I can see the exact origins for which I made a SiteSkin decision and reset one.
- As a user, I can clear browsing data with an explicit confirmation while retaining app settings.
- As a release reviewer, I can verify that Webora contains no analytics/telemetry SDK and understand
  its Data safety declarations.
## Acceptance criteria
1. The global SiteSkin preference persists, defaults enabled, and when disabled prevents candidate
   prompts/activation and returns active integrated browsing to regular mode.
2. Settings lists stored SiteSkin decisions by complete canonical origin and lets the user remove
   an individual decision without affecting other origins.
3. Clear browsing data requires confirmation and clears WebView cookies, Web Storage, WebView
   cache/form data, the in-memory manifest cache, and all per-origin SiteSkin decisions.
4. Clear browsing data does not reset onboarding completion or the global SiteSkin preference.
5. Automated tests cover default/persistence, full-origin isolation, global-off fail-closed
   behavior, targeted decision removal, and the complete clear-data orchestration.
6. Repository documentation records zero default telemetry and a Data safety mapping grounded in
   implemented behavior and dependencies.
7. `bash scripts/pre-commit-check.sh` passes.
## NFR
- Security/privacy: Settings and deletion are browser-owned; no telemetry or remote preference
  sync; origins are rendered from canonical trusted values and never from page text.
- Reliability/fallback: Failed or unavailable deletion adapters report incomplete clearing rather
  than claiming success; global-off always degrades to regular browsing.
- Performance: Preference reads are local and discovery remains asynchronous; clearing may be
  asynchronous and must not block Compose rendering.
- Accessibility: Controls have visible labels, state descriptions, and confirmation semantics.
## Risks
- Android WebView exposes browsing state through several APIs with different completion models;
  orchestration must not imply completion before asynchronous cookie deletion completes.
- Disabling SiteSkin during an active integrated page must remove themed chrome immediately without
  losing the committed page URL.
## Open questions
None. Scope follows `docs/ROADMAP.md`, `docs/DEVELOPMENT_PLAN.md`, and ADR-011.
