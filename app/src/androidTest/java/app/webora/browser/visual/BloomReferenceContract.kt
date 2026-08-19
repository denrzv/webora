package app.webora.browser.visual

import app.webora.browser.siteskin.SITESKIN_DOCK_SITE_TAG_PREFIX
import app.webora.browser.siteskin.SITESKIN_HUB_ROW_TAG_PREFIX

/**
 * Android-test contract for the public Bloom Flowers reference integration.
 *
 * These are trusted manifest ids, not display labels. Keeping the reference values in one place makes
 * the visual showcase and the deeper navigation smoke exercise the same deployed integration contract.
 *
 * **`UX-025` split the ids across two surfaces, and the split is the contract.** Bloom now declares
 * `"dock": ["catalog", "cart", "profile"]`, so those three render as persistent dock slots and are
 * *absent* from the drawer — presentation de-duplication, not deletion. `home` and `call-shop` were
 * deliberately left un-nominated in the manifest precisely so the drawer still has rows to show; a
 * projection covering everything would make the de-duplication assertion vacuous.
 */
internal object BloomReferenceContract {
    const val HOME_ACTION_ID = "home"
    const val PROFILE_ACTION_ID = "profile"

    /** Nominated by `presentation.dock`, in the manifest's order. Rendered as dock slots. */
    val DOCK_ACTION_IDS = listOf("catalog", "cart", PROFILE_ACTION_ID)

    /** Everything the manifest declares and did not nominate. Rendered as drawer rows. */
    val DRAWER_ACTION_IDS = listOf(HOME_ACTION_ID, "call-shop")

    /** Every trusted id, whichever surface carries it. */
    val ACTION_IDS = DOCK_ACTION_IDS + DRAWER_ACTION_IDS

    /** How a projected item is addressed in the persistent dock. */
    fun dockTag(id: String): String = "$SITESKIN_DOCK_SITE_TAG_PREFIX$id"

    /**
     * How an un-nominated item is addressed in the hub drawer.
     *
     * `UX-022` pointed this at the drawer's row prefix when `AUTO` began resolving to the drawer;
     * Bloom now asks for `"hub": "drawer"` explicitly, so the surface is the same one by request
     * rather than by default.
     */
    fun actionTag(id: String): String = "$SITESKIN_HUB_ROW_TAG_PREFIX$id"
}
