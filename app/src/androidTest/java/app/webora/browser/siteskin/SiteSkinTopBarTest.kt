package app.webora.browser.siteskin

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.webora.browser.browser.SecurityPresentation
import app.webora.browser.browser.TransportSecurity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SiteSkinTopBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun securityIdentityIsVisibleAndBrowserAuthored() {
        compose.setContent { topBar() }

        compose.onNodeWithTag(SITESKIN_SECURITY_TAG)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Secure connection to example.co.uk")
    }

    @Test fun extremeAspectRatioBitmapCannotResizeLogoSlot() {
        val bitmap = Bitmap.createBitmap(1_000, 1, Bitmap.Config.ARGB_8888)
        compose.setContent { topBar(asset = BrandAsset.BitmapAsset(bitmap)) }

        compose.onNodeWithTag(SITESKIN_LOGO_TAG)
            .assertIsDisplayed()
            .assertWidthIsEqualTo(LOGO_SLOT_SIZE)
    }

    @Test fun hostileBrandingCannotReplaceIdentityOrExposeDecorativeLogoSemantics() {
        compose.setContent { topBar() }

        compose.onNodeWithText("B").assertDoesNotExist()
        compose.onNodeWithTag(SITESKIN_SECURITY_TAG)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Secure connection to example.co.uk")
    }

    @Test fun browserBackIsStableAndDispatchesOnlyWhenAvailable() {
        var backCount = 0
        compose.setContent { topBar(canGoBack = true, onBack = { backCount++ }) }

        compose.onNodeWithTag(SITESKIN_BACK_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsEnabled().performClick()
        assertEquals(1, backCount)

        compose.setContent { topBar(canGoBack = false, onBack = { backCount++ }) }
        compose.onNodeWithTag(SITESKIN_BACK_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        assertEquals(1, backCount)
    }

    @Composable
    private fun topBar(
        asset: BrandAsset = BrandAsset.Monogram("B"),
        canGoBack: Boolean = true,
        onBack: () -> Unit = {},
    ) = SiteSkinTopBar(model(asset), colors, canGoBack, onBack)

    private fun model(asset: BrandAsset) = SiteSkinTopBarModel(
        title = "A very long trusted brand title that must not replace security identity",
        subtitle = "Fresh today",
        brandAsset = asset,
        security = SecurityPresentation("example.co.uk", TransportSecurity.SECURE),
    )

    private companion object {
        val colors = SiteSkinColorScheme(
            primary = Color(0xFF3F51B5),
            onPrimary = Color.White,
            secondary = Color(0xFF5C6BC0),
            onSecondary = Color.White,
            background = Color.White,
            onBackground = Color.Black,
        )
    }
}
