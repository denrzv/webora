package app.webora.browser.visual

import app.webora.browser.siteskin.SITESKIN_HUB_ROW_TAG_PREFIX

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

    /**
     * How a site item is addressed in whichever hub surface Bloom actually gets.
     *
     * The published manifest declares no `presentation` object, so `UX-022`'s policy resolves `AUTO`
     * to the drawer and these ids appear as drawer rows. One function so the showcase and the smoke
     * exercise address the same surface; if Bloom ever declares `"hub": "bouquet"`, this is the one
     * line that follows it.
     */
    fun actionTag(id: String): String = "$SITESKIN_HUB_ROW_TAG_PREFIX$id"
}
