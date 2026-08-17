package app.webora.browser.siteskin

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
                    canGoBack = false,
                    canGoForward = true,
                    onBack = { invoked += "back" },
                    onForward = { invoked += "forward" },
                    siteActions = emptyList(),
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

        val tags = listOf(
            SITESKIN_DOCK_BACK_TAG,
            SITESKIN_DOCK_FORWARD_TAG,
            SITESKIN_DOCK_HUB_TAG,
            SITESKIN_DOCK_TABS_TAG,
            SITESKIN_DOCK_MORE_TAG,
        )
        tags.forEach { compose.onNodeWithTag(it).assertIsDisplayed().assertHeightIsAtLeast(48.dp) }
        compose.onNodeWithTag(SITESKIN_DOCK_HUB_TAG).assertWidthIsEqualTo(BRAND_HUB_TARGET_SIZE)
        compose.onNodeWithTag(BRAND_HUB_IDENTITY_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(SITESKIN_DOCK_BACK_TAG).assertIsNotEnabled()
        tags.drop(1).forEach { compose.onNodeWithTag(it).performClick() }
        assertEquals(listOf("forward", "hub", "tabs", "more"), invoked)
    }

    @Test fun fiveSiteActionsFormABoundedArcAndDismissBeforeTypedSelection() {
        val model = model()
        var dismissed = false
        var selected = ""
        compose.setContent {
            SiteSkinDock(
                presentation = presentation(false, false),
                canGoBack = true,
                canGoForward = true,
                onBack = {},
                onForward = {},
                siteActions = model.actionBouquet(),
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
                presentation(false, false), true, true, {}, {}, model().actionBouquet(), expanded, {},
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
                presentation(false, false), true, true, {}, {}, emptyList(), false, {}, {}, {}, {}, {},
                BrandAsset.BitmapAsset(bitmap),
            )
        }

        compose.onNodeWithTag(SITESKIN_DOCK_HUB_TAG)
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsEqualTo(BRAND_HUB_TARGET_SIZE)
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
