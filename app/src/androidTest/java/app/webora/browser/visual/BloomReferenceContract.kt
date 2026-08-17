package app.webora.browser.visual

import app.webora.browser.siteskin.SITESKIN_ACTION_TAG_PREFIX

/**
 * Android-test contract for the public Bloom Flowers reference integration.
 *
 * These are trusted manifest ids, not display labels. Keeping the reference values in one place makes
 * the visual showcase and the deeper navigation smoke exercise the same deployed integration contract.
 */
internal object BloomReferenceContract {
    const val HOME_ACTION_ID = "home"
    const val PROFILE_ACTION_ID = "profile"

    val ACTION_IDS = listOf(
        HOME_ACTION_ID,
        "catalog",
        "cart",
        PROFILE_ACTION_ID,
        "call-shop",
    )

    fun actionTag(id: String): String = "$SITESKIN_ACTION_TAG_PREFIX$id"
}
