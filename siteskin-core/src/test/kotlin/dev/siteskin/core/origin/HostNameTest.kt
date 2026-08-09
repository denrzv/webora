package dev.siteskin.core.origin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canonicalization is the whole of origin comparison. Two hosts that name the same site must
 * produce byte-identical output, and two that do not must never converge — every later security
 * control is a string equality on what this file returns.
 */
class HostNameTest {

    private fun canonical(raw: String): String? = HostName.canonicalize(raw)?.ascii

    // --- case ---------------------------------------------------------------------------------

    /**
     * The finding that motivates this test existing separately from the punycode one:
     * `IDN.toASCII` does **not** lowercase ASCII input. Measured on the build JDK —
     * `IDN.toASCII("ShOp.Example")` returns `ShOp.Example` unchanged. Relying on punycode
     * conversion to canonicalize case therefore leaves `https://ShOp.Example` and
     * `https://shop.example` as different origins.
     */
    @Test
    fun asciiHostsAreLowercased() {
        assertEquals("shop.example", canonical("ShOp.Example"))
        assertEquals("shop.example", canonical("SHOP.EXAMPLE"))
        assertEquals("shop.example", canonical("shop.example"))
    }

    // --- IDN ----------------------------------------------------------------------------------

    @Test
    fun idnHostsCanonicalizeToPunycode() {
        assertEquals("xn--mnchen-3ya.example", canonical("münchen.example"))
        assertEquals("xn--mnchen-3ya.example", canonical("xn--mnchen-3ya.example"))
    }

    /**
     * Both spellings and both cases must land on one string. `IDN.toASCII` case-folds non-ASCII
     * labels itself (nameprep), so this passes with or without our own lowercase step — which is
     * exactly why [asciiHostsAreLowercased] carries the negative control instead of this test.
     */
    @Test
    fun idnCaseFoldingAgreesWithPunycodeConversion() {
        assertEquals(canonical("münchen.example"), canonical("MÜNCHEN.example"))
        assertEquals(canonical("münchen.example"), canonical("MÜNCHEN.EXAMPLE"))
    }

    @Test
    fun idnConversionFailureIsNullRatherThanAThrow() {
        // IDN.toASCII throws IllegalArgumentException on an empty label. ADR-010 forbids letting
        // that reach a browsing path.
        assertNull(canonical("shop..example"))
        assertNull(canonical("shop...example"))
    }

    // --- trailing dot -------------------------------------------------------------------------

    /**
     * `shop.example.` is the fully-qualified spelling of `shop.example`: same DNS name, same
     * certificate. Folding them can only make the two sides of a comparison agree; it can never
     * make two distinct names equal, because the fold happens before label comparison.
     */
    @Test
    fun oneTrailingRootDotIsStripped() {
        assertEquals("shop.example", canonical("shop.example."))
        assertEquals(canonical("shop.example"), canonical("shop.example."))
    }

    @Test
    fun moreThanOneTrailingDotIsRejected() {
        // Stripping exactly one leaves an empty final label, which is not a host.
        assertNull(canonical("shop.example.."))
    }

    // --- label grammar ------------------------------------------------------------------------

    /**
     * `IDN.toASCII` does not apply STD3 ASCII rules by default — measured: `-bad.example` comes
     * back unchanged. So the label grammar is ours to enforce.
     */
    @Test
    fun labelsMustSatisfyStd3Rules() {
        assertNull(canonical("-bad.example"))
        assertNull(canonical("bad-.example"))
        assertNull(canonical("bad_label.example"))
        assertNull(canonical("bad label.example"))
        assertEquals("a-b.example", canonical("a-b.example"))
        assertEquals("x1.example", canonical("x1.example"))
    }

    @Test
    fun emptyHostIsRejected() {
        assertNull(canonical(""))
        assertNull(canonical("."))
    }

    @Test
    fun overLongLabelsAndNamesAreRejected() {
        val label63 = "a".repeat(63)
        val label64 = "a".repeat(64)
        assertEquals("$label63.example", canonical("$label63.example"))
        assertNull(canonical("$label64.example"))

        // 253 is the ceiling; four 63-byte labels plus their separators is 255.
        val tooLong = List(4) { label63 }.joinToString(".")
        assertNull(canonical(tooLong))
    }

    // --- IP literals --------------------------------------------------------------------------

    /**
     * HTTPS to an IP literal is legal, and `debugRelease` local testing needs it. `IDN.toASCII`
     * cannot be handed a bracketed IPv6 literal, so literals skip conversion entirely.
     */
    @Test
    fun ipv4LiteralsArePreservedAndFlagged() {
        val host = HostName.canonicalize("1.2.3.4")
        assertEquals("1.2.3.4", host?.ascii)
        assertTrue(host!!.isIpLiteral)
    }

    @Test
    fun ipv6LiteralsArePreservedAndFlagged() {
        val host = HostName.canonicalize("[::1]")
        assertEquals("[::1]", host?.ascii)
        assertTrue(host!!.isIpLiteral)

        assertEquals("[2001:db8::1]", HostName.canonicalize("[2001:DB8::1]")?.ascii)
    }

    @Test
    fun malformedIpv6LiteralsAreRejected() {
        assertNull(canonical("[::1"))
        assertNull(canonical("[not:an:address:zz]"))
        assertNull(canonical("[]"))
    }

    @Test
    fun registeredNamesAreNotFlaggedAsIpLiterals() {
        assertFalse(HostName.canonicalize("shop.example")!!.isIpLiteral)
        assertFalse(HostName.canonicalize("münchen.example")!!.isIpLiteral)
        // Numeric-looking but not a dotted quad.
        assertFalse(HostName.canonicalize("1.2.3.4.5")!!.isIpLiteral)
        assertFalse(HostName.canonicalize("999.1.1.1")!!.isIpLiteral)
    }

    // --- the property that matters -------------------------------------------------------------

    /**
     * The point of the whole file: spellings that name one site converge, and spellings that name
     * different sites never do. A canonicalizer that merged the second group would make every
     * later origin check a formality.
     */
    @Test
    fun distinctHostsNeverConverge() {
        val distinct = listOf(
            "shop.example",
            "admin.shop.example",
            "shop.example.org",
            "notshop.example",
            "xn--mnchen-3ya.example",
        ).map { canonical(it) }

        assertEquals(distinct.size, distinct.toSet().size)
        assertTrue(distinct.none { it == null })
    }
}
