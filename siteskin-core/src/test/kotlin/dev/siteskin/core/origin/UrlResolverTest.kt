package dev.siteskin.core.origin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SPEC.md` §3: a URL "resolves inside the origin" only if, after resolution against the origin
 * root, its canonical origin is identical to the serving origin — and each of five named forms MUST
 * be **rejected**, never silently normalized away.
 *
 * Every rejection here is `drop-item` at the layer above, so a hostile URL costs a site one
 * navigation item and never its whole integration.
 */
class UrlResolverTest {

    private val origin = requireNotNull(SiteOrigin.parse("https://bloomflowers.example"))

    private fun resolve(raw: String): UrlResolution = UrlResolver.resolveInternal(origin, raw)

    private fun resolved(raw: String): String {
        val result = resolve(raw)
        assertTrue("expected $raw to resolve, got $result", result is UrlResolution.Resolved)
        return (result as UrlResolution.Resolved).url
    }

    private fun rejection(raw: String): UrlRejection {
        val result = resolve(raw)
        assertTrue("expected $raw to be rejected, got $result", result is UrlResolution.Rejected)
        return (result as UrlResolution.Rejected).reason
    }

    // --- what must resolve -----------------------------------------------------------------------

    @Test
    fun originRelativePathsResolveToAbsoluteUrls() {
        assertEquals("https://bloomflowers.example/cart", resolved("/cart"))
        assertEquals("https://bloomflowers.example/", resolved("/"))
        assertEquals("https://bloomflowers.example/catalog", resolved("catalog"))
        assertEquals("https://bloomflowers.example/catalog/spring", resolved("/catalog/spring"))
    }

    @Test
    fun queryAndFragmentSurviveResolution() {
        assertEquals("https://bloomflowers.example/?q=1", resolved("?q=1"))
        assertEquals("https://bloomflowers.example/#top", resolved("#top"))
        assertEquals("https://bloomflowers.example/cart?a=1&b=2#x", resolved("/cart?a=1&b=2#x"))
    }

    @Test
    fun sameOriginAbsoluteUrlsResolve() {
        assertEquals("https://bloomflowers.example/catalog", resolved("https://bloomflowers.example/catalog"))
        // The default port is the same origin written differently.
        assertEquals("https://bloomflowers.example/catalog", resolved("https://bloomflowers.example:443/catalog"))
        // ...and so is a differently-cased host.
        assertEquals("https://bloomflowers.example/X", resolved("https://BLOOMFLOWERS.example/X"))
    }

    /**
     * Traversal that stays *inside* the origin is ordinary path normalization, not an escape.
     * `SPEC.md` §3 forbids normalizing an escape away; it does not forbid resolving a relative
     * reference correctly.
     */
    @Test
    fun traversalWithinTheOriginIsNormalizedNotRejected() {
        assertEquals("https://bloomflowers.example/b", resolved("/a/../b"))
        assertEquals("https://bloomflowers.example/a/c", resolved("/a/b/../c"))
    }

    @Test
    fun anEmptyReferenceResolvesToTheOriginRoot() {
        assertEquals("https://bloomflowers.example/", resolved(""))
    }

    // --- the five SPEC section 3 rejections -------------------------------------------------------

    /**
     * Measured: `URI("https://bloomflowers.example/").resolve("//evil.example/catalog")` returns
     * `https://evil.example/catalog`. The reference inherits the base's scheme and lands off-origin,
     * so it *looks* origin-relative to any check that only tests for a leading `/`. Rejected before
     * resolution, because after resolution there is nothing left to notice.
     */
    @Test
    fun protocolRelativeReferencesAreRejected() {
        assertEquals(UrlRejection.PROTOCOL_RELATIVE, rejection("//evil.example/catalog"))
        assertEquals(UrlRejection.PROTOCOL_RELATIVE, rejection("//evil.example"))
    }

    /**
     * Measured: `URI("https://h/../../evil").normalize()` leaves the `..` segments in place rather
     * than collapsing them. That residue is the escape signal, which is what lets this be a
     * rejection rather than a silent rewrite.
     */
    @Test
    fun traversalEscapingTheOriginIsRejected() {
        assertEquals(UrlRejection.TRAVERSAL_ESCAPE, rejection("/../../evil"))
        assertEquals(UrlRejection.TRAVERSAL_ESCAPE, rejection("/a/../../b"))
        assertEquals(UrlRejection.TRAVERSAL_ESCAPE, rejection("../evil"))
    }

    /**
     * Percent-encoded traversal. A resolver that compares raw segments against the literal `..`
     * misses `%2e%2e`, and browsers decode it before resolving — so the encoded spelling escapes
     * the origin in the WebView while passing the check.
     */
    @Test
    fun percentEncodedTraversalIsRejected() {
        assertEquals(UrlRejection.TRAVERSAL_ESCAPE, rejection("/%2e%2e/%2e%2e/evil"))
        assertEquals(UrlRejection.TRAVERSAL_ESCAPE, rejection("/%2E%2E/evil"))
        assertEquals(UrlRejection.TRAVERSAL_ESCAPE, rejection("/%2e./evil"))
        assertEquals(UrlRejection.TRAVERSAL_ESCAPE, rejection("/.%2E/evil"))
    }

    /**
     * The serving origin's host appears verbatim in the URL, so `contains` or `startsWith` passes
     * while the real host is `evil.example`. `spec/fixtures/invalid/nav-userinfo-authority.json`.
     */
    @Test
    fun userinfoInTheAuthorityIsRejected() {
        assertEquals(
            UrlRejection.USERINFO_PRESENT,
            rejection("https://bloomflowers.example@evil.example/catalog"),
        )
        assertEquals(UrlRejection.USERINFO_PRESENT, rejection("https://user:pass@evil.example/"))
    }

    /** Origin is scheme + host + port. A resolver comparing only the host lets this through. */
    @Test
    fun aDifferentPortIsADifferentOrigin() {
        assertEquals(UrlRejection.CROSS_ORIGIN, rejection("https://bloomflowers.example:8443/catalog"))
    }

    /**
     * Subdomains, parents and siblings are all outside. `logo-subdomain.json` is the corpus case
     * and the one most likely to be "fixed" into a bug: `cdn.bloomflowers.example` is the site's
     * own CDN and is still a different origin, per `ADR-004`.
     */
    @Test
    fun subdomainsAndSiblingsAreCrossOrigin() {
        assertEquals(UrlRejection.CROSS_ORIGIN, rejection("https://cdn.bloomflowers.example/logo.png"))
        assertEquals(UrlRejection.CROSS_ORIGIN, rejection("https://evil.example/catalog"))
        assertEquals(UrlRejection.CROSS_ORIGIN, rejection("https://notbloomflowers.example/x"))
        assertEquals(UrlRejection.CROSS_ORIGIN, rejection("https://bloomflowers.example.evil.test/x"))
    }

    @Test
    fun aDifferentSchemeIsADifferentOrigin() {
        // http is an allowed scheme, so this is a cross-origin rejection rather than a scheme one.
        assertEquals(UrlRejection.CROSS_ORIGIN, rejection("http://bloomflowers.example/x"))
    }

    // --- schemes ------------------------------------------------------------------------------------

    @Test
    fun deniedSchemesAreRejected() {
        listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://media/external/images/1",
            "intent://scan/#Intent;scheme=zxing;end",
            // The corpus spelling from spec/fixtures/invalid/scheme-data.json. Base64 payloads are
            // all URI-legal characters, so this reaches the scheme check — see the test below for
            // the spelling that does not.
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "mailto:someone@bloomflowers.example",
            "tel:+10000000000",
            "geo:0,0",
            "ftp://bloomflowers.example/x",
        ).forEach { raw ->
            assertEquals("scheme rejection expected for $raw", UrlRejection.SCHEME_NOT_ALLOWED, rejection(raw))
        }
    }

    /**
     * A denied scheme carrying URI-illegal characters is refused as [UrlRejection.MALFORMED], not
     * [UrlRejection.SCHEME_NOT_ALLOWED] — `java.net.URI` rejects the `<` before any scheme check
     * runs. Both are rejections and the disposition above is identical, so this is a reporting
     * distinction rather than a security one, but it is pinned because the reason reaches a site
     * owner through `DEVX-001`'s inspector and "malformed" sends them looking for a typo when the
     * real answer is that the scheme was never going to be allowed.
     *
     * Deliberately not fixed by sniffing the scheme before parsing: that would add a second,
     * hand-rolled URL parser whose disagreements with `java.net.URI` would themselves be a bug
     * surface, to improve a diagnostic message on an input that is refused either way.
     */
    @Test
    fun deniedSchemesWithIllegalCharactersAreRejectedAsMalformed() {
        assertEquals(UrlRejection.MALFORMED, rejection("data:text/html,<script>alert(1)</script>"))
        assertEquals(UrlRejection.MALFORMED, rejection("javascript:alert(\"a b\")"))
    }

    @Test
    fun opaqueAndAuthorityLessUrlsAreRejected() {
        // Measured: URI("https:evil") is absolute and opaque -- scheme https, authority null.
        assertEquals(UrlRejection.OPAQUE, rejection("https:evil"))
        assertEquals(UrlRejection.OPAQUE, rejection("https:/evil.example/x"))
    }

    @Test
    fun unparseableReferencesAreRejected() {
        assertEquals(UrlRejection.MALFORMED, rejection("\\\\evil.example/x"))
        assertEquals(UrlRejection.MALFORMED, rejection("/\\evil.example"))
        assertEquals(UrlRejection.MALFORMED, rejection("  /cart  "))
        assertEquals(UrlRejection.MALFORMED, rejection("https://bloomflowers.example:99999/x"))
    }

    // --- totality -------------------------------------------------------------------------------------

    /**
     * `ADR-010`: nothing in this package throws into a browsing path. The manifest is untrusted
     * remote input, so "we did not think of that string" must degrade to a rejection.
     */
    @Test
    fun noInputThrows() {
        listOf(
            "", " ", "\n", " ", "?", "#", "//", "///", "..", "../..", "%", "%zz", "%2",
            ":", "::", "https://", "https://:443", "http://[", "[::1]", "a".repeat(10_000),
            "https://bloomflowers.example/ ", "😀", "/cart‎",
        ).forEach { raw ->
            val result = resolve(raw)
            assertTrue(
                "resolve($raw) must return a value, not throw",
                result is UrlResolution.Resolved || result is UrlResolution.Rejected,
            )
        }
    }

    /** Resolution never widens the origin: whatever comes back starts with the canonical origin. */
    @Test
    fun everyResolvedUrlStaysInsideTheServingOrigin() {
        listOf("/cart", "catalog", "/", "?q=1", "#top", "/a/../b", "", "https://bloomflowers.example:443/x")
            .forEach { raw ->
                assertTrue(
                    "resolved($raw) escaped the origin",
                    resolved(raw).startsWith("${origin.canonical}/"),
                )
            }
    }
}
