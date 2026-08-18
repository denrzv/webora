package app.webora.browser.siteskin

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the JVM gate cannot reach: the drawer composed, scrolled, dismissed and driven.
 *
 * Instrumented evidence, never promoted to a gate claim — `A11Y-001`'s rule, and the posture
 * `CI-002`–`CI-005` each recorded. The structural half lives in `SiteSkinHubDrawerContractTest`,
 * which runs on every developer machine; these cases need a device and this checkout has none.
 */
class SiteSkinHubDrawerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * All twenty permitted `menu` entries are reachable, which is the defect this ticket removes.
     *
     * The bouquet showed five items across all three collections, so a site publishing the full
     * `SPEC.md` §8 menu had fifteen entries the browser had validated and never offered. The last
     * entry is reached by scrolling rather than asserted in place: at 200% font scale on a compact
     * host it is far below the viewport, and `performScrollTo` failing is exactly the regression
     * that matters — a drawer that truncated instead of scrolling.
     */
    @Test fun everyPermittedMenuEntryIsReachableAtLargeText() {
        val model = model(MENU_MANIFEST)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                content(model) {}
            }
        }

        assertEquals(MAX_MENU_ENTRIES, model.siteMenu.size)
        model.siteMenu.forEach { item ->
            compose.onNodeWithTag(row(item.id)).performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(MINIMUM_TARGET)
        }
    }

    /** Selecting a row dismisses first, then dispatches the original trusted item. */
    @Test fun selectingARowDismissesBeforeDispatchingTheTrustedItem() {
        var dismissed = false
        var selected = ""
        compose.setContent {
            HubDrawerContent(
                model = model(BLOOM_MANIFEST),
                identity = SiteSkinHubIdentity.from("Bloom", BrandAsset.Monogram("B")),
                colors = colors(),
                onSelect = { item ->
                    check(dismissed) { "the drawer must close before the action runs" }
                    selected = item.id
                },
                onDismiss = { dismissed = true },
            )
        }

        compose.onNodeWithTag(row("catalog")).performClick()

        assertTrue(dismissed)
        assertEquals("catalog", selected)
    }

    /**
     * Back closes the drawer without reaching the browser.
     *
     * The `Dialog` is its own window, so this is structural rather than a handler the drawer
     * installs — which is the whole reason `UX-022` chose it over `ModalNavigationDrawer`. If this
     * ever fails, the fix is the container, not a `BackHandler` inside the drawer.
     */
    @Test fun systemBackClosesTheDrawerAndNothingElse() {
        var visible by mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            SiteSkinHubHost(
                visible = visible,
                surface = HubSurface.DRAWER,
                model = model(BLOOM_MANIFEST),
                identity = SiteSkinHubIdentity.from("Bloom", BrandAsset.Monogram("B")),
                colors = colors(),
                onSelect = {},
                onDismiss = {
                    dismissals += 1
                    visible = false
                },
            )
        }

        pressBack()

        compose.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).assertDoesNotExist()
        assertEquals(1, dismissals)
    }

    /**
     * Only navigation rows publish selection, and the active one is the route `NavMatcher` chose.
     *
     * `UX-015`'s split, driven rather than scanned: a quick action is an action, not a route, so its
     * node must carry no `SelectableGroup` selection property at all. The contract test asserts the
     * guard exists in the source; this asserts the semantics tree it produces.
     */
    @Test fun selectionSemanticsBelongToNavigationRowsAlone() {
        compose.setContent { content(model(BLOOM_MANIFEST)) {} }

        assertEquals(true, selectionOf("home"))
        assertEquals(false, selectionOf("catalog"))
        assertNull("a quick action is an action, not a route", selectionOf("call"))
    }

/**
     * The hub is not the browser's trust surface and must not imitate one.
     *
     * `UX-021` moved `SITESKIN_SECURITY_TAG` onto a chip in the browser-owned header, which stays
     * visible while this drawer is open. A second identity surface inside a site-coloured panel
     * would put the browser's trust mark on a ground the site chose — the colour argument `UX-021`
     * made for the chip, one surface along.
     */
    @Test fun theDrawerCarriesNoBrowserIdentitySurface() {
        compose.setContent { content(model(BLOOM_MANIFEST)) {} }

        compose.onNodeWithTag(SITESKIN_HUB_HEADER_TAG).assertIsDisplayed()
        compose.onNode(hasTestTag(SITESKIN_SECURITY_TAG)).assertDoesNotExist()
    }

    private fun selectionOf(id: String): Boolean? = compose.onNodeWithTag(row(id))
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Selected)

    @androidx.compose.runtime.Composable
    private fun content(model: SiteSkinChromeModel, onDismiss: () -> Unit) {
        HubDrawerContent(
            model = model,
            identity = SiteSkinHubIdentity.from("Bloom", BrandAsset.Monogram("B")),
            colors = colors(),
            onSelect = {},
            onDismiss = onDismiss,
        )
    }

    private fun colors(): SiteSkinColorScheme =
        ExpressiveSiteSkinPresentation.from(CONFIGURATION, false, false).colors

    private fun model(manifest: String) = SiteSkinChromeModel.from(configuration(manifest), BLOOM_ORIGIN)

    private companion object {
        const val BLOOM_ORIGIN = "https://example.com"
        const val MAX_MENU_ENTRIES = 20
        val MINIMUM_TARGET = 48.dp

        fun row(id: String) = "$SITESKIN_HUB_ROW_TAG_PREFIX$id"

        fun configuration(manifest: String) =
            SiteSkinValidator.validate(manifest.byteInputStream(), BLOOM_ORIGIN)
                .let { (it as SiteSkinValidationOutcome.Accepted).configuration }

        val BLOOM_MANIFEST = """
            {"schemaVersion":"1.0","site":{"id":"bloom","name":"Bloom","homeUrl":"/"},
            "bottomNavigation":[
              {"id":"home","label":"Home","icon":"home","action":{"type":"internal_url","url":"/"},
               "match":["/"]},
              {"id":"catalog","label":"Catalog","icon":"catalog","action":{"type":"refresh"}}],
            "quickActions":[
              {"id":"call","label":"Call","icon":"call","action":{"type":"refresh"}}]}
        """.trimIndent()

        val MENU_MANIFEST = buildString {
            append("""{"schemaVersion":"1.0","site":{"id":"bloom","name":"Bloom"},"menu":[""")
            append(
                (1..MAX_MENU_ENTRIES).joinToString(",") {
                    """{"id":"m$it","label":"Menu $it","action":{"type":"refresh"}}"""
                },
            )
            append("]}")
        }

        val CONFIGURATION = configuration(
            """{"schemaVersion":"1.0","site":{"id":"site","name":"Site"}}""",
        )
    }
}
