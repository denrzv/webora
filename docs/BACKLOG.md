# Backlog — ticket scopes

Every ticket in the roadmap, with the scope and the acceptance criteria that are already decided.

**These are not PRDs.** `/idea <TICKET>` instantiates the real PRD from the template when the ticket
starts, using the entry below as its input. Pre-writing twenty-three full PRDs would produce
artifacts that are stale before anyone reads them — the AIDD flow deliberately writes the PRD at
ticket start, when the preceding tickets have already taught you something.

`SPEC-001` and `CORE-001` have full PRDs at `Status: PRD_READY` already, because they are next.

Every ticket's acceptance criteria end with the same final item, per repo convention:
`bash scripts/pre-commit-check.sh` passes.

---

## M1 — SiteSkin API

**`SPEC-002` Versioning and compatibility policy.** ✅ **Done.** Accept `1.x`, reject `2.x`. Unknown
fields ignored with `SS-W-FIELD-UNKNOWN`. Documented deprecation path and what constitutes a
breaking change. Fixtures for `1.0`, `1.1`, `2.0`, missing version, malformed version.

> **What actually shipped**, since the scope moved once the ticket met the corpus. The `1.0`, `1.1`
> and `2.0` fixtures this entry asks for already existed — `SPEC-001` delivered them as
> `valid/minimal.json`, `valid/forward-compat-1.1.json` and `invalid/version-major-2.json`. Rather
> than duplicate them, `SPEC-002` added `invalid/version-missing.json` and
> `invalid/version-malformed.json` (the two genuinely absent), plus two the entry did not anticipate:
> `invalid/version-major-2-alien.json`, a `2.0` document the `1.0` schema rejects outright, which is
> the only fixture that can prove the version layer runs *before* the schema; and
> `invalid/unknown-field-1.0.json`, which pins that the unknown-field policy is version-independent
> rather than a courtesy extended to future minors.
>
> The boundary itself moved out of the document corpus entirely, into `spec/versions.json` — a
> 17-row decision table over every spelling at the edge. Version handling is a decision on a scalar,
> and near-identical manifests differing in one field would have cost more and tested less.
>
> Two defects were found and fixed inside the ticket, both in the *published schema*: `01.0` and
> `1.0` were two spellings of one version, and — the sharper one — every `^…$` pattern in the schema
> accepted a **trailing newline**, because `java.util.regex`'s `$` matches before a final line
> terminator while the ECMA-262 semantics JSON Schema specifies do not. `"schemaVersion": "1.0\n"`
> validated. Both are recorded as breaking changes in `SPEC.md` §4.5, taken deliberately inside the
> free-change window that section defines and closes.

**`SPEC-003` `siteskin-lint` CLI.** Fetches a live origin's manifest and validates it with the same
`:siteskin-core` code path the browser uses. Exit 0 = will activate. Prints diagnostic codes.
Acceptance: running it against `bloom-flowers` exits 0; against each `invalid/` fixture served
locally, it exits non-zero with the expected code. Must share the validator with the browser — a
second implementation is the failure this ticket exists to prevent.

**`CORE-002` DTOs and parsing.** kotlinx.serialization DTOs mirroring the schema. Byte-size guard
*before* parse. Unknown fields ignored with a warning. Acceptance: every `valid/` fixture parses;
malformed JSON yields `SS-E-PARSE`; a 129 KB payload yields `SS-E-SIZE-EXCEEDED` without being fully
read. Parse success must not produce a trusted type — the DTO is inert.

**`CORE-003` ✅ Schema validation.** Parsed JSON → `ManifestValidationResult(errors, warnings)`
carrying the spec's diagnostic codes. The production validator executes the version table and every
parsable corpus document, short-circuiting unsupported majors before structural validation. Because
`CORE-002` is not yet implemented, the seam accepts `JsonElement`; byte parsing, DTO mapping, and
unknown-field discovery remain there. Security allow-lists deliberately remain `CORE-004`.

**`CORE-004` Security validation and normalization.** The heart of the trust boundary. Origin
binding for every URL, scheme allow-list, icon-name allow-list, asset same-origin check, colour
parsing with WCAG AA contrast correction, limit clamping with truncation warnings. Produces
`SiteSkinConfiguration`, constructible **only** here. Acceptance: cross-origin `internal_url` is
rejected; each denied scheme is rejected; a hostile colour pair is corrected and flagged; over-limit
collections truncate. Needs a negative control per protection.

**`CORE-005` Action model and resolution.** Nine allow-listed types → sealed `ResolvedAction`.
Unknown type drops the item and keeps the manifest (`ADR-007`). Acceptance: each type resolves to
the right typed result; unknown type drops exactly one item; `phone` resolves to a dial intent
description, never a call.

**`CORE-006` Navigation active-state matching.** Exact path, then longest glob (`/cart/**`).
Deterministic tie-breaking. Acceptance: exact beats glob; longest glob wins; no match yields no
selection rather than a default.

---

## M2 — Browser foundation

**`BROWSE-001` WebView host and hardening.** JS on; `setAllowFileAccess(false)`,
`setAllowFileAccessFromFileURLs(false)`, `setAllowUniversalAccessFromFileURLs(false)`; SafeBrowsing
via androidx.webkit; mixed content blocked; **no `addJavascriptInterface`** (`ADR-005`). Acceptance:
a `file://` URL cannot be loaded from a web page; each setting has an asserting test.

**`BROWSE-002` Browser state machine and navigation.** Sealed `BrowserMode` (`ADR-008`), URL/search
bar, back/forward/reload, predictive back. Acceptance: mode transitions are total and tested;
back exits the app only from the first page.

**`BROWSE-003` Home and onboarding.** Mockup screens 1–2: recents, favourites, suggested
integrations, onboarding carousel.

**`BROWSE-004` Regular-mode chrome.** Security indicator, overflow menu, error pages, and the
"not integrated with SiteSkin" hint (mockup screen 6). Acceptance: the indicator reflects real TLS
state and cannot be influenced by page content.

**`BROWSE-005` External navigation, downloads, uploads.** Confirmation before leaving an origin;
`DownloadManager` for downloads; SAF for uploads; non-http schemes confirmed before dispatch.
Acceptance: no storage permission is requested; an `intent:` URL from a page does not auto-dispatch.

---

## M3 — SiteSkin runtime

**`NET-001` Manifest fetcher.** OkHttp implementation of `ManifestSource`. HTTPS-only, 128 KB cap,
timeouts, same-origin redirects max 2, concurrent with page load (`ADR-009`). Acceptance via
MockWebServer: cross-origin redirect refused; oversized body aborted mid-stream; page render never
awaits the fetch.

**`NET-002` Manifest cache.** Keyed `origin + schemaVersion`. ETag/Last-Modified, TTL
`min(Cache-Control, 24h)`, offline reuse. Acceptance: a cached manifest is **never** returned for a
different origin — with a negative control.

**`NET-003` Brand asset pipeline.** Same-origin only, PNG/WebP only (**no SVG**), byte and dimension
caps, off-main-thread decode, monogram fallback. Acceptance: an SVG logo is refused; an oversized
image is refused before full decode; failure yields the monogram, never a crash or blank bar.

**`SKIN-001` Dynamic theming.** M3 `ColorScheme` from validated branding, dark derivation, contrast
guard applied before first paint. Acceptance: a manifest whose text and background match is
corrected to AA before render.

**`SKIN-002` SiteSkin top bar.** Mockup screens 3–5, with the **non-suppressible** registrable
domain and TLS indicator (`ADR-006`). Acceptance: no manifest field can remove them — negative
control required; logo confined to its slot regardless of intrinsic size.

**`SKIN-003` Bottom navigation, quick actions, menu.** Up to 5 nav items, quick-action FAB, side
menu. Acceptance: over-limit manifests render exactly 5; long labels truncate without overlap at
maximum font scale.

**`SKIN-004` Mode transitions and origin change.** Activation, deactivation on origin change, skin
swap between two integrated origins. Acceptance: the concept's §46 steps 14–15 — Bloom → PixelPlay
swaps skins, Bloom → News drops to regular — as instrumentation tests against the demo origins.

---

## M4 — Hardening, privacy, demo

**`HARDEN-001` Adversarial corpus.** `javascript:`/`file:`/`content:`/`intent:`/`data:` schemes,
oversized and deeply-nested payloads, redirect loops, IDN homographs, duplicate ids, over-limit
collections, hostile colours. Acceptance: every case has a negative control proving the test detects
removal of the protection.

**`HARDEN-002` Brand-impersonation controls.** Implements `ADR-006` + `ADR-011` end to end:
non-suppressible domain, bounded logo slot, first-use consent sheet, per-site persistence.
Acceptance: a manifest impersonating another brand still shows its true registrable domain; consent
is required before any chrome change; "Never for this site" persists across restart.

**`PRIV-001` Privacy controls.** Zero telemetry by default. Global and per-site SiteSkin toggles.
Clear browsing data. Data-safety form mapping. Acceptance: a network capture during a full browse
session shows no request to any Webora-controlled host.

**`A11Y-001` Accessibility.** TalkBack labels, font scaling to 200%, 48dp targets, contrast, no
reliance on colour alone. Acceptance: the skinned chrome is fully navigable by TalkBack, and
manifest-supplied colours never reduce contrast below AA.

**`DEVX-001` SiteSkin Integration Inspector.** Debug-only panel: current origin, manifest URL, HTTP
status, cache state, schema version, validation result, warnings, applied theme, active nav item,
rejected actions (concept §31). Acceptance: absent from release builds — asserted, not assumed.

**`DEMO-001` Bloom Flowers.** Full reference site in `denrzv/bloom-flowers` plus `INTEGRATION.md`.
Acceptance: `siteskin-lint` exits 0 against the deployed origin; the app renders mockup screen 3.

**`DEMO-002` PixelPlay, Daily Journal, Example News.** Three more origins, the last deliberately
without a manifest as the negative control. Acceptance: all four served over HTTPS on **distinct
origins**, enabling `SKIN-004`'s transition tests.

---

## M5 — Google Play

**`PLAY-001` Compliance sweep.** targetSdk 36; AAB; permissions minimized; edge-to-edge; predictive
back; 16 KB page sizes; Policy 4.3 minimum-functionality evidence prepared (Webora is a
general-purpose browser, and the reviewer note should say why in one paragraph). Acceptance: a
completed checklist with evidence per line.

**`PLAY-002` Release signing and R8.** Implement the signing config — env → gradle property →
`local.properties`. Verify R8 keeps against a real release build; confirm kotlinx.serialization
serializers survive minification. Acceptance: a signed AAB installs and runs, and SiteSkin mode
still activates in the minified build. Missing credentials must fail the release build loudly, not
silently fall back to the debug key.

**`PLAY-003` Store listing.** Listing copy, screenshots, feature graphic, launcher icons, Data
safety form, privacy policy hosted on the product domain, internal testing track. Acceptance: Play
Console pre-launch report clean; the privacy policy URL resolves from both the listing and inside
the app.
