# Architecture Decision Records

Decisions made **up front**, before implementation. Decisions that emerge *during* implementation go
into a named "Note" section in `CLAUDE.md` instead — that is where a future session will actually
read them.

`ADR-006` and `ADR-011` amend the concept document and have their own files because the reasoning
matters. The rest are recorded below in short form; promote one to its own file if it ever needs
re-arguing.

| ADR | Decision | Status |
|---|---|---|
| 001 | Android WebView as the rendering engine | ACCEPTED |
| 002 | `.well-known/siteskin.json` for discovery | ACCEPTED |
| 003 | Manifest is declarative data, never code | ACCEPTED |
| [004](ADR-004-origin-binding.md) | Configuration is bound to an origin | ACCEPTED |
| 005 | No general-purpose JavaScript bridge in MVP | ACCEPTED |
| [006](ADR-006-browser-owned-security-chrome.md) | **Browser-owned security chrome; domain not suppressible** | ACCEPTED |
| 007 | Strict allow-listed action types | ACCEPTED |
| 008 | Explicit sealed browser state, not flag sets | ACCEPTED |
| 009 | Manifest discovery never blocks page rendering | ACCEPTED |
| 010 | Invalid integration always falls back to regular mode | ACCEPTED |
| [011](ADR-011-first-use-consent.md) | **First-use consent before activation** | ACCEPTED |
| 012 | Signed manifests deferred past MVP | ACCEPTED |
| 013 | Browser-owned design tokens, compiled and never derived | PROPOSED — `UX-001` |

---

## ADR-001 — Android WebView as the rendering engine

Bundling a rendering engine (GeckoView, Chromium) would add ~50 MB to the APK and make us
responsible for shipping security patches on a browser-engine cadence. System WebView is updated
independently through Play, so users get engine security fixes without an app update. The cost is
inheriting WebView's API limitations, which is acceptable because Webora's value is in the native
shell, not the renderer. Revisit only if a shell feature turns out to be impossible on WebView.

## ADR-002 — `.well-known/siteskin.json` for discovery

Predictable, origin-bound, cacheable, and requires no HTML parsing — so discovery can run
concurrently with page load rather than after it. HTML `<link rel="siteskin">` is a future addition,
not a v1 alternative: it would force us to wait for the document, which conflicts with `ADR-009`.

This originally pointed at `SPEC-002` for the mechanism. `SPEC-002` deliberately did not implement
it — that belongs to `NET-001` — and instead settled the question the pointer was really about:
whether such an addition needs a major bump. It does not. `SPEC.md` §4.2 uses it as the worked
example of an additive change, since a reader that does not know the `<link>` element still finds
`/.well-known/siteskin.json` and no manifest becomes unreachable.

## ADR-003 — Manifest is declarative data, never code

The manifest may request only capabilities the browser already implements. No JavaScript to run in
Android context, no Kotlin/Java, no arbitrary intents, no package execution, no shell, no dynamic
loading, no unrestricted URI schemes or file access. Everything the manifest can express is a value
the browser interprets, never an instruction the browser executes.

## ADR-005 — No general-purpose JavaScript bridge in MVP

`addJavascriptInterface` exposes reflective access to the host object and is the classic Android
WebView privilege-escalation vector. The MVP's needs — navigation and branding — are fully served by
a declarative manifest. A narrow, versioned, origin-checked bridge for specific capabilities
(cart badges, active-item sync) may come later; "later" means with its own threat model, not as an
incremental feature.

## ADR-007 — Strict allow-listed action types

`internal_url`, `external_url`, `phone`, `email`, `map`, `share`, `home`, `refresh`, `open_menu`.
Schemes: `https`, `mailto`, `tel`, `geo`. An unknown action type drops **that item** and keeps the
rest of the manifest — a site experimenting with a v1.1 action should not lose its whole
integration. An unknown *major* schema version rejects the whole manifest (`SPEC-002`).

Allow-list, never deny-list: a deny-list is a bet that we enumerated every dangerous scheme, and
`intent:` alone shows how that bet loses.

## ADR-008 — Explicit sealed browser state

```kotlin
sealed interface BrowserMode {
    data object Home : BrowserMode
    data class Regular(val origin: SiteOrigin?) : BrowserMode
    data class Integrated(val origin: SiteOrigin, val config: SiteSkinConfiguration) : BrowserMode
}
```

Not `isSiteSkinEnabled` + `hasTheme` + `showBottomBar` + `isIntegrated`. Independent booleans admit
contradictory combinations, and the contradictory ones are precisely the security-relevant states —
"skinned but origin unknown" must be unrepresentable, not merely untested.

## ADR-009 — Discovery never blocks rendering

Page load and manifest fetch run concurrently. The browser shows regular chrome, then transitions
when validation succeeds. A site that publishes a slow manifest endpoint degrades its own chrome
transition, never its page load. No network call may gate `onPageStarted`.

## ADR-010 — Invalid integration always falls back

Every failure path — malformed JSON, unsupported version, invalid colours, unsafe URL, asset failure,
timeout, offline — ends in regular browser mode with the page still rendering. SiteSkin is an
enhancement layer; its failure is never the user's problem. Diagnostics go to the developer
inspector (`DEVX-001`), not to a user-facing error.

## ADR-013 — Browser-owned design tokens *(proposed, `UX-001`)*

Reserved, not yet decided. `M6` gives the browser the token layer it never had, and this ADR records
the choice of direction plus the rule that survives whichever direction wins: Webora's palette,
typography and shape scale are **compiled into the app**, with no path from a manifest value into
them. `SiteSkinColorScheme` remains the entire website-influenceable colour surface.

Written under `UX-001` from the directions in `docs/design/directions/`. Promote to its own file if
the reasoning needs re-arguing — the security half of it (a direction can fail `ADR-006` by placing
the domain inside the editable address field) probably will.

## ADR-012 — Signed manifests deferred

Manifest signing needs a key distribution story, a revocation story, and an authority to anchor
trust — none of which MVP has. Origin binding over HTTPS already ties a manifest to a domain, which
is the same guarantee the rest of the web platform runs on. Revisit alongside verified-domain
indicators, and only if a concrete abuse pattern demands it rather than as pre-emptive machinery.
