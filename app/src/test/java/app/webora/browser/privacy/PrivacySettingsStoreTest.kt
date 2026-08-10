package app.webora.browser.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySettingsStoreTest {
    @Test fun `SiteSkin defaults enabled and persists explicit changes`() {
        val preferences = MemoryPreferences()

        assertTrue(PrivacySettingsStore(preferences).isSiteSkinEnabled())
        PrivacySettingsStore(preferences).setSiteSkinEnabled(false)
        assertFalse(PrivacySettingsStore(preferences).isSiteSkinEnabled())
    }

    private class MemoryPreferences : PrivacyPreferences {
        private val values = mutableMapOf<String, Boolean>()
        override fun getBoolean(key: String, default: Boolean): Boolean = values[key] ?: default
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    }
}
