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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
                projectedIds = emptySet(),
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
                projectedIds = emptySet(),
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
     * Tapping the scrim closes the drawer and dispatches nothing.
     *
     * **The framework could not do this, which is the whole of `UX-026`.**
     * `DialogProperties.dismissOnClickOutside` is true and honoured, but `DialogWrapper` consults
     * `DialogLayout.isInsideContent`, whose rectangle is the composed content's first child — and
     * that child fills the window. So the branch was unreachable and the only reliable way out of
     * the menu was to select a row, which navigates. Deleting `hubScrim` must fail this case; if it
     * ever passes without it, the tap is landing on something else.
     *
     * The tap is placed at the bottom of the scrim rather than its centre: the panel is top-aligned
     * and bounded below the full height, so the bottom strip is scrim at every content height, where
     * the centre is only scrim for a short enough menu.
     */
    @Test fun tappingTheScrimClosesTheDrawerWithoutDispatching() {
        var visible by mutableStateOf(true)
        var selections = 0
        compose.setContent {
            SiteSkinHubHost(
                visible = visible,
                surface = HubSurface.DRAWER,
                model = model(BLOOM_MANIFEST),
                projectedIds = emptySet(),
                identity = SiteSkinHubIdentity.from("Bloom", BrandAsset.Monogram("B")),
                colors = colors(),
                onSelect = { selections += 1 },
                onDismiss = { visible = false },
            )
        }

        compose.onNodeWithTag(SITESKIN_HUB_SCRIM_TAG).performTouchInput { click(bottomCenter) }

        compose.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).assertDoesNotExist()
        assertEquals("a dismissal is not a selection", 0, selections)
    }

    /**
     * A tap that lands on the panel does not close it.
     *
     * The scrim is the panel's ancestor, so it is on the hit path for panel taps too. Without
     * `consumesPanelTaps` a tap on empty panel space — beside a heading, below the last row — falls
     * through to the ancestor's detector and closes the drawer under the user's finger. The tap here
     * is on the panel's own top-centre, which is header space rather than a row, so a pass would mean
     * the fall-through and not a selection.
     */
    @Test fun tappingThePanelDoesNotCloseIt() {
        var visible by mutableStateOf(true)
        compose.setContent {
            SiteSkinHubHost(
                visible = visible,
                surface = HubSurface.DRAWER,
                model = model(BLOOM_MANIFEST),
                projectedIds = emptySet(),
                identity = SiteSkinHubIdentity.from("Bloom", BrandAsset.Monogram("B")),
                colors = colors(),
                onSelect = {},
                onDismiss = { visible = false },
            )
        }

        compose.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).performTouchInput { click(topCenter) }

        compose.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).assertIsDisplayed()
    }

    /**
     * A short menu does not occupy the viewport, and the assertion is bounds rather than presence.
     *
     * `UX-009` and `UX-023` both record that semantics keep a node's full text and unclipped bounds,
     * so `assertIsDisplayed()` cannot see a sizing defect — it passed over a heading clipped to two
     * characters. Bloom's drawer is a header and two rows; against the emulator's viewport that is
     * well under half, and `HUB_DRAWER_MAX_FRACTION` alone would not produce it, so a panel that
     * still filled the height fails here.
     */
    @Test fun aShortMenuDoesNotOccupyTheViewport() {
        compose.setContent {
            SiteSkinHubHost(
                visible = true,
                surface = HubSurface.DRAWER,
                model = model(BLOOM_MANIFEST),
                projectedIds = emptySet(),
                identity = SiteSkinHubIdentity.from("Bloom", BrandAsset.Monogram("B")),
                colors = colors(),
                onSelect = {},
                onDismiss = {},
            )
        }

        val available = compose.onNodeWithTag(SITESKIN_HUB_SCRIM_TAG).fetchSemanticsNode().size.height
        val panel = compose.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).fetchSemanticsNode().size.height

        assertTrue("the panel measured nothing at all", panel > 0)
        assertTrue(
            "a two-row menu occupied $panel of $available available",
            panel < available / 2,
        )
    }

    /**
     * A long menu is bounded and still reaches its last row.
     *
     * The other half of the same rule: compact height may never be bought by dropping entries.
     * Twenty `menu` entries cannot fit any phone, so the panel must stop at the browser's maximum —
     * strictly under the available height, because a panel with no scrim below it leaves Back as the
     * only way out — and the last row must still be reachable by scrolling.
     */
    @Test fun aLongMenuIsBoundedAndStillScrollsToItsLastRow() {
        val model = model(MENU_MANIFEST)
        compose.setContent {
            SiteSkinHubHost(
                visible = true,
                surface = HubSurface.DRAWER,
                model = model,
                projectedIds = emptySet(),
                identity = SiteSkinHubIdentity.from("Bloom", BrandAsset.Monogram("B")),
                colors = colors(),
                onSelect = {},
                onDismiss = {},
            )
        }

        val available = compose.onNodeWithTag(SITESKIN_HUB_SCRIM_TAG).fetchSemanticsNode().size.height
        val panel = compose.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).fetchSemanticsNode().size.height

        assertTrue("the panel must leave scrim below it at every content height", panel < available)
        compose.onNodeWithTag(row("m$MAX_MENU_ENTRIES")).performScrollTo().assertIsDisplayed()
    }

    /**
     * An empty group reserves nothing — no heading, no spacing, no height.
     *
     * `UX-022` wrote the early return and nothing asserted it, in either suite. That was tolerable
     * while the criterion was about tidiness; `UX-026` makes it **load-bearing for the compactness
     * claim**, because a group that reserved a heading and its spacing for an empty collection adds
     * ~28 dp per empty group to a panel whose whole point is now to be the size of its menu.
     *
     * The reference integration draws exactly this case: Bloom's `menu` is empty, so a regression
     * would read as "the compact drawer is not very compact" with nothing failing anywhere.
     */
    @Test fun anEmptyGroupPublishesNoHeading() {
        val model = model(BLOOM_MANIFEST)
        compose.setContent { content(model) {} }

        assertTrue("the fixture must actually have an empty group", model.siteMenu.isEmpty())
        compose.onNodeWithTag(SITESKIN_HUB_MENU_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SITESKIN_HUB_NAV_TAG).assertIsDisplayed()
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
            projectedIds = emptySet(),
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
