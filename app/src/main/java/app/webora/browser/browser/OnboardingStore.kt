package app.webora.browser.browser

import android.content.Context

internal class OnboardingStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = preferences.getBoolean(COMPLETED_KEY, false)

    fun complete() {
        preferences.edit().putBoolean(COMPLETED_KEY, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "webora_onboarding"
        const val COMPLETED_KEY = "completed"
    }
}
