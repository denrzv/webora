# SiteSkin Manifest — Specification v1.0

Status: DRAFT
Schema version: `1.0`
Normative text lands under ticket `SPEC-001`.

> **This document is the API.** The browser is one implementation of it. Written for site owners,
> not for Webora's maintainers — if a section only makes sense to someone who has read the Kotlin,
> it is wrong.

## 1. What this is

A website publishes a JSON document describing the native navigation and branding it would like a
supporting browser to present. The browser decides how — and whether — to honour it.

```
https://bloomflowers.example/.well-known/siteskin.json
```

The website keeps rendering its own content. The browser adds a native shell around it.

## 2. Discovery

`GET {origin}/.well-known/siteskin.json`

- **HTTPS only.** A manifest served over HTTP is ignored.
- Redirects are followed **same-origin only**, at most 2 hops.
- Response must be `application/json`, at most **128 KB**. Size is enforced before parsing.
- The request runs concurrently with page load and never delays it.

## 3. Origin binding

```
origin = scheme + host + port
```

A manifest applies **only** to the exact origin that served it. `https://shop.example` and
`https://admin.shop.example` are unrelated; a manifest for one is never applied to the other.
Cached configuration is never reused across origins.

## 4. Versioning

```json
{ "schemaVersion": "1.0" }
```

| Manifest | Browser supporting 1.x |
|---|---|
| `1.0` | accepted |
| `1.1` | accepted; unknown fields ignored |
| `2.0` | **rejected** — regular browser mode |

Minor versions are additive by definition. An unknown major version is never reinterpreted.

## 5. Structure

```json
{
  "schemaVersion": "1.0",
  "site": {
    "id": "bloom-flowers",
    "name": "Bloom Flowers",
    "shortName": "Bloom",
    "homeUrl": "/"
  },
  "branding": {
    "primaryColor": "#D94F8A",
    "secondaryColor": "#FADADD",
    "backgroundColor": "#FFF7FA",
    "textColor": "#2B1B24",
    "logoUrl": "/assets/siteskin/logo.png"
  },
  "toolbar": {
    "title": "Bloom Flowers",
    "subtitle": "Fresh flowers delivered today"
  },
  "bottomNavigation": [
    { "id": "home",    "label": "Home",    "icon": "home",
      "action": { "type": "internal_url", "url": "/" } },
    { "id": "catalog", "label": "Catalog", "icon": "grid_view",
      "action": { "type": "internal_url", "url": "/catalog" },
      "match": ["/catalog", "/catalog/**"] },
    { "id": "cart",    "label": "Cart",    "icon": "shopping_cart",
      "action": { "type": "internal_url", "url": "/cart" } },
    { "id": "profile", "label": "Profile", "icon": "person",
      "action": { "type": "internal_url", "url": "/account" } }
  ],
  "quickActions": [
    { "id": "call-shop", "label": "Call", "icon": "call",
      "action": { "type": "phone", "value": "+10000000000" } }
  ]
}
```

### There is no `showDomain`

The concept document proposed a site-controlled `toolbar.showDomain`. It is **not** part of this
specification and never will be. The registrable domain and the TLS indicator are always visible in
integrated mode, in browser-owned typography. See `docs/adr/ADR-006-browser-owned-security-chrome.md`
for the reasoning — briefly: a site that controls the title, the logo, the colours *and* the domain
display controls every identity signal on screen, which makes the format a phishing tool.

## 6. Action types

| Type | Effect | Constraint |
|---|---|---|
| `internal_url` | Navigate the WebView | Must resolve within the manifest's origin |
| `external_url` | Leave the origin | HTTPS; browser confirms first |
| `phone` | Open the dialer | `ACTION_DIAL`; never places the call |
| `email` | Open the composer | `mailto:` |
| `map` | Open a location | `geo:` |
| `share` | Android Sharesheet | current page URL |
| `home` | Navigate to `site.homeUrl` | same origin |
| `refresh` | Reload | — |
| `open_menu` | Open the browser-owned SiteSkin menu | — |

Any other value: **that item is dropped**, the rest of the manifest still applies. Schemes outside
`https`, `mailto`, `tel`, `geo` are rejected.

## 7. Limits

| Limit | Value | On exceeding |
|---|---|---|
| Manifest size | 128 KB | reject (before parse) |
| Navigation items | 5 | truncate + warn |
| Menu items | 20 | truncate + warn |
| Quick actions | 5 | truncate + warn |
| Title | 64 chars | truncate + warn |
| Subtitle | 128 chars | truncate + warn |
| Label | 32 chars | truncate + warn |

Truncation rather than rejection: a slightly over-eager site should still get a working integration.

## 8. Branding safety

Colours are validated and **contrast-corrected** before use. A manifest cannot render
browser-owned text unreadable by choosing a hostile background. Invalid colours fall back to
browser defaults; an unreachable logo falls back to a monogram.

Logo: same-origin, PNG or WebP, bounded bytes and dimensions, decoded off the main thread.
**SVG is not supported in v1** — it is a scripting-capable format and the parsing surface is not
worth the fidelity.

## 9. Diagnostics

Every rejection has a stable code, so `siteskin-lint` and the browser report identically.

| Code | Meaning |
|---|---|
| `SS-E-SIZE-EXCEEDED` | manifest over 128 KB |
| `SS-E-PARSE` | not valid JSON |
| `SS-E-VERSION-UNSUPPORTED` | unknown major version |
| `SS-E-ORIGIN-MISMATCH` | URL resolves outside the origin |
| `SS-E-SCHEME-DENIED` | URI scheme not allow-listed |
| `SS-E-ACTION-UNKNOWN` | unrecognised action type (item dropped) |
| `SS-E-ASSET-CROSS-ORIGIN` | asset URL outside the origin |
| `SS-E-DUPLICATE-ID` | duplicate navigation or action id |
| `SS-W-LIMIT-TRUNCATED` | collection or string truncated |
| `SS-W-CONTRAST-CORRECTED` | supplied colour adjusted for legibility |
| `SS-W-FIELD-UNKNOWN` | field not in this schema version, ignored |

The full list and one fixture per code are `SPEC-001` deliverables.

## 10. Conformance corpus

`spec/fixtures/valid/**` — manifests that must be accepted.
`spec/fixtures/invalid/**` — manifests that must be rejected, each paired with its expected code.

**The corpus is the contract.** Implementation is written to satisfy it. A new validation rule means
a new fixture first, and a rule with no fixture does not exist.

## 11. Checklist for site owners

1. Serve `/.well-known/siteskin.json` over HTTPS as `application/json`.
2. Keep every URL within your own origin.
3. Set `Cache-Control`; the browser caps TTL at 24 hours regardless.
4. Run `siteskin-lint https://your-site.example`. Exit 0 means the browser will activate.

A worked example is `denrzv/bloom-flowers`.
