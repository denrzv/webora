package app.webora.browser.privacy

import android.content.Context

internal interface PrivacyPreferences {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

internal class PrivacySettingsStore(private val preferences: PrivacyPreferences) {
    constructor(context: Context) : this(SharedPrivacyPreferences(context))

    fun isSiteSkinEnabled(): Boolean = preferences.getBoolean(SITESKIN_ENABLED, true)

    fun setSiteSkinEnabled(enabled: Boolean) = preferences.putBoolean(SITESKIN_ENABLED, enabled)
}

private class SharedPrivacyPreferences(context: Context) : PrivacyPreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        preferences.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
}

private const val PREFERENCES_NAME = "webora_privacy_settings"
private const val SITESKIN_ENABLED = "siteskin_enabled"
