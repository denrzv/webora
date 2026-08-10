package app.webora.browser.siteskin

import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteConsentStoreTest {
    @Test fun `decision survives store recreation`() {
        val preferences = MemoryPreferences()
        SiteConsentStore(preferences).save(origin("https://shop.example"), SiteConsentDecision.ALLOW)

        assertEquals(
            SiteConsentDecision.ALLOW,
            SiteConsentStore(preferences).decision(origin("https://shop.example")),
        )
    }

    @Test fun `scheme host and port decisions are isolated`() {
        val store = SiteConsentStore(MemoryPreferences())
        store.save(origin("https://shop.example"), SiteConsentDecision.NEVER)

        assertNull(store.decision(origin("http://shop.example")))
        assertNull(store.decision(origin("https://admin.shop.example")))
        assertNull(store.decision(origin("https://shop.example:8443")))
        assertEquals(SiteConsentDecision.NEVER, store.decision(origin("https://SHOP.example:443")))
    }

    @Test fun `decisions list canonical origins and supports exact removal and clear`() {
        val preferences = MemoryPreferences()
        val store = SiteConsentStore(preferences)
        val shop = origin("https://shop.example:8443")
        val admin = origin("https://admin.shop.example")
        store.save(shop, SiteConsentDecision.ALLOW)
        store.save(admin, SiteConsentDecision.NEVER)
        preferences.put("not-base64!", "ALLOW")

        assertEquals(listOf(admin.canonical, shop.canonical), store.decisions().map { it.origin.canonical })
        store.remove(shop)
        assertEquals(listOf(admin.canonical), store.decisions().map { it.origin.canonical })
        store.clear()
        assertTrue(store.decisions().isEmpty())
    }

    private fun origin(value: String) = checkNotNull(SiteOrigin.parse(value))

    private class MemoryPreferences : SiteConsentPreferences {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun entries(): Map<String, String> = values.toMap()
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
