package app.webora.browser.siteskin

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import android.graphics.Bitmap
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SiteSkinDockTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun compactLargeTextKeepsFixedCommandsAndCallbacks() {
        val invoked = mutableListOf<String>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SiteSkinDock(
                    presentation(false, true),
                    arrangement = DockArrangement.BrowserOnly,
                    siteActions = emptyList(),
                    hubSurface = HubSurface.BOUQUET,
                    siteActionsExpanded = false,
                    onSiteActionsToggle = { invoked += "hub" },
                    onSiteActionsDismiss = {},
                    onSiteSelect = {},
                    onTabs = { invoked += "tabs" },
                    onMore = { invoked += "more" },
                    brandAsset = BrandAsset.Monogram("S"),
                )
            }
        }

        // `UX-024`: three slots, not five. Back and Forward moved to the header's navigation hub,
        // where `SiteSkinNavigationHubTest` drives their enabled state and dispatch.
        val tags = listOf(
            SITESKIN_DOCK_HUB_TAG,
            SITESKIN_DOCK_TABS_TAG,
            SITESKIN_DOCK_MORE_TAG,
        )
        tags.forEach { compose.onNodeWithTag(it).assertIsDisplayed().assertHeightIsAtLeast(48.dp) }
        compose.onNodeWithTag(SITESKIN_DOCK_HUB_TAG).assertWidthIsEqualTo(BRAND_HUB_TARGET_SIZE)
        compose.onNodeWithTag(BRAND_HUB_IDENTITY_TAG, useUnmergedTree = true).assertIsDisplayed()
        tags.forEach { compose.onNodeWithTag(it).performClick() }
        assertEquals(listOf("hub", "tabs", "more"), invoked)
    }

    @Test fun fiveSiteActionsFormABoundedArcAndDismissBeforeTypedSelection() {
        val model = model()
        var dismissed = false
        var selected = ""
        compose.setContent {
            SiteSkinDock(
                presentation = presentation(false, false),
                arrangement = DockArrangement.BrowserOnly,
                siteActions = model.actionBouquet(),
                // These cases are about the bouquet, so they name it. `UX-022` made the surface a
                // decision, and a dock test that let the default choose would be testing whichever
                // surface the policy happens to prefer today.
                hubSurface = HubSurface.BOUQUET,
                siteActionsExpanded = true,
                onSiteActionsToggle = {},
                onSiteActionsDismiss = { dismissed = true },
                onSiteSelect = { item ->
                    check(dismissed)
                    selected = item.id
                },
                onTabs = {},
                onMore = {},
                brandAsset = BrandAsset.Monogram("B"),
            )
        }

        val actions = model.actionBouquet()
        actions.forEach { item ->
            compose.onNodeWithTag("$SITESKIN_ACTION_TAG_PREFIX${item.id}")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
        val first = compose.onNodeWithTag("${SITESKIN_ACTION_TAG_PREFIX}home").fetchSemanticsNode().boundsInRoot
        val middle = compose.onNodeWithTag("${SITESKIN_ACTION_TAG_PREFIX}cart").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("${SITESKIN_ACTION_TAG_PREFIX}call").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, last.top, ARC_TOLERANCE)
        org.junit.Assert.assertTrue("the middle petal must rise above the edges", middle.top < first.top)

        compose.onNodeWithTag("${SITESKIN_ACTION_TAG_PREFIX}catalog").performClick()
        assertEquals("catalog", selected)
    }

    @Test fun systemBackDismissesTheFocusableSiteActionBouquet() {
        var expanded by mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            SiteSkinDock(
                presentation(false, false), DockArrangement.BrowserOnly, model().actionBouquet(),
                HubSurface.BOUQUET, expanded, {},
                onSiteActionsDismiss = {
                    dismissals += 1
                    expanded = false
                },
                onSiteSelect = {}, onTabs = {}, onMore = {}, brandAsset = BrandAsset.Monogram("B"),
            )
        }

        pressBack()

        compose.onNodeWithTag(SITESKIN_ACTION_BOUQUET_TAG).assertDoesNotExist()
        assertEquals(1, dismissals)
    }

    @Test fun decodedBitmapKeepsTheSameBrandedHubGeometryAndSemantics() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        compose.setContent {
            SiteSkinDock(
                presentation(false, false), DockArrangement.BrowserOnly, emptyList(),
                HubSurface.BOUQUET, false, {}, {}, {}, {}, {}, BrandAsset.BitmapAsset(bitmap),
            )
        }

        compose.onNodeWithTag(SITESKIN_DOCK_HUB_TAG)
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsEqualTo(BRAND_HUB_TARGET_SIZE)
        compose.onNodeWithTag(BRAND_HUB_IDENTITY_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * The issue's target shape, rendered: `Catalog / Cart / Brand / Account / More`.
     *
     * Five slots at the 320 dp floor give 56.0 dp each — the measurement `UX-015` shipped and
     * `UX-024` recorded when it went the other way — so every slot must still clear the one 48 dp
     * browser target contract. Tabs is deliberately absent: it moved to the More menu, which has
     * offered it since `DEVX-003`.
     */
    @Test fun projectedSiteActionsFillTheNonCentralSlotsAndKeepBrowserTargets() {
        val model = model()
        val arrangement = dockArrangement(model, listOf("catalog", "cart", "profile"))
        var selected = ""
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                SiteSkinDock(
                    presentation = presentation(false, false),
                    arrangement = arrangement,
                    siteActions = model.actionBouquet(),
                    hubSurface = HubSurface.DRAWER,
                    siteActionsExpanded = false,
                    onSiteActionsToggle = {},
                    onSiteActionsDismiss = {},
                    onSiteSelect = { selected = it.id },
                    onTabs = {},
                    onMore = {},
                    brandAsset = BrandAsset.Monogram("B"),
                )
            }
        }

        listOf("catalog", "cart", "profile").forEach { id ->
            compose.onNodeWithTag("$SITESKIN_DOCK_SITE_TAG_PREFIX$id")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
        compose.onNodeWithTag(SITESKIN_DOCK_HUB_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SITESKIN_DOCK_MORE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SITESKIN_DOCK_TABS_TAG).assertDoesNotExist()

        compose.onNodeWithTag("${SITESKIN_DOCK_SITE_TAG_PREFIX}cart").performClick()
        assertEquals("the trusted item is what reaches the dispatcher", "cart", selected)
    }

    /**
     * The hub follows a late-arriving asset instead of keeping the fallback it first drew.
     *
     * `UX-027`'s composed half. The pipeline test proves the loader publishes; this proves a hub
     * already on screen with a monogram picks up the bitmap when it arrives — which is the user-
     * visible symptom of issue #128 and the one thing no JVM test can reach.
     *
     * Asserted as a *change*: the monogram's text must be present first and gone after, because a
     * hub handed the bitmap from the start would satisfy an end-state check while proving nothing.
     */
    @Test fun brandHubFollowsAnAssetThatArrivesAfterFirstComposition() {
        var asset by mutableStateOf<BrandAsset>(BrandAsset.Monogram("B"))
        compose.setContent {
            SiteSkinDock(
                presentation = presentation(false, false),
                arrangement = DockArrangement.BrowserOnly,
                siteActions = emptyList(),
                hubSurface = HubSurface.DRAWER,
                siteActionsExpanded = false,
                onSiteActionsToggle = {},
                onSiteActionsDismiss = {},
                onSiteSelect = {},
                onTabs = {},
                onMore = {},
                brandAsset = asset,
            )
        }

        compose.onNodeWithText("B").assertIsDisplayed()

        asset = BrandAsset.BitmapAsset(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        compose.waitForIdle()

        compose.onNodeWithText("B").assertDoesNotExist()
        compose.onNodeWithTag(BRAND_HUB_IDENTITY_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun presentation(dark: Boolean, reduced: Boolean) =
        ExpressiveSiteSkinPresentation.from(CONFIGURATION, dark, reduced)

    private fun model() = SiteSkinChromeModel.from(
        SiteSkinValidator.validate(BLOOM_MANIFEST.byteInputStream(), BLOOM_ORIGIN)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration },
        BLOOM_ORIGIN,
    )

    private companion object {
        const val BLOOM_ORIGIN = "https://example.com"
        const val ARC_TOLERANCE = 1f
        val BLOOM_MANIFEST = """
            {"schemaVersion":"1.0","site":{"id":"bloom","name":"Bloom"},
            "bottomNavigation":[
              {"id":"home","label":"Home","icon":"home","action":{"type":"home"}},
              {"id":"catalog","label":"Catalog","icon":"catalog","action":{"type":"refresh"}},
              {"id":"cart","label":"Cart","icon":"shopping_cart","action":{"type":"refresh"}},
              {"id":"profile","label":"Profile","icon":"person","action":{"type":"refresh"}}],
            "quickActions":[
              {"id":"call","label":"Call","icon":"call","action":{"type":"refresh"}}]}
        """.trimIndent()
        val CONFIGURATION = SiteSkinValidator.validate(
            """{"schemaVersion":"1.0","site":{"id":"site","name":"Site"}}""".byteInputStream(),
            "https://example.com",
        ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
    }
}
