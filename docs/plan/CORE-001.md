# CORE-001: Implementation plan
Status: PLAN_APPROVED

## Overview

Adds `dev.siteskin.core.origin` — the first implementation package in `:siteskin-core`. It supplies
one value type (`SiteOrigin`), one resolver (`UrlResolver`), and two derived signals the chrome needs
(`registrableDomain`, `hasMixedScriptHost`). Nothing here parses a manifest, emits a diagnostic code
or knows what a manifest is.

The whole ticket exists to make one question answerable and hard to answer wrongly: **does this URL,
written by an untrusted site, belong to the origin that served it?**

## Origin-boundary implications *(stated before the file list, per `PROJECT_RULES.md`)*

| Capability | Website-controlled | Browser-owned contract |
|---|---|---|
| The URLs in a manifest | fully | Every one is resolved against the serving origin and compared on a canonical form. No manifest field supplies an origin |
| Which of its own items survive | indirectly | A URL that fails binding drops that item (`CORE-004` names the code). Self-harm only |
| The serving origin | **no** | A parameter from the WebView's committed URL. `SiteOrigin` has no constructor a caller can reach |
| The rendered registrable domain | **no** | Computed here from a bundled PSL snapshot; `ADR-006` makes it non-suppressible |
| The homograph signal | **no** | Computed from the pre-punycode host and carried on the type |
| Whether a comparison succeeds | **no** | String equality on `(scheme, host, port)`, all canonical |

The contract in one line: **a `SiteOrigin` that exists is canonical**, so every comparison in the
system is a comparison of canonical forms. This is the same construct-only-via-validator device
`conventions.md` requires of `SiteSkinConfiguration`, applied one layer lower — enforced with a
private constructor and no `copy()`, not with a comment.

## Flow

- **discovery** — not this ticket. `SiteOrigin.parse` receives a URL string from the caller.
- **canonicalization** — scheme lowercased and allow-listed to `http`/`https`; authority split off
  `java.net.URI`'s raw authority (never `getHost()`); userinfo present ⇒ reject; host lowercased
  *after* IDN conversion; default port elided.
- **resolution** — `UrlResolver.resolveInternal(origin, raw)` returns a sealed result: an absolute
  same-origin URL, or a typed rejection. Never a normalized URL that "looks close enough".
- **UI state** — not this ticket. `SKIN-002` renders `registrableDomain` and reacts to
  `hasMixedScriptHost`.

## Data

**Trust boundary.** `SiteOrigin` is the trusted side; every `String` URL crossing into it is the
untrusted side. There is no DTO here because there is no serialization — the untrusted input is a
bare string, and `parse` returning `SiteOrigin?` *is* the boundary.

```kotlin
public class SiteOrigin private constructor(
    public val scheme: String,      // "http" | "https", lowercase
    public val host: String,        // punycode, lowercase, no trailing dot
    public val port: Int,           // explicit; default ports normalized to 443/80
)
```

- `equals`/`hashCode` cover **exactly** `(scheme, host, port)`. Written by hand, not derived — a
  `data class` would also synthesize `copy()`, which is a public constructor wearing a hat.
- `canonical`: `"$scheme://$host"`, with `":$port"` appended only for a non-default port. Matches the
  corpus's `"origin"` field verbatim.
- `rootUrl`: `"$canonical/"` — what `site.homeUrl` falls back to per `SPEC.md` §12.
- `registrableDomain`: `String`, computed on construction. See the PSL section.
- `hasMixedScriptHost`: `Boolean`, computed from the **pre-punycode** host, which is why it is
  computed during `parse` rather than derivable later from `host`.

**Neither derived property participates in equality.** `registrableDomain` and `hasMixedScriptHost`
are functions of `host`, so including them would be redundant; excluding them is what makes it
impossible for a display concern to influence a security comparison. The KDoc says so, and
`SiteOriginTest` asserts it.

**Storage / cache keys.** None here. `NET-002` will key its cache on `origin + schemaVersion`;
`canonical` is the string it should use, and this ticket's job is that two different sites can never
produce the same one.

## Security

### Origin binding

`resolveInternal` accepts a raw reference and returns `UrlResolution`:

```kotlin
public sealed interface UrlResolution {
    public data class Resolved(val url: String) : UrlResolution
    public data class Rejected(val reason: UrlRejection) : UrlResolution
}

public enum class UrlRejection {
    MALFORMED, OPAQUE, SCHEME_NOT_ALLOWED, USERINFO_PRESENT,
    PROTOCOL_RELATIVE, TRAVERSAL_ESCAPE, CROSS_ORIGIN,
}
```

`CORE-004` collapses every one of these to `SS-E-ORIGIN-MISMATCH` (or `SS-E-SCHEME-DENIED`), so the
enum is not a diagnostic vocabulary — it is what makes each test name the rule it is testing, and
what `DEVX-001` will surface in the inspector. **`CORE-001` emits no `SS-*` code.** The layer that
owns the disposition owns the code.

The five `SPEC.md` §3 MUST-rejects, each mapped to a mechanism rather than a hope:

| §3 clause | Mechanism | Why the obvious approach fails |
|---|---|---|
| protocol-relative `//evil.example/x` | reject a reference starting `//` **before** calling `resolve` | measured: `resolve` silently inherits the scheme and returns `https://evil.example/x` |
| traversal `/../../evil` | `normalize()`, then reject if any segment is `..` | measured: `normalize()` leaves a leading `..` in place, so the residue *is* the signal |
| userinfo `https://a@evil.example/` | reject when raw authority contains `@` | a `startsWith`/`contains` check on the host passes — this is the fixture that punishes string comparison |
| differing port | compare parsed `port` after default-elision | `:443` vs implicit must compare equal; `:8443` must not |
| any sub/parent/sibling domain | full canonical host equality | suffix matching makes `notbloomflowers.example` a match |

Order matters and is fixed: **reject protocol-relative before resolving**, and **reject traversal
before comparing origins**. A traversal that escapes to a path still inside the origin
(`/a/../../b` → `/../b`) would otherwise compare origin-equal and be accepted.

### Allow-lists

- Schemes at this layer: `http`, `https`. Everything else — `javascript:`, `file:`, `content:`,
  `intent:`, `data:`, and anything unlisted — returns `null` from `parse` and
  `SCHEME_NOT_ALLOWED` from the resolver. Allow-list, per `ADR-007`.
- Host labels: `^[a-z0-9]([a-z0-9-]*[a-z0-9])?$` after punycode, max 63 bytes, ≤ 253 total. This is
  the STD3 rule `IDN.toASCII` does not apply by default — measured: `-bad.example` passes it
  untouched.

### Decisions on the research note's open questions

1. **Bundle the PSL verbatim.** It stays diffable against `publicsuffix.org`, and it is the only
   option that keeps the MPL-2.0 notice and the `VERSION:` line inside the artifact. ~333 KB on
   disk, ~46 KB in a compressed APK.
2. **No matching rule ⇒ the full host is the registrable domain.** A deliberate divergence from the
   PSL algorithm's default `*` rule, which would return the last two labels. The divergence is
   *toward showing more*, per the PRD, and the reason is that this value is an anti-impersonation
   affordance rather than a cookie scope: for an unknown suffix, the default rule renders
   `evil.co.newtld` and `bank.co.newtld` identically as `co.newtld`, which is precisely the collision
   `ADR-006` exists to prevent. Recorded in `ADR-004` as a documented deviation.
3. **Trailing root dot: strip exactly one.** `shop.example.` and `shop.example` are the same DNS name
   under the same certificate, so folding them can only make the two sides of a comparison agree —
   it can never make two distinct names equal. More than one trailing dot yields an empty label and
   is rejected.
4. **IP-literal hosts are valid origins**, IPv4 and bracketed IPv6. `debugRelease` local testing
   needs them. They skip IDN conversion entirely (`IDN.toASCII("[::1]")` throws), their
   `registrableDomain` is the literal itself — never elided — and `hasMixedScriptHost` is `false`.
5. **Both derived signals live on `SiteOrigin`**, per PRD acceptance 5, excluded from equality as
   described above.

Both PSL sections (ICANN **and** PRIVATE) are loaded. ICANN-only reports `github.io` as the
registrable domain of `site.github.io`, merging every GitHub Pages user into one displayed identity.

### Fallback on failure

Every entry point is total. `parse` returns `null`; `resolveInternal` returns `Rejected`. The
`URISyntaxException` and `IllegalArgumentException` that `java.net.URI` and `java.net.IDN` throw
(measured, both) are caught at the boundary and converted. Nothing in this package throws, per
`ADR-010`.

## File-by-file plan

### New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/SiteOrigin.kt`
The value type, its private constructor, `parse(url: String): SiteOrigin?`, hand-written
`equals`/`hashCode`/`toString`, `canonical`, `rootUrl`.

### New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/HostName.kt`
`internal` canonicalization: trailing-dot folding, IP-literal detection, `IDN.toASCII` then
ASCII-lowercase (the order the research note settled), STD3 label validation, length limits.

### New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/IdnGuard.kt`
`internal fun hasMixedScript(unicodeHost: String): Boolean` over `Character.UnicodeScript`, ignoring
`COMMON`/`INHERITED`, per label, with the Han+Hiragana+Katakana and Han+Hangul allowances.

### New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/PublicSuffixList.kt`
`internal object` loading the bundled snapshot lazily from the classpath. Implements the published
algorithm in full — exception (`!`) rules beat wildcard (`*`) rules beat literal rules by specificity
— plus the documented no-match deviation.

### New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/UrlResolver.kt`
`UrlResolution`, `UrlRejection`, `resolveInternal(origin, raw)`.

### New: `siteskin-core/src/main/resources/dev/siteskin/core/origin/public_suffix_list.dat`
The verbatim snapshot. Package-qualified path so it cannot collide with another module's resource.

### New: `docs/adr/ADR-004-origin-binding.md`
Promotes `ADR-004` out of the short-form table, as `docs/adr/README.md` invites. It is where
decisions 2–4 above and the PSL provenance (source URL, `VERSION:` line, snapshot SHA-256, manual
refresh procedure) are recorded. The README's table row becomes a link.

### Modified: `config/detekt/detekt.yml`
Adds `style > ForbiddenImport` banning `java.net.URL`. The NFR bans it because its `equals` performs
a DNS lookup — blocking and leaking. A banned import that only a review catches is not banned.

### Modified: `docs/adr/README.md`, `docs/ROADMAP.md`, `CLAUDE.md`
Link the promoted ADR; tick `CORE-001`; add a CLAUDE.md note for the JDK behaviours that will
otherwise be rediscovered.

## Tests

`siteskin-core/src/test/kotlin/dev/siteskin/core/origin/` — JUnit 4, no MockK needed (nothing here
has a collaborator worth faking).

| File | Covers | Negative control |
|---|---|---|
| `SiteOriginTest` | PRD acceptance 1–3; equality, canonical form, default-port elision, derived fields excluded from equality | drop the port from `equals` ⇒ `:8443` case must fail |
| `HostNameTest` | PRD acceptance 4; punycode, the ASCII-case finding, STD3 labels, trailing dot, IP literals | remove the post-`toASCII` lowercase ⇒ `ShOp.Example` case must fail |
| `IdnGuardTest` | PRD acceptance 5; Latin+Cyrillic flagged, Japanese not | ignore `UnicodeScript` ⇒ the `аpple.com` case must fail |
| `PublicSuffixListTest` | PRD acceptance 7; `co.uk`, `github.io`, the `*.kawasaki.jp`/`!city.kawasaki.jp` pair, no-match deviation, snapshot SHA-256 | drop exception-rule handling ⇒ `city.kawasaki.jp` must fail |
| `UrlResolverTest` | PRD acceptance 6; one test per `UrlRejection` | revert each guard in turn; recorded per-row in the tasklist |
| `OriginCorpusTest` | The five `spec/fixtures/invalid/nav-*` bodies plus `home-url-cross-origin` and `logo-subdomain`, driven through the resolver at the pointer the corpus names | rendered moot if the corpus is not actually read — asserts a non-zero fixture count |

`OriginCorpusTest` is the one that stops this ticket drifting from the contract. It reads the fixture
bodies and their `.expected.json` origins directly, asserts the resolver rejects the URL at each
expected `pointer`, and — the half that catches over-rejection — asserts it *accepts* every other URL
in the same fixture. It deliberately does not assert diagnostic codes; those are `CORE-004`'s.

Test hosts use real TLDs (`co.uk`, `github.io`, `kawasaki.jp`) wherever a PSL rule is the subject,
because `example` is measurably **not** a public suffix and would silently exercise the no-match path
instead of the rule being tested.

## Rollout / versioning

Purely additive: `:siteskin-core` currently exports three declarations and no behaviour, so nothing
can regress. No spec change, no schema change, no fixture change — `CORE-001` is written to satisfy
the existing corpus, not to extend it. The PSL snapshot is versioned by its `VERSION:` line and
pinned by SHA-256; refreshing it is a normal ticket, and stale data can only affect a rendered
string, never a comparison.

## Open questions

None outstanding. The research note's five are decided above; anything found during implementation is
recorded as a deviation on its task, per `/implement`.
