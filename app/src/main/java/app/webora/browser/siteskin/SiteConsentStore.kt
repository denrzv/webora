package app.webora.browser.siteskin

import android.content.Context
import dev.siteskin.core.origin.SiteOrigin
import java.nio.charset.StandardCharsets
import java.util.Base64

internal interface SiteConsentPreferences {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun entries(): Map<String, String>
    fun remove(key: String)
    fun clear()
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

    fun decisions(): List<StoredSiteConsent> = preferences.entries().mapNotNull { (key, value) ->
        val canonical = runCatching {
            String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8)
        }.getOrNull() ?: return@mapNotNull null
        val origin = SiteOrigin.parse(canonical)?.takeIf { it.canonical == canonical }
            ?: return@mapNotNull null
        val decision = SiteConsentDecision.entries.singleOrNull { it.name == value }
            ?: return@mapNotNull null
        StoredSiteConsent(origin, decision)
    }.sortedBy { it.origin.canonical }

    fun remove(origin: SiteOrigin) = preferences.remove(origin.consentKey())

    fun clear() = preferences.clear()
}

internal data class StoredSiteConsent(val origin: SiteOrigin, val decision: SiteConsentDecision)

private class SharedSiteConsentPreferences(context: Context) : SiteConsentPreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun entries(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap()

    override fun remove(key: String) { preferences.edit().remove(key).apply() }

    override fun clear() { preferences.edit().clear().apply() }
}

private fun SiteOrigin.consentKey(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(canonical.toByteArray(StandardCharsets.UTF_8))

private const val PREFERENCES_NAME = "webora_siteskin_consent"
