package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.action.ActionResolver
import dev.siteskin.core.model.SiteSkinConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `presentation.hub` selects a surface and moves nothing else.
 *
 * Criterion 18's negative control, and it is a **runtime sweep** rather than a scan for the reason
 * `UX-002` records for `C2`: a scan asserts what a file mentions, and the leak worth catching is a
 * value that reaches somewhere it was never named. The same manifest is validated once per hint
 * spelling and every browser-owned projection is compared across the set.
 *
 * The sweep carries its own guard against proving nothing — the hint must actually move the surface
 * — because a sweep whose inputs stopped differing passes silently forever.
 */
class HubHintIsolationTest {
    @Test
    fun `no hub value moves any browser-owned projection`() {
        val projections = HINTS.map { hint -> hint to project(configuration(hint)) }
        val baseline = projections.first().second

        projections.forEach { (hint, projection) ->
            assertEquals("hub=$hint moved a browser-owned projection", baseline, projection)
        }
    }

    /**
     * The guard on the sweep: the hint does change the one thing it is allowed to change.
     *
     * Without this, deleting `presentation` from the schema — or from `SecurityValidator` — leaves
     * every configuration identical, the sweep above green, and nothing anywhere red.
     */
    @Test
    fun `the sweep exercises a hint that really does select a surface`() {
        val surfaces = HINTS.map { configuration(it).hubSurface() }.toSet()

        assertTrue("the sweep must reach more than one surface", surfaces.size > 1)
        assertNotEquals(configuration("bouquet").hubSurface(), configuration("drawer").hubSurface())
    }

    /**
     * A hint cannot reach the browser trust surface, action validation or the item allow-lists.
     *
     * Stated as an equality over what a *site* can influence, so it covers the fields a future
     * reviewer would think to check and the ones nobody thought of: the whole normalized action
     * list, every resolved action, and every colour role are compared, not sampled.
     */
    private fun project(configuration: SiteSkinConfiguration): List<String> {
        val chrome = SiteSkinChromeModel.from(configuration, PAGE_URL)
        val theme = SiteSkinTheme.from(configuration)
        return buildList {
            add("origin=${configuration.origin}")
            add("home=${configuration.site.homeUrl}")
            add("browserMenu=${chrome.browserMenu}")
            (chrome.bottomNavigation + chrome.quickActions + chrome.siteMenu).forEach { item ->
                add("item=${item.id}|${item.label}|${item.icon}|${item.isActive}|${item.isNavigation}")
                add("action=${item.item.action.type}|${item.item.action.url}|${item.item.action.value}")
                add(
                    "resolved=" +
                        ActionResolver.resolve(item.item.action, configuration.site, PAGE_URL)?.javaClass?.simpleName,
                )
            }
            listOf(false, true).forEach { dark ->
                val scheme = theme.scheme(dark)
                add(
                    "colors[$dark]=${scheme.primary}|${scheme.onPrimary}|${scheme.secondary}|" +
                        "${scheme.onSecondary}|${scheme.background}|${scheme.onBackground}",
                )
            }
        }
    }

    private fun configuration(hint: String?): SiteSkinConfiguration {
        val presentation = hint?.let { """"presentation":{"hub":"$it"},""" }.orEmpty()
        val manifest = MANIFEST.replace("<PRESENTATION>", presentation)
        return SiteSkinValidator.validate(manifest.byteInputStream(), ORIGIN)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration }
    }

    private companion object {
        const val ORIGIN = "https://shop.example"
        const val PAGE_URL = "https://shop.example/catalog"

        /** Every spelling at the boundary: absent, each recognised token, and an unrecognised one. */
        val HINTS = listOf(null, "auto", "bouquet", "drawer", "carousel")

        val MANIFEST = """
            {"schemaVersion":"1.0",
             "site":{"id":"shop","name":"Shop","homeUrl":"/"},
             "branding":{"primaryColor":"#D94F8A","backgroundColor":"#FFF7FA","textColor":"#2B1B24"},
             <PRESENTATION>
             "bottomNavigation":[
               {"id":"home","label":"Home","icon":"home","action":{"type":"internal_url","url":"/"},
                "match":["/"]},
               {"id":"catalog","label":"Catalog","icon":"catalog",
                "action":{"type":"internal_url","url":"/catalog"},"match":["/catalog/**"]}],
             "quickActions":[
               {"id":"call","label":"Call","icon":"call","action":{"type":"phone","value":"+10000000000"}}],
             "menu":[{"id":"about","label":"About","action":{"type":"internal_url","url":"/about"}}]}
        """.trimIndent()
    }
}
