package dev.siteskin.core.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPolicyTest {
    @Test fun `short and lowercase colors canonicalize`() {
        assertEquals("#D94F8A", ColorPolicy.canonicalize("#d94f8a"))
        assertEquals("#AABBCC", ColorPolicy.canonicalize("#abc"))
    }

    @Test fun `adequate contrast remains unchanged`() {
        val result = ColorPolicy.correct("#000000", "#FFFFFF", 4.5)
        assertEquals("#000000", result.color)
        assertFalse(result.corrected)
    }

    @Test fun `hostile contrast is corrected deterministically`() {
        val result = ColorPolicy.correct("#777777", "#777777", 4.5)
        assertTrue(result.corrected)
        assertEquals("#FFFFFF", result.color)
        assertEquals(result, ColorPolicy.correct("#777777", "#777777", 4.5))
    }
}
