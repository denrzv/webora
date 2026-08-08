# CORE-001: Tasklist
Status: TASKLIST_READY

References:
- PRD: `docs/prd/CORE-001.prd.md`
- Research: `docs/research/CORE-001.md`
- Plan: `docs/plan/CORE-001.md`

Security-relevant tasks carry a **Negative control** line. Per `CLAUDE.md`, the result is recorded
here after the control is actually run — "revert the protection, confirm the test fails, restore it".
A control that was not run is left blank, never assumed.

## Tasks

- [x] TASK-1: Host canonicalization
  - New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/HostName.kt`
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/origin/HostNameTest.kt`
  - Acceptance: `münchen.example` and `MÜNCHEN.example` both canonicalize to
    `xn--mnchen-3ya.example`; `ShOp.Example` to `shop.example`; `shop.example.` to `shop.example`;
    `shop..example`, `shop...example`, `-bad.example`, `bad-.example`, a 64-byte label and a
    254-byte name are all rejected; `1.2.3.4` and `[::1]` pass through untouched and are reported as
    IP literals.
  - Tests: `HostNameTest`
  - Negative control: remove the ASCII-lowercase step that follows `IDN.toASCII` — the
    `ShOp.Example` and `SHOP.EXAMPLE` cases must fail. (Result: **ran, fails as required.**
    `asciiHostsAreLowercased` and `idnCaseFoldingAgreesWithPunycodeConversion` both failed;
    restored, both pass. Worth recording *how* they failed: `expected:<shop.example> but
    was:<null>` rather than `was:<ShOp.Example>`. Without the lowercase step the uppercase host
    stops matching the lowercase-only STD3 label grammar, so it is rejected outright instead of
    canonicalizing wrongly. The two guards are independent and the test detects the removal either
    way, but a reader expecting a comparison failure should know why the message says `null`.)
  - Deviation: needed a scoped `config/detekt/detekt.yml` change — `SwallowedException`'s
    `ignoredExceptionTypes` now also lists `IllegalArgumentException` and `URISyntaxException`.
    `IDN.toASCII` and `java.net.URI` signal "malformed" by throwing, and `ADR-010` requires that
    become a typed `null`/rejection rather than propagating into a navigation. Listing the two
    types keeps the rule active for every other exception. Planned for TASK-7; pulled forward
    because TASK-1 is where it first blocks.

- [x] TASK-2: `SiteOrigin` value type
  - New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/SiteOrigin.kt`
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/origin/SiteOriginTest.kt`
  - Acceptance: PRD 1–4. `parse` returns `null` for `javascript:`, `file:`, `content:`, `intent:`,
    `data:`, `mailto:`, an opaque `https:evil`, an authority-less `https:/x`, a userinfo authority,
    and anything malformed. `https://shop.example` == `https://shop.example:443` ==
    `https://SHOP.example` == `https://shop.example.`; each is `!=` `https://admin.shop.example`,
    `http://shop.example` and `https://shop.example:8443`. `canonical` elides the default port and
    matches the corpus's `origin` field; `rootUrl` appends `/`. No public constructor and no `copy`.
  - Tests: `SiteOriginTest`
  - Negative control: drop `port` from `equals`/`hashCode` — the `:8443` inequality case must fail.
    (Result: **ran, fails as required.** `distinctOriginsNeverCompareEqual` and
    `defaultPortsAreNormalizedPerScheme` both failed; restored, both pass.)
  - Deviation: `siteOriginIsConstructibleOnlyThroughParse` filters out **synthetic** constructors.
    Kotlin emits a package-private bridge taking a trailing `DefaultConstructorMarker` so the
    companion can reach the private constructor. It is `ACC_SYNTHETIC`, callable from neither
    Kotlin nor ordinary Java, and asserting over it fails on a compiler detail rather than on a
    real API leak. Found by writing the test first — it went red on the real class.

- [x] TASK-3: Mixed-script homograph guard
  - New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/IdnGuard.kt`
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/origin/IdnGuardTest.kt`
  - Modified: `SiteOrigin.kt` — expose `hasMixedScriptHost`, computed from the pre-punycode host
  - Acceptance: PRD 5. `аpple.com` (Cyrillic а) is flagged; `apple.com`, `münchen.example`,
    `xn--mnchen-3ya.example` and `1.2.3.4` are not; a Japanese host mixing Han, Hiragana and
    Katakana in one label is not flagged; digits and hyphens (`COMMON`) never flag on their own.
    The flag is absent from `equals`/`hashCode`.
  - Tests: `IdnGuardTest`, plus an equality assertion in `SiteOriginTest`
  - Negative control: return a constant `false` — the `аpple.com` case must fail. (Result: **ran,
    fails as required.** Four tests failed — `latinAndCyrillicInOneLabelIsFlagged`,
    `punycodeAndUnicodeSpellingsAgree`, `scriptsAreComparedWithinALabelNotAcrossTheHost` and
    `SiteOriginTest.theHomographFlagIsExposedButNeverPartOfEquality`; restored, all pass.)
  - Deviation from the plan, and a correction to it: the flag is computed from the **canonical**
    host via `IDN.toUnicode`, not from the pre-punycode input the plan named. Deriving it from the
    raw input makes it a function of *spelling* rather than of the origin, so
    `https://аpple.com` and `https://xn--pple-43d.com` — which compare equal, being the same
    origin — would have carried contradicting flags. Decoding the canonical form is the only way
    the flag stays a function of `host`. `punycodeAndUnicodeSpellingsAgree` pins it.
  - Deviation: the guard detects *mixed*-script labels only. An all-Cyrillic whole-script
    confusable such as `раураӏ.com` uses one script and is **not** flagged; catching those needs a
    confusable mapping rather than a script partition. Recorded in the KDoc and left to
    `HARDEN-001`, which owns the adversarial corpus that would justify carrying one. The PRD asks
    for mixed-script and this is exactly that.

- [ ] TASK-4: Public Suffix List and registrable domain
  - New: `siteskin-core/src/main/resources/dev/siteskin/core/origin/public_suffix_list.dat`
  - New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/PublicSuffixList.kt`
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/origin/PublicSuffixListTest.kt`
  - Modified: `SiteOrigin.kt` — expose `registrableDomain`
  - Acceptance: PRD 7. `www.example.co.uk` → `example.co.uk`; `site.github.io` →
    `site.github.io` (PRIVATE section, so an ICANN-only load fails this); `a.b.city.kawasaki.jp` →
    `city.kawasaki.jp` via the `!` exception, and `a.b.other.kawasaki.jp` → `b.other.kawasaki.jp`
    via the `*` wildcard; `shop.bloomflowers.example` → the full host, since `example` matches no
    rule; IP literals return themselves. The bundled snapshot's SHA-256 and `VERSION:` line are
    asserted against the constants recorded in `ADR-004`.
  - Tests: `PublicSuffixListTest`
  - Negative control: ignore `!` exception rules — the `city.kawasaki.jp` case must fail.
    (Result: )

- [ ] TASK-5: `UrlResolver` and origin binding
  - New: `siteskin-core/src/main/kotlin/dev/siteskin/core/origin/UrlResolver.kt`
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/origin/UrlResolverTest.kt`
  - Acceptance: PRD 6. `/cart` → `https://…/cart`; `catalog`, `?q=1` and `#f` resolve inside the
    origin; each of `//evil.example/x`, `/../../evil`, `/a/../../b`,
    `https://shop.example@evil.example/`, `https://shop.example:8443/x`,
    `https://admin.shop.example/x`, `http://shop.example/x`, `javascript:alert(1)`, `https:evil`
    and `\\evil.example/x` returns `Rejected` with the reason the plan's table names. Nothing throws
    for any input, including the empty string.
  - Tests: `UrlResolverTest`, one case per `UrlRejection`
  - Negative control: one per guard, reverted in turn — (a) resolve protocol-relative instead of
    rejecting it, (b) skip the residual-`..` check, (c) drop the userinfo check, (d) compare hosts
    with `endsWith` instead of `==`. Each must break its own test and no other. (Result: )

- [ ] TASK-6: Drive the conformance corpus through the resolver
  - New: `siteskin-core/src/test/kotlin/dev/siteskin/core/origin/OriginCorpusTest.kt`
  - Acceptance: reads `spec/fixtures/invalid/{nav-cross-origin,nav-port-change,
    nav-protocol-relative,nav-traversal-escape,nav-userinfo-authority,home-url-cross-origin,
    logo-subdomain}` with their `.expected.json` origins; asserts the resolver rejects the URL at
    each expected `pointer` and accepts every other URL in the same fixture. Asserts a non-zero
    fixture count so a path typo fails loudly instead of vacuously passing. Asserts **no** `SS-*`
    code — those are `CORE-004`'s.
  - Tests: `OriginCorpusTest`
  - Negative control: point the reader at a directory that does not exist — the count assertion must
    fail rather than the suite passing on an empty list. (Result: )

- [ ] TASK-7: Close out
  - New: `docs/adr/ADR-004-origin-binding.md`
  - Modified: `docs/adr/README.md`, `docs/ROADMAP.md`, `CLAUDE.md`, `config/detekt/detekt.yml`
  - Acceptance: `ADR-004` is promoted to a file recording the trailing-dot, IP-literal, PSL-section
    and no-match-deviation decisions plus the snapshot provenance and manual refresh procedure; the
    README table links it; detekt bans `java.net.URL` and the ban is verified with a negative
    control; `CLAUDE.md` gains an origin-model section covering the measured JDK behaviours;
    `ROADMAP.md` ticks `CORE-001`.
  - Tests: `bash scripts/pre-commit-check.sh`
  - Negative control: add a file importing `java.net.URL`, confirm `./gradlew detekt` fails, delete
    it. (Result: )
