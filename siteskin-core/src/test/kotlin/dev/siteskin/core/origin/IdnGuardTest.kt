package dev.siteskin.core.origin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.IDN

/**
 * `https://аpple.com` with a Cyrillic а renders identically to `https://apple.com` and is a
 * different origin. Origin binding already treats them as different — that part is not at risk.
 * What this guard supplies is the signal `SKIN-002` needs to tell the *user* that what they are
 * reading is not what they think.
 */
class IdnGuardTest {

    /** Hosts arrive canonicalized, so the guard is always handed punycode. */
    private fun flagged(unicodeHost: String): Boolean =
        IdnGuard.hasMixedScript(IDN.toASCII(unicodeHost))

    @Test
    fun latinAndCyrillicInOneLabelIsFlagged() {
        // U+0430 CYRILLIC SMALL LETTER A, then Latin "pple".
        assertTrue(flagged("аpple.com"))
    }

    @Test
    fun singleScriptHostsAreNotFlagged() {
        assertFalse(flagged("apple.com"))
        assertFalse(flagged("münchen.example"))
        assertFalse(flagged("shop.example"))
    }

    /**
     * The flag must not depend on how the host was spelled on the way in. `SiteOrigin` derives it
     * from the canonical form for exactly this reason: two spellings that compare equal must
     * produce equal flags, or the type carries a field that contradicts its own equality.
     */
    @Test
    fun punycodeAndUnicodeSpellingsAgree() {
        assertEquals(
            IdnGuard.hasMixedScript("xn--pple-43d.com"),
            IdnGuard.hasMixedScript(IDN.toASCII("аpple.com")),
        )
        assertTrue(IdnGuard.hasMixedScript("xn--pple-43d.com"))
        assertFalse(IdnGuard.hasMixedScript("xn--mnchen-3ya.example"))
    }

    /**
     * Japanese routinely mixes Han, Hiragana and Katakana **inside one label**, so a naive
     * "more than one script ⇒ suspicious" rule would flag a large fraction of the legitimate
     * Japanese web. UTS #39's Highly Restrictive profile is the line drawn here.
     */
    @Test
    fun legitimateCjkScriptCombinationsAreNotFlagged() {
        assertFalse("Han + Hiragana + Katakana is ordinary Japanese", flagged("東京とうきょうトウキョウ.example"))
        assertFalse(flagged("日本語.example"))
        assertFalse(flagged("ソニー.example"))
        assertFalse(flagged("한국.example"))
    }

    @Test
    fun digitsAndHyphensNeverFlagOnTheirOwn() {
        // Digits and '-' are script COMMON. If COMMON counted, every hyphenated host would flag.
        assertFalse(flagged("a-b-1.example"))
        assertFalse(flagged("x1.example"))
        assertFalse(IdnGuard.hasMixedScript("1.2.3.4"))
        assertFalse(IdnGuard.hasMixedScript("[::1]"))
    }

    /**
     * Scripts are compared **per label**, not across the whole host. A Japanese second-level name
     * under a Latin TLD is the normal case, and comparing across the whole host would flag it.
     */
    @Test
    fun scriptsAreComparedWithinALabelNotAcrossTheHost() {
        assertFalse(flagged("日本語.example"))
        // ...but mixing inside a single label still flags, even with a Latin label elsewhere.
        assertTrue(flagged("аpple.example"))
    }

    @Test
    fun malformedInputIsNotFlaggedRatherThanThrowing() {
        // ADR-010: nothing in this package throws into a browsing path.
        assertFalse(IdnGuard.hasMixedScript(""))
        assertFalse(IdnGuard.hasMixedScript("xn--"))
        assertFalse(IdnGuard.hasMixedScript("xn--zzzzzzzz"))
    }
}
