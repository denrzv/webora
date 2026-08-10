package app.webora.browser.siteskin

import android.content.Context
import dev.siteskin.core.origin.SiteOrigin
import java.nio.charset.StandardCharsets
import java.util.Base64

internal interface SiteConsentPreferences {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

internal class SiteConsentStore(private val preferences: SiteConsentPreferences) {
    constructor(context: Context) : this(SharedSiteConsentPreferences(context))

    fun decision(origin: SiteOrigin): SiteConsentDecision? =
        preferences.get(origin.consentKey())?.let { stored ->
            SiteConsentDecision.entries.singleOrNull { it.name == stored }
        }

    fun save(origin: SiteOrigin, decision: SiteConsentDecision) {
        preferences.put(origin.consentKey(), decision.name)
    }
}

private class SharedSiteConsentPreferences(context: Context) : SiteConsentPreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

private fun SiteOrigin.consentKey(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(canonical.toByteArray(StandardCharsets.UTF_8))

private const val PREFERENCES_NAME = "webora_siteskin_consent"
