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
- JSON structural nesting MUST NOT exceed **64** objects/arrays. Implementations MUST enforce the
  limit before constructing a JSON tree; deeper input is rejected with `SS-E-PARSE` even when its
  JSON grammar is otherwise valid.
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
integers written without leading zeros.

| Manifest declares | Browser supporting 1.x |
|---|---|
| `1.0` | accepted |
| `1.y`, any `y` | accepted; unknown fields ignored with `SS-W-FIELD-UNKNOWN` |
| `0.x`, `2.x`, or any other major | **rejected** → `SS-E-VERSION-UNSUPPORTED`, regular browser mode |
| absent, non-string or malformed | **rejected** → `SS-E-SCHEMA-INVALID` |

Minor versions are additive by definition; a browser MUST ignore fields it does not recognise rather
than rejecting the document. An unknown **major** version MUST reject the whole manifest — an
implementation that reinterprets a format it does not know has replaced a security boundary with a
guess.

The supported majors are an **allow-list**, currently `{1}`. "Reject `2.x`" is a consequence of that
allow-list, not a rule of its own; a `3.0` or `7.4` manifest is refused by the same mechanism and
MUST NOT require a new rule to be written. The machine-readable table of decisions, including every
malformed spelling at the boundary, is [`versions.json`](versions.json).

### 4.1 Validation layers and short-circuiting

Validation happens in five layers, in this order:

```
transport → parse → version → schema → security
```

**A manifest rejected at one layer MUST NOT be evaluated by any later one.** This is normative
rather than an implementation note: the diagnostics a conforming implementation reports for a given
document depend on it, and the conformance corpus asserts that a fixture never expects a diagnostic
from a layer its own rejection has already made unreachable. The order is published in
[`diagnostics.json`](diagnostics.json) as `layerOrder`.

| Layer | Enforces | On failure |
|---|---|---|
| `transport` | HTTPS, redirect policy, the 128 KB cap — before the body is read | `SS-E-SIZE-EXCEEDED` |
| `parse` | bytes are JSON | `SS-E-PARSE` |
| `version` | the declared major is supported | `SS-E-VERSION-UNSUPPORTED` |
| `schema` | structural validity per `siteskin-1.0.schema.json` | `SS-E-SCHEMA-INVALID` |
| `security` | origin binding, allow-lists, limits, contrast | see [§11](#11-diagnostics) |

#### Why `version` precedes `schema`, and why they are two codes

The split is a real distinction, not a filing convenience, and getting it backwards produces wrong
diagnostics:

- The **version** layer runs only on a `schemaVersion` that is **present and well-formed**. It asks
  one question — is this major supported? — and that question is not expressible in JSON Schema,
  because `siteskin-1.0.schema.json` deliberately does not pin the major (it must stay valid for all
  of `1.x`). An unsupported major is therefore a **policy** rejection.
- An **absent, non-string or malformed** `schemaVersion` is a **structural** defect. The version
  layer has no opinion on it, because there is no major to evaluate. It is caught by the schema like
  any other malformed field, and yields `SS-E-SCHEMA-INVALID`.

Version runs first because a manifest declaring a major we do not know may legitimately have a shape
this schema was never written for. Validating a `2.0` document against the `1.0` schema would emit a
pile of structural errors about a format we have already decided not to interpret — and a pile of
errors is an invitation to handle them. Refusing on the version alone is the smaller and safer
behaviour. `fixtures/invalid/version-major-2-alien.json` is that case made concrete: a `2.0`
document that the `1.0` schema rejects outright, and whose only conforming diagnostic is
`SS-E-VERSION-UNSUPPORTED`.

A conforming implementation MUST NOT report `SS-E-VERSION-UNSUPPORTED` for a manifest that declares
no version at all.

### 4.2 What may change in a minor

A **minor** bump (`1.0` → `1.1`) is for changes an implementation of the previous minor handles
correctly *without being modified*. The test is mechanical, and it is the only test:

> Take a conforming implementation of `1.N`. Feed it a `1.N+1` document. If it produces a safe,
> sensible result — possibly with fewer features, never with a wrong one — the change was additive.

The following are additive, and MUST NOT bump the major:

1. **Adding an OPTIONAL field**, at any nesting level. Older readers ignore it with
   `SS-W-FIELD-UNKNOWN` — the policy in §4's table, unchanged since `1.0`, and the reason additive
   growth is legal at all.
2. **Adding an allow-listed `action.type`**. An older reader drops that one item with
   `SS-E-ACTION-UNKNOWN` and keeps the rest of the manifest — which is exactly the behaviour
   [`ADR-007`](../docs/adr/README.md) specifies, and the reason it specifies it.
3. **Adding an `icon` name.** An older reader substitutes a generic glyph with `SS-W-ICON-UNKNOWN`.
4. **Adding a `presentation` hint value.** An older reader falls back to `auto` with
   `SS-W-PRESENTATION-UNKNOWN` and chooses for itself — which is what `auto` asks for anyway, so the
   degraded result is one the site already declared itself content with.
5. **Adding a diagnostic code** whose disposition is `warn` or `drop-item`.
6. **Raising a limit** in [§8](#8-limits). A `1.1` manifest with 8 navigation items renders 5 on a
   `1.0` reader, truncated with `SS-W-LIMIT-TRUNCATED` — degraded, not wrong. Site owners SHOULD
   assume older readers truncate.
7. **Adding a discovery mechanism.** A `<link rel="siteskin">` element, for instance, would be
   additive: a reader that does not know it still finds `/.well-known/siteskin.json`, so no manifest
   becomes unreachable. (This is an illustration of the rule, not a commitment to ship it.)
8. **Adding an OPTIONAL top-level section**, on the same basis as (1). `presentation` was added to
   `1.0` inside the free-change window of [§4.5](#45-the-free-change-window-and-the-changes-taken-inside-it);
   a section added after it is a minor bump.

A minor bump is a **declaration of intent, not a request for permission.** Nothing is unlocked by
declaring `1.1`: a `1.0` reader given a `1.1` document does not acquire `1.1` behaviour, and a site
that declares `1.1` while using only `1.0` fields loses nothing. Sites SHOULD bump the minor when
they start using a field introduced in it, because the declared version is what a diagnostic report
and a cache entry are keyed on.

### 4.3 What forces a major

A **major** bump (`1.x` → `2.0`) is required when a conforming implementation of the previous
version, fed a document valid under the new one, would produce a **wrong** result rather than a
degraded one. Concretely, each of the following is breaking:

1. **Removing or renaming a field** that had meaning. Older readers keep applying the old one.
2. **Changing a field's type or value grammar**, including narrowing it. A previously-conforming
   manifest may stop conforming.
3. **Adding a REQUIRED field.** Every existing document instantly becomes invalid.
4. **Changing what an existing value means** — redefining `internal_url`, or changing which URL
   `home` resolves to.
5. **Lowering a limit.** A manifest that rendered fully now silently truncates.
6. **Changing a diagnostic's disposition** — `drop-item` to `reject`, or `reject` to `drop-item`.
   The disposition is part of the code's definition ([§10](#10-dispositions)), so changing it
   changes what a document does.
7. **Changing normalization order or the canonical result's shape** ([§12](#12-normalization-and-the-canonical-result)),
   where the change alters the result for a document that was already valid.
8. **Removing a supported major from the allow-list.**

Editorial changes — clarified wording, added examples, corrected typos, new fixtures that pin
already-required behaviour — bump **nothing**. There is no patch component, and this is why one is
not needed: a change that does not alter what a conforming implementation does is not a version.

#### The security carve-out

**A change required to close a security hole MAY ship in a minor, even when the rules above classify
it as breaking.**

This is stated as an exception rather than by defining such changes as non-breaking, because they
*are* breaking and pretending otherwise would not survive contact with the first real case. The
alternative is worse: a compatibility promise that can trap a security fix behind a major bump is a
promise to leave holes open, and no site owner is served by that.

The exception is bounded. A change taken under it MUST:

- be the **narrowest** change that closes the hole;
- be recorded in this document with its reasoning, as §4.5 does for the two taken so far;
- come with a conformance fixture, so the new behaviour is pinned rather than described;
- and degrade gracefully — a manifest caught by the new rule falls back per
  [`ADR-010`](../docs/adr/README.md), never to a broken page.

"Security" here means a defect that lets a site influence something the trust model says it must
not. It does not mean tidiness, consistency, or a rule someone now considers a mistake.

### 4.4 Deprecation

A field is never removed without warning. The lifecycle is:

1. **Deprecated in a minor.** The field is marked deprecated in this document, in the same minor
   that introduces its replacement. It keeps working exactly as before.
2. **Honoured for the remainder of the major.** A conforming implementation MUST continue to honour
   a deprecated field until the next major. It MUST NOT downgrade it to a warning-only no-op, and
   MUST NOT treat its presence as an error.
3. **Removed only at a major.** Removal is a breaking change by §4.3(1) and follows that rule with
   no exception.

An implementation MAY report a deprecated field to the developer inspector. The reserved code for
that is **`SS-W-FIELD-DEPRECATED`** (disposition `warn`).

That code is deliberately **not** in [`diagnostics.json`](diagnostics.json), and the omission is not
an oversight. Nothing in `1.0` is deprecated, so the code has no fixture — and this specification's
rule is that a code with no fixture does not exist ([§13](#13-conformance-corpus)). Registering it
early would mean the registry asserted something false about the format in order to satisfy a test.
The name is reserved here so the first real deprecation cannot pick a colliding one; the code and
its fixture enter the registry together.

**The honest limit of this guarantee:** it is expressed in versions, not in time. "Until the next
major" is a strong promise only if majors are rare, and this format has no release cadence yet to
anchor a duration to. A site owner should read it as *"you will not be broken by a minor"* — which
is precise — rather than as a calendar commitment, which it is not. When there is a cadence, this
section should gain one.

### 4.5 The free-change window, and the changes taken inside it

The compatibility promise in §§4.2–4.4 begins binding when this section was written. Before that
point the format had no published `$id`, no deployed manifest outside this repository, and no
consumer but its own conformance corpus — so changing it cost nobody anything, and the honest thing
was to make the corrections that were known to be needed rather than grandfather them.

Two such changes were taken, on **2026-08-08**, in `SPEC-002`. Both narrow a value grammar, so both
are **breaking changes** by the rules in §4.3. They are recorded here rather than reclassified,
because a policy that redefines its own first violations as compliant is not a policy.

**(a) `schemaVersion` no longer admits leading zeros.**

> `^[0-9]+\.[0-9]+$` → `^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)…`. Under the old grammar `01.0` and `1.0`
> were two spellings of one version.

The motivation is not tidiness. `schemaVersion` participates in the manifest cache key
(`origin + schemaVersion`), so two spellings of one version mean two cache entries for one
configuration. Fixing the grammar is strictly better than normalizing at read time, because a
normalization step is a second place for the two spellings to diverge.

**(b) Every pattern in the schema now anchors with `(?![\s\S])` instead of `$`.**

JSON Schema specifies ECMA-262 regular expressions, where an unflagged `$` matches only at end of
input. `java.util.regex` is not ECMA-262 here: its `$` *also* matches immediately before a final
line terminator. So on a JVM validator every `^…$` pattern in this schema accepted a trailing
newline, and `"schemaVersion": "1.0\n"` validated. Change (a) alone would not have closed that —
`"01.0\n"` and `"1.0\n"` simply move the ambiguity one character along.

`(?![\s\S])` asserts that no character of any kind follows, and behaves identically under both
engines. The correction is applied to all six patterns, not only `schemaVersion`, because the defect
is in the anchoring idiom rather than in any one field.

This is the sharper of the two findings and it is worth stating plainly: **a pattern that is
correct under the specification it cites can still be wrong under the engine that runs it.** A
conforming implementation MUST reject a `schemaVersion` with leading or trailing whitespace of any
kind, including a trailing newline, and SHOULD verify that against its own regex engine rather than
inferring it from the pattern's shape.

The window is now closed. Either change made after this section would require a `2.0`, and no
further changes of this kind will be taken. Note that §4.3's security carve-out remains available
for defects discovered later — it exists precisely so that a narrowing needed to close a hole is
never trapped behind a major bump.

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
      "action": { "type": "internal_url", "url": "/" },
      "match": ["/"] },
    { "id": "catalog", "label": "Catalog", "icon": "grid_view",
      "action": { "type": "internal_url", "url": "/catalog" },
      "match": ["/catalog", "/catalog/**"] },
    { "id": "cart", "label": "Cart", "icon": "shopping_cart",
      "action": { "type": "internal_url", "url": "/cart" },
      "match": ["/cart/**"] },
    { "id": "profile", "label": "Profile", "icon": "person",
      "action": { "type": "internal_url", "url": "/account" },
      "match": ["/account/**"] }
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

`icon` names are drawn from a browser-provided set and MUST match `^[a-z][a-z0-9_]{0,31}$`. The v1
semantic vocabulary is `home`, `catalog`, `flower`, `grid_view`, `shopping_cart`, `person`, `call`,
`share`, `menu`, and `search`. `grid_view` is the compatibility spelling for a general catalogue;
new integrations SHOULD use `catalog`, or `flower` when that domain-specific cue is accurate. These
names select bundled browser artwork — they are not Android resource names.

The pattern is the security requirement: it structurally prevents an icon field from carrying a URL
or any other resource reference. An icon name the browser does not recognise MUST fall back to a
generic browser-owned icon with `SS-W-ICON-UNKNOWN`; it MUST NOT reject the manifest, for the same
reason an unknown action type does not (see [§7](#7-actions)).

`presentation` is an OPTIONAL object of hints. Every value in it selects among presentations the
browser already implements; nothing in it may supply a dimension, colour, shape, asset, URL,
callback or duration, and a browser MUST NOT treat it as a general style channel. A hint is a
**preference, not an instruction**: the browser decides whether it can be honoured, and it remains
free to present a hinted surface differently, or to ignore the hint entirely on a device or in an
accessibility configuration where the alternative is unsuitable.

`presentation.hub` is an OPTIONAL string naming how the site's navigation and actions are gathered
when the user opens the browser's site hub:

```json
"presentation": { "hub": "drawer" }
```

It MUST match `^[a-z][a-z0-9_]{0,31}$`. The v1 vocabulary is:

| Value | Meaning |
|---|---|
| `auto` | No preference; the browser chooses. This is the behaviour of an absent field. |
| `bouquet` | Prefer a compact radial arrangement of the site's quick actions. |
| `drawer` | Prefer a start-side list presenting navigation, quick actions and the extended menu. |

`presentation.dock` is an OPTIONAL array of ids, nominating items declared elsewhere in this manifest
for the browser's persistent integrated surface:

```json
"presentation": { "hub": "drawer", "dock": ["catalog", "cart", "profile"] }
```

Each entry MUST match the `id` grammar and MUST name a `bottomNavigation`, `quickActions` or `menu`
item **declared in the same manifest**. The field carries ids and nothing else: it MUST NOT be used
to introduce an action, a URL, a label, an icon or an ordering of anything but itself. A browser
therefore learns nothing new from it — every item it can name has already been validated — and this
is what keeps a persistent native surface from becoming a site-defined one.

At most three entries are honoured ([§8](#8-limits)). Over that, a browser MUST truncate with
`SS-W-LIMIT-TRUNCATED` rather than reject, and MUST truncate **before** resolving ids, so a site
cannot name six, have three fail, and receive its fourth choice. A repeated id MUST be dropped
first-occurrence-wins with `SS-E-DUPLICATE-ID`. An id that names no declared item MUST be dropped
with `SS-W-DOCK-UNRESOLVED`. None of these rejects the manifest, and none removes the *item* — only
the reference to it, so the item remains available everywhere else the browser presents it.

Resolution MUST run against the items that survived security validation. An item dropped for origin,
scheme or action reasons MUST NOT become reachable by naming it here.

A browser remains free to project fewer entries than requested, or none, when its viewport or
accessibility configuration makes the projection unsuitable — `presentation` is a hint throughout.

As with `icon`, the pattern rather than an enumeration is the structural requirement, and for the
same reason: a hub value the browser does not recognise MUST fall back to `auto` with
`SS-W-PRESENTATION-UNKNOWN`, and it MUST NOT drop an item or reject the manifest. The proportionality
argument is sharper here than for `icon` — a hint decides only which of the browser's own components
the user sees, so discarding a whole working integration over a typo in it would be absurd. A site
with a `menu` longer than a compact presentation can show SHOULD request `drawer`, since a browser
that honours the hint is the one that can guarantee every entry is reachable.

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
| JSON structural nesting | 64 objects/arrays | reject, before tree construction |
| Navigation items | 5 | truncate + `SS-W-LIMIT-TRUNCATED` |
| Menu items | 20 | truncate + `SS-W-LIMIT-TRUNCATED` |
| Quick actions | 5 | truncate + `SS-W-LIMIT-TRUNCATED` |
| Dock projection ids | 3 | truncate + `SS-W-LIMIT-TRUNCATED` |
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
| `SS-W-PRESENTATION-UNKNOWN` | security | warn | presentation hint not recognised, `auto` substituted |
| `SS-W-DOCK-UNRESOLVED` | security | warn | dock projection named an id no declared item carries |

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
  "presentation": { "hub": "drawer", "dock": ["catalog", "cart"] },
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

`presentation` follows the general rule and is **omitted when the manifest did not declare it** —
including when it declared only fields this version does not define. It is NOT filled in with
`{"hub": "auto"}`, even though a browser treats the two identically. A manifest that declares
`"hub": "auto"`, one that declares a hub value the browser corrected to `auto`, and one that
declares nothing are three different documents, and only the first two said anything a site owner
can be shown. A canonical result that materialised the default would erase that distinction and
would also change the pinned result of every existing manifest in the corpus.

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
