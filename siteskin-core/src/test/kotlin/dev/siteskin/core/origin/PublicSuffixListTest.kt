package dev.siteskin.core.origin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.IDN
import java.security.MessageDigest

/**
 * The registrable domain is what `ADR-006` renders in chrome that no manifest can suppress. It is
 * on **no** comparison path — origin binding compares full canonical hosts — which is what makes a
 * dated data file tolerable here: a stale list can render the wrong number of labels, never make
 * two origins compare equal.
 */
class PublicSuffixListTest {

    private fun registrable(host: String): String = PublicSuffixList.registrableDomain(host)

    private fun punycode(host: String): String = IDN.toASCII(host)

    // --- the ordinary cases --------------------------------------------------------------------

    @Test
    fun singleLabelSuffixesYieldTheSecondLevelDomain() {
        assertEquals("google.com", registrable("www.google.com"))
        assertEquals("google.com", registrable("a.b.c.google.com"))
        assertEquals("google.com", registrable("google.com"))
    }

    /** PRD acceptance 7, first case. A plain "last two labels" rule gets this wrong. */
    @Test
    fun multiLabelIcannSuffixesAreRespected() {
        assertEquals("example.co.uk", registrable("www.example.co.uk"))
        assertEquals("example.co.uk", registrable("example.co.uk"))
        assertEquals("bbc.co.uk", registrable("news.bbc.co.uk"))
    }

    /**
     * PRD acceptance 7, second case — and the reason **both** PSL sections are loaded.
     * `github.io` lives in the PRIVATE section, so an ICANN-only load reports `github.io` as the
     * registrable domain of every GitHub Pages site, merging every user into one displayed
     * identity. That is precisely the collision `ADR-006` exists to prevent.
     */
    @Test
    fun privateSectionSuffixesAreRespected() {
        assertEquals("site.github.io", registrable("site.github.io"))
        assertEquals("site.github.io", registrable("www.site.github.io"))
        assertTrue(
            "two Pages users must not share a registrable domain",
            registrable("alice.github.io") != registrable("bob.github.io"),
        )
    }

    // --- wildcard and exception rules ------------------------------------------------------------

    /**
     * `*.kawasaki.jp` with `!city.kawasaki.jp` is the pair that punishes a plain suffix-set lookup.
     * The exception rule must win over the wildcard regardless of which is longer.
     */
    @Test
    fun exceptionRulesBeatWildcardRules() {
        assertEquals("city.kawasaki.jp", registrable("a.b.city.kawasaki.jp"))
        assertEquals("city.kawasaki.jp", registrable("city.kawasaki.jp"))
    }

    @Test
    fun wildcardRulesConsumeExactlyOneLabel() {
        assertEquals("b.other.kawasaki.jp", registrable("a.b.other.kawasaki.jp"))
        assertEquals("b.other.kawasaki.jp", registrable("b.other.kawasaki.jp"))
    }

    // --- IDN rules --------------------------------------------------------------------------------

    /**
     * 459 of the bundled rules are written in Unicode, but every host reaching this object has
     * already been canonicalized to punycode. Without converting the rules on load they simply
     * never match, and every internationalized suffix silently falls through to the no-match path
     * — which fails open and looks like it works.
     */
    @Test
    fun unicodeRulesMatchPunycodeHosts() {
        // 公司.cn is a rule; a.例子.公司.cn must lose its leading label.
        val host = punycode("a.例子.公司.cn")
        assertEquals(punycode("例子.公司.cn"), registrable(host))
        assertTrue("the test host must actually be punycode", host.contains("xn--"))
    }

    // --- the documented deviation ------------------------------------------------------------------

    /**
     * The published algorithm says that when no rule matches, the prevailing rule is `*` — which
     * would return the last two labels. This implementation returns the **whole host** instead, and
     * `ADR-004` records why: for an unknown suffix the default rule renders `evil.co.newtld` and
     * `bank.co.newtld` identically as `co.newtld`, which is the impersonation `ADR-006` exists to
     * prevent. Failing toward showing *more* of the host is the safe direction for a value whose
     * job is anti-impersonation rather than cookie scoping.
     */
    @Test
    fun anUnknownSuffixYieldsTheWholeHost() {
        // Measured: `example` is not in the Public Suffix List.
        assertEquals("shop.bloomflowers.example", registrable("shop.bloomflowers.example"))
        assertEquals("bloomflowers.example", registrable("bloomflowers.example"))
        assertEquals("a.b.c.nosuchtld", registrable("a.b.c.nosuchtld"))
    }

    @Test
    fun aHostThatIsItselfAPublicSuffixYieldsTheWholeHost() {
        // There is no registrable domain here; showing the whole host is the safe direction.
        assertEquals("co.uk", registrable("co.uk"))
        assertEquals("com", registrable("com"))
    }

    @Test
    fun ipLiteralsAreReturnedUnchanged() {
        assertEquals("1.2.3.4", registrable("1.2.3.4"))
        assertEquals("[::1]", registrable("[::1]"))
    }

    @Test
    fun degenerateInputIsReturnedUnchangedRatherThanThrowing() {
        assertEquals("", registrable(""))
        assertEquals("localhost", registrable("localhost"))
    }

    // --- provenance -------------------------------------------------------------------------------

    /**
     * The snapshot is pinned so that refreshing it is a deliberate, reviewable act rather than
     * something that drifts in with an unrelated change. `ADR-004` carries the same two constants
     * and the manual refresh procedure.
     */
    @Test
    fun theBundledSnapshotMatchesItsRecordedProvenance() {
        val bytes = requireNotNull(
            PublicSuffixList::class.java.getResourceAsStream(PublicSuffixList.RESOURCE)?.use { it.readBytes() },
        ) { "bundled Public Suffix List is missing from the classpath" }

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        assertEquals(PublicSuffixList.SNAPSHOT_SHA256, sha256)
        assertTrue(
            "snapshot must carry its upstream VERSION line",
            bytes.decodeToString().contains("// VERSION: ${PublicSuffixList.SNAPSHOT_VERSION}"),
        )
    }

    @Test
    fun bothSectionsOfTheListAreLoaded() {
        val text = requireNotNull(
            PublicSuffixList::class.java.getResourceAsStream(PublicSuffixList.RESOURCE)?.use { it.readBytes() },
        ).decodeToString()

        assertTrue(text.contains("===BEGIN ICANN DOMAINS==="))
        assertTrue(text.contains("===BEGIN PRIVATE DOMAINS==="))
    }
}
