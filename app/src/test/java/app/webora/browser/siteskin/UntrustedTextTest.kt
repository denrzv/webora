package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bound itself, at bounds other than the inspector's.
 *
 * `InspectorTextTest` already covers the walk's behaviour through `inspectorValue`. What it cannot
 * cover is the part that changed when the function was generalized: that the limit is genuinely the
 * caller's, rather than `MAX_SUBTITLE_LENGTH` hiding behind a parameter that is never read. The
 * consent sheet bounds a title at 64 and would silently accept 128 if that regressed.
 */
class UntrustedTextTest {

    @Test
    fun `the caller's bound is the bound, not the inspector's`() {
        val raw = "x".repeat(SiteSkinLimits.MAX_SUBTITLE_LENGTH * 2)

        assertEquals(SiteSkinLimits.MAX_TITLE_LENGTH, untrustedText(raw, SiteSkinLimits.MAX_TITLE_LENGTH).length)
        assertEquals(SiteSkinLimits.MAX_LABEL_LENGTH, untrustedText(raw, SiteSkinLimits.MAX_LABEL_LENGTH).length)
        assertEquals(1, untrustedText(raw, 1).length)
    }

    @Test
    fun `a bidi override is stripped at every bound`() {
        // U+202E reverses everything after it. In the consent sheet that can reorder the reading of
        // browser-authored copy sitting beside the site's own text, so it must not survive.
        val value = untrustedText("‮Your Bank", SiteSkinLimits.MAX_TITLE_LENGTH)

        assertFalse(value.any { Character.getType(it) == Character.FORMAT.toInt() })
        assertEquals("Your Bank", value)
    }

    @Test
    fun `the separators a regex misses still collapse`() {
        // U+2028 LINE SEPARATOR and U+00A0 NO-BREAK SPACE are not matched by \s in java.util.regex.
        assertEquals("a b", untrustedText("a  b", SiteSkinLimits.MAX_TITLE_LENGTH))
    }

    @Test
    fun `a newline cannot open a second line at any bound`() {
        val value = untrustedText("Bloom Flowers\nVerified by Webora", SiteSkinLimits.MAX_TITLE_LENGTH)

        assertFalse("a displayed value must never contain a line break: $value", value.any { it == '\n' })
        assertEquals("Bloom Flowers Verified by Webora", value)
    }

    @Test
    fun `absent and blank input render as nothing rather than as a claim`() {
        assertTrue(untrustedText(null, SiteSkinLimits.MAX_TITLE_LENGTH).isEmpty())
        assertTrue(untrustedText("", SiteSkinLimits.MAX_TITLE_LENGTH).isEmpty())
        assertTrue(untrustedText("  \n  ", SiteSkinLimits.MAX_TITLE_LENGTH).isEmpty())
    }
}
