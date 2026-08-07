# CORE-001: Origin model and URL resolution
Status: PRD_READY

## Context / Problem

Origin binding is the foundation every other security control rests on (`ADR-004`). If
`SiteOrigin` gets equality or parsing wrong, then cache poisoning, cross-origin branding leakage and
`internal_url` escapes all follow — and each of those would look like a bug in a different ticket.

The subtle failures are not in the obvious cases. `https://shop.example` and
`https://shop.example:443` are the same origin; `https://ShOp.Example` is too. `https://аpple.com`
with a Cyrillic а is a different origin that renders identically. Getting these right once, in
pure Kotlin with no Android dependency, is much cheaper than discovering them later through the
WebView.

## Goals

- `SiteOrigin` value type: scheme, host, port, with correct equality and a canonical string form.
- Parse from a URL string; reject anything that is not `http`/`https`.
- Default port normalization (`:443` for https, `:80` for http elide).
- Host normalization: lowercase, IDN → punycode (ASCII form is canonical).
- Mixed-script / homograph detection surfaced as a signal for `SKIN-002` to display.
- Registrable domain (eTLD+1) extraction — `ADR-006` requires it for the always-visible chrome.
- Relative path resolution against an origin, rejecting anything that escapes it.

## Non-goals

- Public Suffix List bundling decision beyond MVP needs — document the chosen source and its update
  path; do not build an updater.
- URL *display* formatting — that is `SKIN-002`/`BROWSE-004`.
- Manifest semantics — `CORE-003`+.

## User stories

- As `CORE-004`, I can ask "does this resolved URL belong to this origin?" and get a trustworthy
  yes/no.
- As `NET-002`, I can key a cache on an origin and be certain two different sites never collide.
- As `SKIN-002`, I can render the registrable domain and know whether the host is homograph-risky.

## Acceptance criteria

1. `SiteOrigin.parse` returns null for non-`http(s)` schemes, including `javascript:`, `file:`,
   `content:`, `intent:`, `data:`.
2. `https://shop.example` == `https://shop.example:443` == `https://SHOP.example`.
3. `https://shop.example` != `https://admin.shop.example` != `http://shop.example`.
4. IDN hosts canonicalize to punycode; `https://münchen.example` == `https://xn--mnchen-3ya.example`.
5. A host mixing scripts (e.g. Latin + Cyrillic) is flagged, and the flag is exposed on the type.
6. `resolve(origin, "/cart")` yields the same-origin absolute URL; `resolve` of an absolute
   cross-origin URL, a protocol-relative `//evil.example`, or a traversal that escapes the origin
   all return a rejection, not a URL.
7. Registrable domain extraction is correct for multi-label suffixes (`example.co.uk`,
   `site.github.io`).
8. Tests run with `ANDROID_HOME` unset.
9. `bash scripts/pre-commit-check.sh` passes.

## NFR

- **Security/privacy:** no `java.net.URL` — its `equals` performs DNS resolution, which both blocks
  and leaks. Use `java.net.URI` or an explicit parser.
- **Reliability/fallback:** parse failures return null; nothing here throws into a browsing path.
- **Performance:** called on every navigation; must be allocation-light and synchronous.
- **Accessibility:** the homograph flag exists so the UI can warn — `CORE-001` supplies the signal,
  `SKIN-002` decides the presentation.

## Risks

- **eTLD+1 needs the Public Suffix List**, which is a data file with an update cadence. A stale list
  mis-identifies newer suffixes. Mitigation: pick the source in `/plan`, record staleness impact,
  and prefer failing toward showing *more* of the host rather than less.
- **Unicode normalization order.** Case-folding before punycode conversion gives different results
  than after for some scripts. Mitigation: fix the order in the spec, fixture both directions.

## Open questions

- Bundle the PSL or use a minimal built-in suffix set for MVP? Resolve in `/plan` — the trade is
  APK size against correctness on uncommon TLDs.
