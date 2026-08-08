# CORE-001: Research
Status: RESEARCH_READY

## Question

`CORE-001` builds the type every later security control asks its question of. Before `/plan` can
commit to a trust boundary and a file list it needs four things decided:

1. What `SiteOrigin` canonicalizes, and **in what order** — the PRD flags case-folding vs punycode as
   a risk without resolving it.
2. Whether `java.net.URI` is a usable parser here, or whether the origin has to be parsed by hand.
3. Where the registrable domain (eTLD+1) comes from, given `ADR-006` renders it as a security
   affordance and a Public Suffix List is a dated data file.
4. What "resolves inside the origin" rejects, expressed as code rather than as `SPEC.md` §3 prose.

Everything below that is empirical was **measured on the build JDK**, not recalled. Four of the
measurements contradict the natural assumption, and each one is a bug this ticket would otherwise
ship.

## Origins involved

- **The serving origin.** Supplied by the browser from the URL the WebView actually loaded, never by
  the manifest. `CORE-001` is what turns it into a comparable value.
- **Candidate origins**, derived from manifest URL fields resolved against the serving origin.
  Every one is untrusted.
- **Asset origins** (`branding.logoUrl`) are same-origin only per `ADR-004`, so they are not a
  separate category — the same comparison answers them. `logo-subdomain.json` is the fixture that
  pins the part people expect to be lenient: `cdn.bloomflowers.example` is *cross-origin* to
  `bloomflowers.example`.

There is exactly one origin comparison in the system, and it is string equality on a canonical form.
Everything else is canonicalization.

## Manifest-controlled surface

If this ships as scoped, a website can influence:

- the **path, query and fragment** of any URL it publishes, within its own origin;
- whether a given item survives, by publishing a URL that fails origin binding (self-harm only —
  the item drops, per `SS-E-ORIGIN-MISMATCH`'s `drop-item` disposition);
- nothing about how its own origin is computed. The serving origin is an input to this module from
  the browser, never a field in the manifest.

A manifest cannot influence the registrable domain shown in chrome, the homograph flag, the scheme
allow-list, or the outcome of a comparison. There is no manifest field in `1.0` that reaches any of
them, and `SPEC.md` §3's table is exhaustive by construction: every URL-bearing field is listed with
its rule, and there is no opt-out.

## Browser-owned remainder

| Stays browser-owned | Enforced by |
|---|---|
| The serving origin itself | It is a parameter to `SiteOrigin.parse`, sourced from the WebView's committed URL |
| eTLD+1 shown in the top bar | `ADR-006`; computed here, rendered by `SKIN-002`, never suppressible |
| The homograph signal | Computed here from the *pre-punycode* host; `SKIN-002` decides presentation |
| Scheme allow-list (`http`/`https` at this layer) | `SiteOrigin.parse` returns `null` for anything else |
| Rejection over normalization | `SPEC.md` §3 — a traversal that escapes is refused, not silently collapsed |

The last row is the one that matters most and is the easiest to lose. Normalizing `/../../evil` to
`/evil` and then comparing origins would pass every origin test in the corpus while quietly
resolving a URL the site never wrote.

## Relevant code

| Path | Why it matters |
|---|---|
| `siteskin-core/src/main/kotlin/dev/siteskin/core/SiteSkin.kt` | Today's whole of core: `SiteSkinSchema`, `SiteSkinLimits`, `ManifestSource`. `CORE-001` adds the first `origin/` package beside it |
| `spec/SPEC.md` §3 | Normative origin binding — the five things that MUST be rejected |
| `spec/SPEC.md` §12 | Canonical result: every URL absolute, `origin` and `site.homeUrl` always present |
| `spec/fixtures/invalid/nav-*.json` | Five fixtures, one per §3 rejection clause |
| `spec/fixtures/invalid/logo-subdomain.json` | Subdomains are cross-origin — the fixture most likely to be "fixed" into a bug |
| `spec/fixtures/invalid/home-url-cross-origin.json` | The drop is scoped to the field; navigation survives |
| `siteskin-core/build.gradle.kts` | `assertNoAndroidDependencies`; `jvmTarget = 21`; corpus wired as a test input |
| `docs/adr/README.md` § ADR-004 | Origin binding, in short form. No separate file — see Risks |

## Prior art

- **`ADR-004`** — `origin = scheme + host + port`; subdomains not trusted; cache never applied
  cross-origin. Recorded in `docs/adr/README.md`, not its own file.
- **`ADR-006`** — requires the registrable domain to be renderable, which is the *only* reason
  eTLD+1 is in this ticket at all. Origin *comparison* never needs it.
- **`ADR-010`** — nothing here may throw into a browsing path. Parse failure returns `null`.
- **`SPEC.md` §3** — the five MUST-reject clauses, each with a fixture.
- **`SPEC.md` §7.1** — `match` patterns are path-only and their authority check is *schema*-layer,
  not security-layer. `CORE-001` does not own it; `match-pattern-with-authority.json` expects
  `SS-E-SCHEMA-INVALID`. Worth stating because the fixture name reads like an origin case.
- **`CORE-004`** is the consumer that turns a rejection into `SS-E-ORIGIN-MISMATCH` /
  `SS-E-ASSET-CROSS-ORIGIN`. `CORE-001` emits **no diagnostic codes** — it returns a typed
  rejection reason and lets the layer that owns the disposition name the code.

## Measured behaviour of the JDK primitives

Run against the build toolchain (JDK 25) with `java Probe.java`. Every row here is a design input,
and the starred ones are where the natural assumption is wrong.

| Input | Result | Consequence |
|---|---|---|
| ★ `IDN.toASCII("ShOp.Example")` | `ShOp.Example` | **Does not lowercase ASCII.** Punycode conversion alone does not canonicalize case |
| `IDN.toASCII("MÜNCHEN.example")` | `xn--mnchen-3ya.example` | Non-ASCII labels *are* case-folded, by nameprep |
| `IDN.toASCII("-bad.example")` | `-bad.example` | No STD3 rules by default; leading-hyphen labels pass. Needs an explicit label check |
| `IDN.toASCII("shop..example")` | throws `IllegalArgumentException` | Must be caught — `ADR-010` forbids throwing into a browsing path |
| ★ `URI("https://münchen.example/x").getHost()` | `null` (authority is non-null) | **`getHost()` is unusable on IDN input.** It applies RFC 2396 host rules and gives up |
| `URI("https://shop_underscore.example/x").getHost()` | `null` | Same trap, second cause |
| `URI("https://a.example@evil.example/").getHost()` | `evil.example` | Userinfo is parsed off correctly — a *string* comparison is what `nav-userinfo-authority` punishes |
| `URI("HTTPS://Shop.Example/X").getScheme()` | `HTTPS` | Scheme is not lowercased either |
| ★ `base.resolve("//evil.example/catalog")` | `https://evil.example/catalog` | **Protocol-relative silently inherits the scheme** and lands off-origin |
| ★ `URI("https://h/../../evil").normalize()` | `https://h/../../evil` | **`normalize()` does not collapse a leading `..`** — it leaves it in place |
| `URI("https://h/a/../../b").normalize()` | `https://h/../b` | Same: the escape survives normalization as a residual `..` segment |
| `base.resolve("https:evil")` | `https:evil` (opaque, no authority) | An absolute-but-opaque URI resolves to itself |
| `URI("https:/evil.example/x")` | scheme `https`, authority `null` | Single-slash form: absolute, no authority |
| `new URI("\\\\evil.example/x")` | throws `URISyntaxException` | Backslash forms throw rather than parse |
| `URI("https://[::1]:8443/x").getHost()` | `[::1]` | IPv6 literals keep their brackets; `IDN.toASCII` would reject them |
| `URI("https://shop.example./x").getHost()` | `shop.example.` | Trailing root dot survives parsing |
| `Character.UnicodeScript.of(0x430)` | `CYRILLIC` | Available since Android API 24; minSdk is 26 |

Two of these settle open questions outright:

- **The PRD's "case-folding before or after punycode?" risk has an answer that is neither.** Both
  orders agree on `MÜNCHEN.example`, so a test using only that input proves nothing. The real finding
  is that `toASCII` *does not case-fold ASCII at all*, so lowercasing is not an ordering choice but a
  mandatory separate step. Doing it **after** `toASCII` is the safe order: the output is pure ASCII
  by then, so `lowercase()` is locale-independent and the Turkish dotted-İ divergence cannot arise.
- **`normalize()` is a usable escape *detector*, not an escape *fixer*.** Because it leaves a
  residual leading `..`, "normalize, then reject if any segment is `..`" is exactly the
  reject-don't-normalize rule `SPEC.md` §3 demands, using the JDK rather than fighting it.

## The Public Suffix List

Needed **only** for `ADR-006`'s rendered domain. It is on no comparison path, which is the fact that
makes the staleness risk tolerable: a stale list can only make the top bar show the wrong number of
labels, never make two origins compare equal.

Measured: the published list is **332,855 bytes / 16,409 lines**, `VERSION: 2026-07-25_14-20-03_UTC`.
Stripping comments and blanks gives 141,880 bytes / 10,239 rules; gzip of that is 43,460 bytes. It
carries 8 exception (`!`) rules and 281 wildcard rules, so the matching algorithm has to implement
both — a plain suffix-set lookup gets `city.kawasaki.jp` wrong.

Both criteria the PRD names are in the list, in *different sections*: `co.uk` is ICANN,
`github.io` is PRIVATE. A bundle that keeps only the ICANN section reports `github.io` as the
registrable domain of `site.github.io`, which is the impersonation case `ADR-006` exists to prevent
— `a.github.io` and `b.github.io` are different parties. **Both sections are required.**

Options, for `/plan` to choose between:

| Option | Cost | Risk |
|---|---|---|
| Bundle verbatim | ~333 KB resource, ~46 KB in a compressed APK | Staleness; MPL-2.0 notice must survive |
| Bundle comment-stripped | ~142 KB, ~43 KB compressed | Same, plus the MPL header and the `VERSION:` line are what got stripped |
| Curated minimal set | ~1 KB | Silently wrong for every suffix nobody thought of, with no signal that it is wrong |

The PRD's non-goal forbids *building an updater*, not bundling the list. Note that "fail toward
showing more of the host" is the PRD's stated preference but is **not** what the PSL algorithm's
default rule does: with no matching rule the prevailing rule is `*`, which yields the last two
labels — i.e. showing *less*. That divergence needs an explicit decision in `/plan`, not an
assumption.

## Risks

- **Origin comparison via string equality on a non-canonical form.** → The plan must make
  `SiteOrigin` constructible only through `parse`, so an uncanonicalized instance cannot exist. Same
  argument as `SiteSkinConfiguration`; same enforcement.
- **`java.net.URI.getHost()` returning `null` on valid IDN input.** → The plan must parse the
  authority itself rather than trusting `getHost()`, and must have a test whose host is non-ASCII.
  Using `getHost()` passes every ASCII test in the corpus and fails in production on the first
  German customer.
- **`java.net.URL` anywhere.** Its `equals` does a DNS lookup — it blocks and it leaks. The NFR bans
  it; the plan should add a detekt `ForbiddenImport` rule so the ban is enforced rather than
  remembered.
- **Trailing-dot and IPv6 hosts** are neither rejected nor canonicalized by anything today. The plan
  must decide both; each needs a fixture in both directions.
- **A stale PSL.** Bounded to display, per the section above. Must be recorded, with the refresh
  procedure, next to the data file.
- **`ADR-004` has no file.** `docs/adr/README.md` promises "promote one to its own file if it ever
  needs re-arguing", and `CORE-001` is the ticket that turns that ADR into code — the point at which
  the trailing-dot, IPv6, PSL-section and fail-direction decisions get made. Those are amendments to
  `ADR-004`, and the short-form table has nowhere to put them.
- **Detekt's 40-line method limit** against a PSL matcher and a URL resolver. Neither should need
  more, but the plan should keep them as small named functions rather than discovering the limit at
  `/pre-commit`.

## Open questions

Carried into `/plan` as explicit unknowns rather than settled here:

1. Bundle the PSL verbatim, comment-stripped, or curated? (Recommendation above leans verbatim: it
   is diffable against upstream, and it is the only option that keeps the MPL notice and the
   `VERSION:` line in the artifact itself.)
2. When no PSL rule matches, follow the specified `*` default (last two labels) or the PRD's
   "show more" preference (the whole host)? These genuinely disagree.
3. Trailing root dot: strip one, or reject the host?
4. IP-literal hosts: allowed as origins, or refused? They cannot have a registrable domain or a
   homograph flag, so they need a defined answer for both.
5. Does the homograph flag belong on `SiteOrigin` (PRD acceptance 5 says "exposed on the type"), or
   beside it? Putting it on the type means the type carries a field no comparison may read — which
   needs to be stated, or someone will read it.
