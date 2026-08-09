package app.webora.browser.siteskin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandAssetPolicyTest {
    @Test fun `recognizes only PNG and WebP signatures`() {
        assertEquals(BrandImageFormat.PNG, brandImageFormat(PNG))
        assertEquals(BrandImageFormat.WEBP, brandImageFormat(WEBP))
        assertNull(brandImageFormat("<svg/>".toByteArray()))
        assertNull(brandImageFormat("RIFFtiny".toByteArray()))
    }

    @Test fun `bounds both axes and decoded pixel count`() {
        assertTrue(brandImageDimensionsAllowed(1024, 1024))
        assertFalse(brandImageDimensionsAllowed(1025, 1))
        assertFalse(brandImageDimensionsAllowed(1, 1025))
        assertFalse(brandImageDimensionsAllowed(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(brandImageDimensionsAllowed(0, 20))
    }

    @Test fun `monogram prefers short name and preserves a supplementary code point`() {
        assertEquals("B", brandMonogram(" bloom ", "Flowers"))
        assertEquals("F", brandMonogram(" ", "flowers"))
        assertEquals("🚀", brandMonogram(null, "🚀 Labs"))
        assertEquals("•", brandMonogram(null, " "))
    }

    private companion object {
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val WEBP = "RIFF0000WEBP".toByteArray()
    }
}
