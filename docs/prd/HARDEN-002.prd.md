# HARDEN-002: Brand-impersonation controls
Status: PRD_READY

## Context / Problem
SiteSkin deliberately lets a website supply brand names, colours, and a logo for native browser
chrome. Those inputs can imitate another brand and therefore carry more phishing risk than ordinary
page content unless the browser keeps an unmistakable, non-suppressible identity signal and asks
the user before the first restyle. ADR-006 and ADR-011 define those controls, and M4 requires an
explicit hardening pass that proves the complete contract rather than relying on incidental UI.
## Goals
- Prove that every integrated top bar always presents browser-observed transport state and
  registrable domain independently of manifest branding.
- Keep logo, title, palette, consent copy, security details, overflow, and settings within their
  documented website-controlled or browser-owned boundaries.
- Prove first-use consent is origin-scoped, precedes branding, and exposes Allow, Not now, and Never.
- Add focused negative controls so removing or obscuring an impersonation safeguard fails tests.
## Non-goals
- Domain verification, signed manifests, or judging whether a site's claimed brand is legitimate.
- Redesigning regular browser chrome, privacy settings, or the global/per-site toggle owned by
  `PRIV-001`.
- Expanding manifest fields, action capabilities, asset formats, or cross-origin trust.
## User stories
- As a user, I can always tell which registrable domain and TLS state own skinned browser chrome.
- As a first-time visitor, I approve a site's customization before its branding changes Webora.
- As a security reviewer, I can deterministically verify that a hostile manifest cannot suppress,
  replace, recolour into invisibility, or semantically impersonate browser-owned identity chrome.
## Acceptance criteria
1. SiteSkin top chrome always exposes the browser-observed registrable domain and TLS state in
   browser-authored UI; manifest title, logo, colours, and fields cannot replace or suppress them.
2. Security identity is derived from the active canonical origin, not manifest text, asset URLs,
   navigation targets, or stale discovery results.
3. Brand imagery remains confined to its bounded decorative slot and cannot replace the security
   identity's accessibility semantics or interaction target.
4. Corrected theme colours preserve readable browser-owned identity text for adversarial palettes.
5. A first valid manifest for an origin remains regular chrome until the browser-owned consent UI
   receives Allow; Not now stays regular without persistence and Never persists an origin-exact
   refusal.
6. Consent and stored decisions are isolated by canonical scheme, host, and port; sibling
   subdomains and distinct ports never inherit trust.
7. The consent surface explains that navigation/appearance may change while address and security
   identity remain browser-controlled, and exposes Allow, Not now, and Never for this site.
8. Focused unit and compiled instrumentation tests include negative controls for non-suppressible
   identity, hostile brand text/palette, consent-before-branding, and exact-origin decisions.
9. Invalid, stale, refused, or superseded candidates degrade to regular browser mode without a
   broken page or website-controlled native capability.
10. `bash scripts/pre-commit-check.sh` passes.
## NFR
- Security/privacy: All identity and consent values originate from browser-observed state; no new
  telemetry, permission, native bridge, or trust inheritance is introduced.
- Reliability/fallback: Missing assets and rejected or refused manifests retain usable regular
  browsing and a browser-owned monogram fallback where integration is already active.
- Performance: The controls add no network round trips or page-render blocking.
- Accessibility: Domain/TLS identity has browser-authored semantics, survives font scaling, and is
  not conveyed by colour alone; brand imagery remains decorative.
## Risks
- Existing implementation can make the ticket appear complete while tests assert only visible
  strings and miss provenance, semantics, contrast, stale-origin, or pre-consent behavior.
- A UI-level fix could duplicate origin/security policy outside the existing pure app/core seams.
- Full runtime screenshots require a connected Android device; instrumentation must still compile
  in managed cloud and runtime absence is an environment limitation, not weakened acceptance.
## Open questions
- Research must map which ADR-006/ADR-011 requirements already have explicit negative coverage and
  whether the narrowest completion is test hardening or a production change.
