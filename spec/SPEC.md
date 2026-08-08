# SiteSkin Manifest — Specification v1.0

Status: SPEC_READY
Schema version: `1.0`
JSON Schema: [`siteskin-1.0.schema.json`](siteskin-1.0.schema.json)
Diagnostic registry: [`diagnostics.json`](diagnostics.json)
Conformance corpus: [`fixtures/`](fixtures/)

> **This document is the API.** The browser is one implementation of it. It is written for site
> owners, not for Webora's maintainers — if a section only makes sense to someone who has read the
> Kotlin, it is wrong.

The key words MUST, MUST NOT, REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY and
OPTIONAL in this document are to be interpreted as described in [RFC 2119][rfc2119].

[rfc2119]: https://www.rfc-editor.org/rfc/rfc2119

---

## 1. Trust model — read this before any field

A SiteSkin manifest is **untrusted remote input**. It is data describing what a site would *like*,
and it is never an instruction the browser obeys.

Three consequences run through every section below, and none of them are negotiable by a field:

1. **The browser is the security authority.** The site proposes; the browser disposes. Every value
   in this document is validated, bounded or discarded before it reaches a pixel.
2. **Parsing success is not validity.** A manifest that is well-formed JSON conforming to the JSON
   Schema has cleared the *structural* bar and nothing more. Origin binding, scheme allow-listing,
   asset provenance and limit enforcement all happen afterwards, and any of them MAY still discard
   part or all of the document.
3. **The manifest is data, never code.** It cannot cause script execution, dynamic loading,
   arbitrary intent dispatch, or the acquisition of any operating-system permission. A site does not
   gain a capability by publishing a manifest; it selects from capabilities the browser already
   has.

A conforming browser MUST NOT grant a manifest any influence over its security-relevant chrome. In
particular the registrable domain and the transport-security indicator MUST remain visible whenever
a manifest is applied — see [§6](#6-there-is-no-showdomain).

If any of this fails, browsing continues. **Every failure path in this specification ends in
regular browser mode with the page still rendering.** A manifest is an enhancement; its failure is
never the user's problem.

---

## 2. Discovery

```
GET {origin}/.well-known/siteskin.json
```

- The request MUST use HTTPS. A manifest served over cleartext HTTP MUST be ignored.
- The response SHOULD carry `Content-Type: application/json`. A browser MAY accept a manifest
  without the header but MUST NOT accept one whose declared type contradicts it.
- Redirects MUST be followed **same-origin only**, to a maximum of **2 hops**. A cross-origin
  redirect MUST abort discovery.
- The body MUST NOT exceed **131,072 bytes (128 KB)**. The limit MUST be enforced *before* parsing,
  so an oversized payload is never fully read into memory → `SS-E-SIZE-EXCEEDED`.
- Discovery MUST run concurrently with page load and MUST NOT delay it. A browser MUST NOT gate
  rendering on this request.
- A browser MUST cap cached manifest lifetime at `min(Cache-Control max-age, 24 hours)`.

A site that publishes no manifest, or whose manifest fails at any stage, is browsed normally. That
is the expected case for almost every site on the web, and it is not an error condition.

---

## 3. Origin binding

```
origin = scheme + host + port
```

A manifest applies **only** to the exact origin that served it.

- `https://shop.example` and `https://admin.shop.example` are unrelated origins. **Subdomains are
  not trusted.** A subdomain is frequently delegated to a third party, and inheriting the parent's
  branding would let that party impersonate it.
- `https://shop.example` and `http://shop.example` are unrelated origins.
- `https://shop.example` and `https://shop.example:8443` are unrelated origins.
- Origins MUST be compared on their canonical form: lowercased host, IDN in punycode, default port
  elided. Canonicalization happens **before** comparison, never after.

A browser MUST NOT apply a cached configuration to any origin other than the one it was fetched
from.

Every URL-bearing field in this format is either origin-relative or origin-checked. There is no
field that opts out:

| Field | Rule | On violation |
|---|---|---|
| `site.homeUrl` | MUST resolve inside the serving origin | `SS-E-ORIGIN-MISMATCH`, falls back to `/` |
| `branding.logoUrl` | MUST be same-origin | `SS-E-ASSET-CROSS-ORIGIN`, falls back to a monogram |
| `action.url` for `internal_url` | MUST resolve inside the serving origin | `SS-E-ORIGIN-MISMATCH`, item dropped |
| `action.url` for `external_url` | MUST be HTTPS; MAY be any origin | `SS-E-SCHEME-DENIED`, item dropped |
| `match[]` | MUST be a path pattern with no scheme or authority | `SS-E-SCHEMA-INVALID`, manifest rejected |

A URL "resolves inside the origin" only if, after resolution against the origin root, its canonical
origin is identical to the serving origin. Implementations MUST reject — never silently normalize
away — each of:

- protocol-relative references (`//evil.example/x`),
- path traversal escaping the origin (`/../../evil`),
- an authority carrying userinfo (`https://shop.example@evil.example/`),
- a differing port,
- any subdomain, parent domain or sibling domain.

---

## 4. Versioning

```json
{ "schemaVersion": "1.0" }
```

`schemaVersion` is REQUIRED and MUST be a string of the form `MAJOR.MINOR`, both non-negative
integers.

| Manifest declares | Browser supporting 1.x |
|---|---|
| `1.0` | accepted |
| `1.1` | accepted; unknown fields ignored with `SS-W-FIELD-UNKNOWN` |
| `2.0` | **rejected** → `SS-E-VERSION-UNSUPPORTED`, regular browser mode |
| absent or malformed | **rejected** → `SS-E-SCHEMA-INVALID` |

Minor versions are additive by definition; a browser MUST ignore fields it does not recognise rather
than rejecting the document. An unknown **major** version MUST reject the whole manifest — an
implementation that reinterprets a format it does not know has replaced a security boundary with a
guess.

Note that `siteskin-1.0.schema.json` validates the *format* of `schemaVersion` and deliberately does
not pin the major. Version support is a policy decision evaluated separately, which is why an
unsupported major produces `SS-E-VERSION-UNSUPPORTED` rather than a schema failure.

---

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
      "match": ["/catalog", "/catalog/**"] }
  ],
  "quickActions": [
    { "id": "call-shop", "label": "Call", "icon": "call",
      "action": { "type": "phone", "value": "+10000000000" } }
  ]
}
```

REQUIRED: `schemaVersion`, `site.id`, `site.name`. Everything else is OPTIONAL and has a browser
default. A missing REQUIRED field is a rejection, not a droppable item — see
[§10](#10-dispositions).

`id` values MUST be unique within `bottomNavigation` and within `quickActions`. Colours MUST be
`#RRGGBB` or `#RGB`; an unparseable colour MUST fall back to the browser default rather than
rejecting the manifest.

`menu` is an OPTIONAL array with the same item shape as `bottomNavigation`. It is surfaced through
the `open_menu` action rather than rendered directly, and carries its own limit ([§8](#8-limits)).

`icon` names are drawn from a browser-provided set and MUST match `^[a-z][a-z0-9_]{0,31}$`. The
pattern is the security requirement — it structurally prevents an icon field from carrying a URL or
any other resource reference. An icon name the browser does not recognise MUST fall back to a
generic glyph with `SS-W-ICON-UNKNOWN`; it MUST NOT reject the manifest, for the same reason an
unknown action type does not (see [§7](#7-actions)).

## 6. There is no `showDomain`

The concept document proposed a site-controlled `toolbar.showDomain`. It is **not** part of this
specification and never will be.

The registrable domain and the transport-security indicator MUST be visible whenever a manifest is
applied, in browser-controlled typography and contrast. No field in this format can suppress,
restyle, obscure or relocate them, and a conforming implementation MUST NOT add one.

The reasoning is in [`ADR-006`](../docs/adr/ADR-006-browser-owned-security-chrome.md). Briefly: a
manifest already supplies the title, the logo and the colours. Let it hide the domain too and every
identity signal on screen belongs to the site, with nothing left to contradict it — which makes the
format a phishing kit with a schema.

A manifest containing `toolbar.showDomain` is **not** an error. The field is unknown, so it is
ignored and reported as `SS-W-FIELD-UNKNOWN`, exactly like any other unrecognised field. Ignoring a
field and rejecting the document are very different outcomes, and this specification requires the
former.

---

## 7. Actions

| Type | Effect | Constraint |
|---|---|---|
| `internal_url` | Navigate the WebView | MUST resolve within the serving origin |
| `external_url` | Leave the origin | MUST be HTTPS; the browser MUST confirm before leaving |
| `phone` | Open the dialer | `ACTION_DIAL` or equivalent; MUST NOT place the call |
| `email` | Open the composer | `mailto:` |
| `map` | Open a location | `geo:` |
| `share` | System share sheet | current page URL |
| `home` | Navigate to `site.homeUrl` | same origin |
| `refresh` | Reload | — |
| `open_menu` | Open the browser-owned SiteSkin menu | — |

This list is an **allow-list**. Any other value MUST cause that item to be dropped
(`SS-E-ACTION-UNKNOWN`) while the rest of the manifest still applies — a site experimenting with a
`1.1` action SHOULD NOT lose its whole integration.

URI schemes are likewise allow-listed: `https`, `mailto`, `tel`, `geo`. Everything else MUST be
refused with `SS-E-SCHEME-DENIED`, including but not limited to `javascript:`, `file:`, `content:`,
`intent:` and `data:`. Implementations MUST NOT use a deny-list here; `intent:` alone demonstrates
why enumerating the dangerous cases is a bet that loses.

A browser MUST NOT acquire an operating-system permission in order to service an action. A `phone`
action opens the dialer with the number prefilled; the user presses dial.

### 7.1 `match` patterns and the glob grammar

`match` is an OPTIONAL array of path patterns marking a navigation item active. Patterns are matched
against the **path** of the current URL, anchored at its start, after origin canonicalization. Query
string and fragment are ignored.

The grammar is deliberately minimal:

| Token | Matches |
|---|---|
| `*` | zero or more characters **within a single path segment** (never `/`) |
| `**` | zero or more whole path segments, including none |
| anything else | itself, literally |

`?`, character classes (`[a-z]`), brace alternation (`{a,b}`) and escape sequences are **not** part
of the grammar and MUST be treated as literal characters. A pattern containing a scheme or an
authority is a schema violation.

Regular expressions are deliberately excluded: a pattern from an untrusted source invites
catastrophic backtracking, and the mitigation for that costs more code than this grammar does.

**Active-item resolution** MUST be deterministic:

1. An exact literal match wins over any pattern match.
2. Among pattern matches, the pattern with the longest literal prefix wins.
3. If still tied, the item appearing earliest in `bottomNavigation` wins.
4. If nothing matches, **no item is active**. A browser MUST NOT fall back to selecting the first
   item.

---

## 8. Limits

| Limit | Value | On exceeding |
|---|---|---|
| Manifest size | 131,072 bytes | reject, before parse |
| Navigation items | 5 | truncate + `SS-W-LIMIT-TRUNCATED` |
| Menu items | 20 | truncate + `SS-W-LIMIT-TRUNCATED` |
| Quick actions | 5 | truncate + `SS-W-LIMIT-TRUNCATED` |
| Title | 64 characters | truncate + `SS-W-LIMIT-TRUNCATED` |
| Subtitle | 128 characters | truncate + `SS-W-LIMIT-TRUNCATED` |
| Label | 32 characters | truncate + `SS-W-LIMIT-TRUNCATED` |

Collections truncate by keeping the **first** N items in document order. Strings truncate by
character rather than by byte, and MUST NOT split a grapheme cluster.

Truncation rather than rejection is deliberate: a slightly over-eager site should still get a
working integration. The size limit is the exception, because a body that large cannot be
interpreted at all without first paying the cost the limit exists to avoid.

---

## 9. Branding safety

### 9.1 Contrast is normative, not advisory

Manifest-supplied colours MUST be contrast-corrected before use. A site MUST NOT be able to render
browser-owned text unreadable by choosing a hostile background.

- Contrast ratio is computed per [WCAG 2.2 relative luminance][wcag].
- Text against its background MUST meet **AA**: `4.5:1` for body text, `3:1` for large text and UI
  components.
- Correction MUST adjust the **manifest-supplied** colour. A browser MUST NOT adjust the colour of
  browser-owned text, because that text is the signal the correction exists to protect.
- Correction MUST be deterministic: the same input pair MUST always produce the same corrected
  output, and MUST record `SS-W-CONTRAST-CORRECTED`.

The correction algorithm is specified exactly, not left to the implementation. This is unusually
prescriptive for a specification and it is deliberate: the conformance corpus pins corrected colour
values, and a fixture asserting an output that only one implementation can reproduce is a fixture
that tests that implementation rather than this format.

Given a manifest colour `C` and the browser-owned text colour `T`:

1. If `contrast(C, T) ≥ target`, `C` is unchanged and no diagnostic is recorded.
2. Otherwise let `direction` be *lighten* when `relativeLuminance(T) < 0.5`, else *darken*.
3. Repeatedly add (lighten) or subtract (darken) **8** from each of `C`'s red, green and blue
   channels, clamping each to `[0, 255]`, until `contrast(C, T) ≥ target` or 64 iterations elapse.
4. The result is the corrected colour. `SS-W-CONTRAST-CORRECTED` is recorded.

`target` is `4.5` for body text and `3.0` for large text and UI components. Channels are stepped
uniformly rather than in a perceptual colour space because the property being guaranteed is
legibility, not hue fidelity — a site that supplies a hostile colour has already forfeited the
argument about its exact shade.

[wcag]: https://www.w3.org/TR/WCAG22/#dfn-relative-luminance

### 9.2 Assets

- `logoUrl` MUST be same-origin. Subdomains are cross-origin here as everywhere
  (`SS-E-ASSET-CROSS-ORIGIN`).
- Permitted types: `image/png`, `image/webp`.
- **SVG is not supported in v1.** It is a scripting-capable format and its parsing surface is not
  worth the fidelity.
- Assets MUST be decoded off the main thread and bounded in both bytes and dimensions.
- An unreachable, oversized or wrong-typed logo MUST fall back to a monogram. It MUST NOT block the
  chrome from rendering.

---

## 10. Dispositions

Every diagnostic declares what it does to the manifest. Without this, "rejected" is not a testable
claim — and the disposition is a property of the *code*, not a per-site or per-fixture choice.

| Disposition | Meaning |
|---|---|
| `reject` | The whole manifest is discarded; the browser stays in regular mode. |
| `drop-item` | The offending element is removed; the rest of the manifest still applies. |
| `warn` | Nothing is removed; the diagnostic is recorded for the developer inspector. |

The rule behind the split: **reject only when the document cannot be interpreted as a whole.**
Anything narrower removes the offending element. Once a cross-origin URL is dropped, the remaining
manifest is exactly as safe as one that never contained it, and rejecting wholesale would only
punish a site for a typo.

Three consequences that MUST be implemented as stated, because each is a place where `drop-item` is
easy to get subtly wrong:

1. **Dropping every navigation item leaves navigation empty, not absent.** SiteSkin mode still
   applies and the bottom bar does not render. A browser MUST NOT fall back to regular mode merely
   because a collection ended up empty.
2. **`SS-E-DUPLICATE-ID` drops the later occurrence**, so the outcome depends on document order. The
   first occurrence of an `id` wins.
3. **A missing REQUIRED field is a rejection, not a dropped item.** The absence of `site.id` is not
   an element that can be removed.

## 11. Diagnostics

Every diagnostic has a stable code, so `siteskin-lint` and the browser report identically. The
machine-readable registry is [`diagnostics.json`](diagnostics.json); this table MUST agree with it.

| Code | Layer | Disposition | Meaning |
|---|---|---|---|
| `SS-E-SIZE-EXCEEDED` | transport | reject | body over 128 KB |
| `SS-E-PARSE` | parse | reject | not valid JSON |
| `SS-E-VERSION-UNSUPPORTED` | version | reject | unknown major version |
| `SS-E-SCHEMA-INVALID` | schema | reject | fails `siteskin-1.0.schema.json` |
| `SS-E-ORIGIN-MISMATCH` | security | drop-item | URL resolves outside the serving origin |
| `SS-E-SCHEME-DENIED` | security | drop-item | URI scheme not allow-listed |
| `SS-E-ACTION-UNKNOWN` | security | drop-item | unrecognised action type |
| `SS-E-ASSET-CROSS-ORIGIN` | security | drop-item | asset URL outside the serving origin |
| `SS-E-DUPLICATE-ID` | security | drop-item | duplicate navigation or action id |
| `SS-W-LIMIT-TRUNCATED` | security | warn | collection or string truncated |
| `SS-W-CONTRAST-CORRECTED` | security | warn | supplied colour adjusted for legibility |
| `SS-W-FIELD-UNKNOWN` | security | warn | field not in this schema version, ignored |
| `SS-W-ICON-UNKNOWN` | security | warn | icon name not recognised, generic glyph substituted |

The `E`/`W` prefix indicates severity to a reader; **the disposition, not the prefix, determines
behaviour.** `SS-E-ACTION-UNKNOWN` is an error the site should fix, and it drops one item rather
than the document.

## 12. Normalization and the canonical result

A manifest that survives validation is normalized into a **canonical result**. This projection is
what the conformance corpus pins, and it is defined here rather than by any implementation's data
types, so that a second implementer can conform to it without reading our source.

Normalization proceeds in this order:

1. Unknown fields are dropped (`SS-W-FIELD-UNKNOWN`).
2. Every URL is resolved to an absolute URL against the serving origin. Items whose URLs fail origin
   binding are dropped.
3. Items whose action type or URI scheme is not allow-listed are dropped.
4. Duplicate ids are dropped, later occurrence first.
5. Collections and strings are clamped to their limits.
6. Colours are parsed and contrast-corrected.
7. Diagnostics are collected in the order the steps above emit them.

The canonical result has this shape and this field order:

```json
{
  "schemaVersion": "1.0",
  "origin": "https://bloomflowers.example",
  "site": { "id": "…", "name": "…", "shortName": "…", "homeUrl": "https://…/" },
  "branding": {
    "primaryColor": "#RRGGBB",
    "secondaryColor": "#RRGGBB",
    "backgroundColor": "#RRGGBB",
    "textColor": "#RRGGBB",
    "logoUrl": "https://…"
  },
  "toolbar": { "title": "…", "subtitle": "…" },
  "bottomNavigation": [
    { "id": "…", "label": "…", "icon": "…",
      "action": { "type": "internal_url", "url": "https://…" },
      "match": ["/catalog", "/catalog/**"] }
  ],
  "quickActions": [ { "id": "…", "label": "…", "icon": "…", "action": { } } ]
}
```

Absent optional values are **omitted**, not rendered as `null`. Diagnostics are not part of the
result; they are reported alongside it.

Two exceptions, because "omitted when absent" and "the browser applies a default" would otherwise
describe the same field differently depending on who is reading:

- `origin` and `site.homeUrl` are **always present**. When the manifest omits `homeUrl`, or when it
  supplies one that fails origin binding, the canonical result carries the origin root.
- A collection that is emptied by dropped items is present as `[]`, not omitted. The distinction
  matters: `[]` means "the site asked for navigation and none of it survived", and per
  [§10](#10-dispositions) that is not the same as never having asked.

## 13. Conformance corpus

`fixtures/valid/**` — manifests that MUST be accepted, each paired with its canonical result.
`fixtures/invalid/**` — manifests that MUST produce the stated diagnostics, each paired with its
expected codes and dispositions.

Each fixture body is accompanied by a `<name>.expected.json` carrying the **origin it was served
from** — without which no origin-relative rule is expressible — plus the expected diagnostics and,
wherever anything survives, the canonical result.

**The corpus is the contract.** Implementation is written to satisfy it, never the reverse. Adding a
validation rule means adding a fixture first, and a rule with no fixture does not exist.

## 14. Checklist for site owners

1. Serve `/.well-known/siteskin.json` over HTTPS as `application/json`.
2. Keep every URL inside your own origin. Subdomains count as outside.
3. Set `Cache-Control`; the browser caps the lifetime at 24 hours regardless.
4. Run `siteskin-lint https://your-site.example`. Exit 0 means the browser will activate.

A worked example is [`fixtures/valid/bloom-flowers.json`](fixtures/valid/bloom-flowers.json), served
in production by `denrzv/bloom-flowers`.
