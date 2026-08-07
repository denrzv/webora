package dev.siteskin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placeholder coverage so the module has a green test task from the first commit.
 * Real validation tests arrive with CORE-002..006, driven by the fixture corpus in spec/.
 */
class SiteSkinSchemaTest {

    @Test
    fun `current schema version matches the supported major`() {
        val major = SiteSkinSchema.CURRENT.substringBefore('.').toInt()
        assertEquals(SiteSkinSchema.SUPPORTED_MAJOR, major)
    }

    @Test
    fun `discovery path is origin-relative and well-known`() {
        assertTrue(SiteSkinSchema.WELL_KNOWN_PATH.startsWith("/.well-known/"))
    }

    @Test
    fun `navigation limit is small enough to render on a phone`() {
        // Five is the Material bottom-navigation maximum; above that items become
        // unreadable rather than merely crowded.
        assertTrue(SiteSkinLimits.MAX_NAVIGATION_ITEMS in 3..5)
    }
}
