package app.webora.browser.inspector

import dev.siteskin.core.SiteSkinLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorTextTest {

    @Test
    fun `a forged pointer cannot open a second line in the panel`() {
        // SS-W-FIELD-UNKNOWN reports the key it did not recognise, and the key is website text.
        // This is the manifest trying to draw its own row into the browser's diagnostic tool.
        val forged = "/x\nHTTP status: 200"

        val value = inspectorValue(forged)

        assertFalse("a value must never contain a line break: $value", value.any { it == '\n' })
        assertEquals("/x HTTP status: 200", value)
    }

    @Test
    fun `every separator class collapses, including the ones a regex misses`() {
        // LINE SEPARATOR and NO-BREAK SPACE are not matched by \s in java.util.regex, which is why
        // this is a character walk rather than a pattern.
        val raw = "a b c\td\re f"

        assertEquals("a b c d e f", inspectorValue(raw))
    }

    @Test
    fun `a bidi override cannot reverse a value into something that reads like a label`() {
        val raw = "‮diorigin"

        val value = inspectorValue(raw)

        assertFalse(value.any { Character.getType(it) == Character.FORMAT.toInt() })
        assertEquals("diorigin", value)
    }

    @Test
    fun `a run of separators becomes one space and never leads or trails`() {
        assertEquals("a b", inspectorValue("  \n\t a \r\n\n b  \n "))
    }

    @Test
    fun `an over-long value is bounded by the published core limit`() {
        val raw = "x".repeat(SiteSkinLimits.MAX_SUBTITLE_LENGTH * 2)

        assertEquals(SiteSkinLimits.MAX_SUBTITLE_LENGTH, inspectorValue(raw).length)
    }

    @Test
    fun `the bound counts displayed characters, not the separators it removed`() {
        val padded = " ".repeat(SiteSkinLimits.MAX_SUBTITLE_LENGTH) + "y".repeat(4)

        assertEquals("yyyy", inspectorValue(padded))
    }

    @Test
    fun `absent and empty values render as nothing rather than as a claim`() {
        assertTrue(inspectorValue(null).isEmpty())
        assertTrue(inspectorValue("").isEmpty())
        assertTrue(inspectorValue("   \n  ").isEmpty())
    }
}
