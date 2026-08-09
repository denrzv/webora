# BROWSE-004 research

Status: RESEARCH_READY

## Existing seams

`BrowserMode.Regular` already carries a browser-parsed `SiteOrigin`; `BrowserState` records the last WebView-observed URL and navigation availability. `BrowserScreen` owns the Compose chrome, while `HardenedWebViewClient` observes main-frame lifecycle and currently has no error callback. `BrowserWebViewController` already owns reload/history operations.

The public-suffix implementation in `:siteskin-core` exposes `SiteOrigin.registrableDomain`, specifically intended for ADR-006 display. Material 3 supplies menus, icons via text affordances, surfaces, and accessibility semantics without another dependency.

## Origins and browser-owned boundary

The authoritative identity is the exact origin parsed from a WebView main-frame callback. The visible registrable domain is only a display property; it never relaxes full-origin equality. Address-field edits, document title/favicon, DOM content, manifests, subresources, and error descriptions cannot set the displayed security identity.

Regular-mode chrome, overflow labels/actions, TLS wording, and error copy remain browser-controlled. Remote content influences only the observed main-frame URL and framework error category. Error pages must not echo attacker-controlled framework descriptions, HTML, or arbitrary full URLs.

## Relevant code

| Path | Why it matters |
|---|---|
| `browser/BrowserState.kt` | Pure navigation/error reducer and committed origin. |
| `browser/BrowserScreen.kt` | Regular chrome, overflow, and error composition. |
| `web/HardenedWebViewClient.kt` | Main-frame lifecycle/error boundary. |
| `web/BrowserWebViewController.kt` | Browser-owned retry and navigation commands. |
| `web/HardenedWebView.kt` | Transports observations from WebView to state. |
| `strings.xml` | Browser-owned visible and accessibility copy. |

## Prior decisions

ADR-006 makes the domain, TLS state, overflow, security surface, and settings entry browser-owned and reachable. ADR-008's sealed mode means this work extends Regular mode without adding contradictory flags. ADR-010 requires invalid/failing SiteSkin behavior to preserve ordinary browsing; this ticket does not implement discovery. BROWSE-005 owns non-HTTP schemes and transfer flows.

## Risks and obligations

- Address edits could spoof identity if chrome uses the text field: derive presentation only from `BrowserMode.Regular.origin`.
- Subresource failures are frequent and attacker-triggerable: only main-frame requests may activate the error page.
- Stale errors could cover a successful page: clear error at the next main-frame start/commit.
- Framework descriptions may contain remote text: map integer error categories to a closed browser-owned reason set.
- A retry target could become an arbitrary scheme: retain it only when `AddressResolver` resolves it as renderer-owned HTTP(S).

## Open questions

None. Detailed certificate state and the security sheet can extend the stable indicator in a later hardening ticket.
