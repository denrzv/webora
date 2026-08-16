package app.webora.browser.siteskin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SiteSkinDockTest {
    @get:Rule val compose = createComposeRule()

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
                    onOpenHub = { invoked += "hub" },
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
        compose.onNodeWithTag(BRAND_HUB_IDENTITY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SITESKIN_DOCK_BACK_TAG).assertIsNotEnabled()
        tags.drop(1).forEach { compose.onNodeWithTag(it).performClick() }
        assertEquals(listOf("forward", "hub", "tabs", "more"), invoked)
    }

    @Test fun decodedBitmapKeepsTheSameBrandedHubGeometryAndSemantics() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        compose.setContent {
            SiteSkinDock(
                presentation(false, false), true, true, {}, {}, {}, {}, {},
                BrandAsset.BitmapAsset(bitmap),
            )
        }

        compose.onNodeWithTag(SITESKIN_DOCK_HUB_TAG)
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsEqualTo(BRAND_HUB_TARGET_SIZE)
        compose.onNodeWithTag(BRAND_HUB_IDENTITY_TAG).assertIsDisplayed()
    }

    private fun presentation(dark: Boolean, reduced: Boolean) =
        ExpressiveSiteSkinPresentation.from(CONFIGURATION, dark, reduced)

    private companion object {
        val CONFIGURATION = SiteSkinValidator.validate(
            """{"schemaVersion":"1.0","site":{"id":"site","name":"Site"}}""".byteInputStream(),
            "https://example.com",
        ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
    }
}
