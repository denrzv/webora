package dev.siteskin.core.origin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * `SiteOrigin` is the type every later security control asks its question of. Two properties carry
 * the weight: a `SiteOrigin` that exists is canonical, and equality is exactly
 * `(scheme, host, port)`.
 */
class SiteOriginTest {

    private fun origin(url: String): SiteOrigin? = SiteOrigin.parse(url)

    // --- scheme allow-list --------------------------------------------------------------------

    @Test
    fun onlyHttpAndHttpsAreOrigins() {
        assertTrue(origin("https://shop.example") != null)
        assertTrue(origin("http://shop.example") != null)

        // Allow-list, not deny-list: these are the ADR-007 cases, but anything unlisted fails too.
        listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://media/external/images/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "data:text/html,<script>alert(1)</script>",
            "mailto:someone@shop.example",
            "tel:+10000000000",
            "geo:0,0",
            "ftp://shop.example",
            "wss://shop.example",
        ).forEach { assertNull("expected null for $it", origin(it)) }
    }

    @Test
    fun schemeComparisonIsCaseInsensitive() {
        assertEquals(origin("https://shop.example"), origin("HTTPS://Shop.Example"))
        assertEquals("https", origin("HTTPS://Shop.Example")?.scheme)
    }

    // --- malformed and authority-less ----------------------------------------------------------

    @Test
    fun urlsWithoutAnAuthorityAreNotOrigins() {
        // Measured: URI("https:evil") is absolute and opaque -- scheme https, authority null.
        assertNull(origin("https:evil"))
        // Measured: URI("https:/evil.example/x") parses as scheme https with a null authority and
        // the whole thing as a path.
        assertNull(origin("https:/evil.example/x"))
        assertNull(origin("https://"))
        assertNull(origin("//evil.example/x"))
        assertNull(origin("/cart"))
        assertNull(origin(""))
        assertNull(origin("   "))
        assertNull(origin("not a url at all"))
        assertNull(origin("\\\\evil.example/x"))
    }

    /**
     * The authority carries userinfo, so the serving origin's host can appear verbatim in a URL
     * whose real host is somewhere else. `spec/fixtures/invalid/nav-userinfo-authority.json` is the
     * corpus case; rejecting it at `parse` means no caller can be handed the wrong origin to
     * compare against in the first place.
     */
    @Test
    fun userinfoInTheAuthorityIsRejected() {
        assertNull(origin("https://shop.example@evil.example/catalog"))
        assertNull(origin("https://user:pass@evil.example/"))
        assertNull(origin("https://@evil.example/"))
    }

    @Test
    fun malformedPortsAreRejected() {
        assertNull(origin("https://shop.example:0"))
        assertNull(origin("https://shop.example:65536"))
        assertNull(origin("https://shop.example:-1"))
        assertNull(origin("https://shop.example:abc"))
        assertNull(origin("https://shop.example:"))
        assertEquals(65535, origin("https://shop.example:65535")?.port)
    }

    // --- equality -----------------------------------------------------------------------------

    /** PRD acceptance 2. Every spelling of one origin must compare equal, and hash alike. */
    @Test
    fun equivalentSpellingsAreOneOrigin() {
        val spellings = listOf(
            "https://shop.example",
            "https://shop.example:443",
            "https://SHOP.example",
            "https://ShOp.Example",
            "https://shop.example.",
            "https://shop.example/",
            "https://shop.example/cart?q=1#frag",
        ).map { requireNotNull(origin(it)) { "failed to parse $it" } }

        assertEquals(1, spellings.toSet().size)
        assertEquals(1, spellings.map { it.hashCode() }.toSet().size)
    }

    /** PRD acceptance 3. Subdomain, scheme and port each make a different origin. */
    @Test
    fun distinctOriginsNeverCompareEqual() {
        val base = requireNotNull(origin("https://shop.example"))

        listOf(
            "https://admin.shop.example",
            "https://shop.example.org",
            "https://notshop.example",
            "http://shop.example",
            "https://shop.example:8443",
            "http://shop.example:443",
        ).forEach { other ->
            assertNotEquals("$other must not equal ${base.canonical}", base, origin(other))
        }
    }

    @Test
    fun defaultPortsAreNormalizedPerScheme() {
        assertEquals(443, origin("https://shop.example")?.port)
        assertEquals(443, origin("https://shop.example:443")?.port)
        assertEquals(80, origin("http://shop.example")?.port)
        assertEquals(80, origin("http://shop.example:80")?.port)

        // 443 is not http's default, so it stays visible and stays a distinct origin.
        assertNotEquals(origin("http://shop.example"), origin("http://shop.example:443"))
    }

    // --- canonical form -------------------------------------------------------------------------

    /**
     * The corpus's `.expected.json` files carry `"origin": "https://bloomflowers.example"`. That
     * string is what `canonical` must produce, byte for byte -- `OriginCorpusTest` compares against
     * it directly, and `NET-002` will use it as half of a cache key.
     */
    @Test
    fun canonicalFormElidesTheDefaultPort() {
        assertEquals("https://bloomflowers.example", origin("https://bloomflowers.example/")?.canonical)
        assertEquals("https://shop.example", origin("https://SHOP.example:443/cart")?.canonical)
        assertEquals("http://shop.example", origin("http://shop.example:80")?.canonical)
        assertEquals("https://shop.example:8443", origin("https://shop.example:8443")?.canonical)
        assertEquals("http://shop.example:8080", origin("http://shop.example:8080")?.canonical)
    }

    @Test
    fun rootUrlIsTheCanonicalFormWithATrailingSlash() {
        assertEquals("https://bloomflowers.example/", origin("https://bloomflowers.example")?.rootUrl)
        assertEquals("https://shop.example:8443/", origin("https://shop.example:8443/deep/path")?.rootUrl)
    }

    @Test
    fun idnHostsCanonicalizeToPunycode() {
        // PRD acceptance 4.
        assertEquals(origin("https://xn--mnchen-3ya.example"), origin("https://münchen.example"))
        assertEquals("https://xn--mnchen-3ya.example", origin("https://MÜNCHEN.example")?.canonical)
    }

    @Test
    fun ipLiteralsAreOrigins() {
        assertEquals("https://1.2.3.4", origin("https://1.2.3.4/x")?.canonical)
        assertEquals("https://[::1]:8443", origin("https://[::1]:8443/x")?.canonical)
        assertNotEquals(origin("https://1.2.3.4"), origin("https://1.2.3.5"))
    }

    // --- the construction rule -------------------------------------------------------------------

    /**
     * `conventions.md`: a trusted domain object is constructible only through its validator, so
     * that "this instance is canonical" is a compile-time guarantee rather than a review comment.
     * Asserted structurally because the failure mode is someone adding a convenience constructor
     * later — at which point every comparison in the codebase silently weakens, with no test
     * anywhere else going red.
     *
     * `copy()` is checked for by name because a `data class` would synthesize one, and a
     * synthesized `copy()` is a public constructor wearing a hat.
     *
     * Synthetic constructors are excluded. Kotlin emits a package-private bridge taking a trailing
     * `DefaultConstructorMarker` so the companion can reach the private one; it is flagged
     * `ACC_SYNTHETIC`, is not callable from Kotlin or from ordinary Java, and is not API. Asserting
     * over it would fail on a compiler implementation detail rather than on a real leak.
     */
    @Test
    fun siteOriginIsConstructibleOnlyThroughParse() {
        val constructors = SiteOrigin::class.java.declaredConstructors.filterNot { it.isSynthetic }
        assertTrue("SiteOrigin must declare a non-synthetic constructor", constructors.isNotEmpty())
        constructors.forEach { constructor ->
            assertTrue(
                "constructor ${constructor.parameterTypes.toList()} must be private",
                Modifier.isPrivate(constructor.modifiers),
            )
        }

        assertTrue(
            "SiteOrigin must not expose copy() — it would bypass parse",
            SiteOrigin::class.java.methods.none { it.name == "copy" },
        )
    }

    /**
     * The homograph flag is a display signal, not a comparison input. Two origins that differ only
     * in it must still compare equal — which they always will, since it is a function of the host,
     * but asserting it pins the intent: nobody may later "improve" equality by folding it in.
     */
    @Test
    fun theHomographFlagIsExposedButNeverPartOfEquality() {
        val homographic = requireNotNull(origin("https://аpple.com"))
        val plain = requireNotNull(origin("https://apple.com"))

        assertTrue(homographic.hasMixedScriptHost)
        assertTrue(!plain.hasMixedScriptHost)

        // Different origins for an independent reason: the hosts genuinely differ.
        assertNotEquals(homographic, plain)

        // Spelling must not change the flag, since spelling does not change the origin.
        assertEquals(homographic, origin("https://xn--pple-43d.com"))
        assertEquals(homographic.hasMixedScriptHost, origin("https://xn--pple-43d.com")?.hasMixedScriptHost)
    }

    @Test
    fun toStringIsTheCanonicalForm() {
        // Logs and assertion messages print origins constantly; the canonical form is the one
        // spelling that cannot be mistaken for a different origin.
        assertEquals("https://shop.example:8443", origin("https://shop.example:8443/x").toString())
    }
}
