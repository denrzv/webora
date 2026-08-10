package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestCacheTest {
    @Test fun `entries are isolated by complete origin and schema version`() {
        val cache = ManifestCache { 0 }
        val first = key("https://shop.example", "1.0")
        val otherOrigin = key("https://cdn.shop.example", "1.0")
        val otherVersion = key("https://shop.example", "1.1")

        cache.put(first, byteArrayOf(1), metadata())
        cache.put(otherOrigin, byteArrayOf(2), metadata())

        assertArrayEquals(byteArrayOf(1), cache.get(first)?.bytes)
        assertArrayEquals(byteArrayOf(2), cache.get(otherOrigin)?.bytes)
        assertNull(cache.get(otherVersion))
        assertFalse(first == otherOrigin)
        assertFalse(first == otherVersion)
    }

    @Test fun `new active version replaces only that origins previous entry`() {
        val cache = ManifestCache { 0 }
        val old = key("https://shop.example", "1.0")
        val current = key("https://shop.example", "1.1")
        val other = key("https://other.example", "1.0")
        cache.put(old, byteArrayOf(1), metadata())
        cache.put(other, byteArrayOf(2), metadata())

        cache.put(current, byteArrayOf(3), metadata())

        assertNull(cache.get(old))
        assertEquals(current, cache.active(current.origin)?.key)
        assertEquals(other, cache.active(other.origin)?.key)
    }

    @Test fun `cache owns bytes and callers receive defensive copies`() {
        val cache = ManifestCache { 0 }
        val supplied = byteArrayOf(1, 2)
        cache.put(key(), supplied, metadata())
        supplied[0] = 9
        val firstRead = requireNotNull(cache.active(key().origin))
        firstRead.bytes[1] = 9

        assertArrayEquals(byteArrayOf(1, 2), cache.active(key().origin)?.bytes)
    }

    @Test fun `freshness obeys max age and the twenty four hour ceiling`() {
        var now = 1_000L
        val cache = ManifestCache { now }
        val short = cache.put(key(), byteArrayOf(1), metadata("max-age=2"))
        now += 1_999
        assertTrue(cache.isFresh(short))
        now += 1
        assertFalse(cache.isFresh(short))

        val capped = cache.put(key(), byteArrayOf(1), metadata("public, max-age=999999999999999999999"))
        assertFalse(cache.isFresh(capped))
        val validCapped = cache.put(key(), byteArrayOf(1), metadata("max-age=999999"))
        now += SiteSkinLimits.MAX_CACHE_TTL_SECONDS * 1_000 - 1
        assertTrue(cache.isFresh(validCapped))
        now += 1
        assertFalse(cache.isFresh(validCapped))
    }

    @Test fun `missing malformed negative and ambiguous max age are stale`() {
        listOf(null, "max-age=nope", "max-age=-1", "max-age=1, max-age=2").forEach { value ->
            assertEquals(0, cacheTtlMillis(value))
        }
    }

    @Test fun `clear removes entries and active version index`() {
        val cache = ManifestCache { 0 }
        val key = key()
        cache.put(key, byteArrayOf(1), metadata())

        cache.clear()

        assertNull(cache.get(key))
        assertNull(cache.active(key.origin))
    }

    private fun key(origin: String = "https://shop.example", version: String = "1.0") =
        ManifestCacheKey(origin, version)

    private fun metadata(cacheControl: String? = "max-age=60") =
        ManifestCacheMetadata(cacheControl = cacheControl)
}
