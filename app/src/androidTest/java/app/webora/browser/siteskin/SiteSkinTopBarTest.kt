package app.webora.browser.siteskin

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.webora.browser.browser.SecurityPresentation
import app.webora.browser.browser.TransportSecurity
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun compactLargeTextKeepsOwnedBrandIdentityAndBackSeparate() {
        listOf(false to false, true to false, true to true).forEach { (darkTheme, reducedMotion) ->
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    Box(Modifier.width(320.dp).testTag(FIXTURE_TAG)) {
                        topBar(presentation = presentation(darkTheme, reducedMotion))
                    }
                }
            }

            compose.onNodeWithTag(EXPRESSIVE_HEADER_TAG).assertIsDisplayed()
            compose.onNodeWithTag(SITESKIN_BACK_TAG).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag(SITESKIN_BRAND_TAG).assertIsDisplayed()
            compose.onNodeWithTag(SITESKIN_SECURITY_TAG).assertIsDisplayed()

            // `UX-021` moved the identity into the brand row, so the old assertion — identity
            // strictly below brand — describes a layout that no longer exists. What replaced it is
            // the stronger question the move raises: the title is manifest-supplied and deliberately
            // longer than the header, so does the site's text push the browser's trust mark out?
            val header = compose.onNodeWithTag(EXPRESSIVE_HEADER_TAG).fetchSemanticsNode().boundsInRoot
            val identity = compose.onNodeWithTag(SITESKIN_SECURITY_TAG).fetchSemanticsNode().boundsInRoot
            val fixture = compose.onNodeWithTag(FIXTURE_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "identity must stay inside the header for dark=$darkTheme reduced=$reducedMotion: " +
                    "$header $identity",
                identity.left >= header.left && identity.right <= header.right,
            )
            assertTrue(
                "identity must stay inside the 320 dp host, not merely inside a header that overflows " +
                    "it: $fixture $identity",
                identity.right <= fixture.right,
            )
            assertTrue("identity must have width at 200% font scale: $identity", identity.width > 0f)
        }
    }

    @Test fun everyTransportStateKeepsTheBrowserAuthoredIdentityNode() {
        // Issue requirement 8, and the reason the node is tagged rather than recognised by colour:
        // the shield's tint changes across these four, the description changes with it, and neither
        // is anything the manifest in `CONFIGURATION` can reach. The same hostile title is present
        // in every row.
        listOf(
            TransportSecurity.SECURE to "Secure connection to example.co.uk",
            TransportSecurity.NOT_SECURE to "Not secure connection to example.co.uk",
            TransportSecurity.UNKNOWN to "Not verified connection to example.co.uk",
            TransportSecurity.TLS_ERROR to "Certificate error connection to example.co.uk",
        ).forEach { (transport, description) ->
            compose.setContent { topBar(transport = transport) }

            compose.onNodeWithTag(SITESKIN_SECURITY_TAG)
                .assertIsDisplayed()
                .assertContentDescriptionEquals(description)
        }
    }

    @Test fun theRegistrableDomainStaysVisibleInIntegratedChrome() {
        // `ADR-006`: the domain is *visible*, not merely announced. Issue #104's target layout drops
        // it and this implementation deliberately does not — a coloured glyph beside a
        // manifest-supplied title and logo would be the only contradicting signal on screen, which
        // is the exact scenario `ADR-006` exists to prevent. Asserted as displayed text, because a
        // `contentDescription` alone would satisfy an implementation that had dropped the pixels.
        compose.setContent { topBar() }

        compose.onNodeWithText("example.co.uk").assertIsDisplayed()
    }

    @Composable
    private fun topBar(
        asset: BrandAsset = BrandAsset.Monogram("B"),
        canGoBack: Boolean = true,
        onBack: () -> Unit = {},
        presentation: ExpressiveSiteSkinPresentation = presentation(false, false),
        transport: TransportSecurity = TransportSecurity.SECURE,
    ) = SiteSkinTopBar(model(asset, transport), presentation, canGoBack, onBack)

    private fun presentation(darkTheme: Boolean, reducedMotion: Boolean) =
        ExpressiveSiteSkinPresentation.from(CONFIGURATION, darkTheme, reducedMotion)

    private fun model(asset: BrandAsset, transport: TransportSecurity = TransportSecurity.SECURE) =
        SiteSkinTopBarModel(
            title = "A very long trusted brand title that must not replace security identity",
            subtitle = "Fresh today",
            brandAsset = asset,
            security = SecurityPresentation("example.co.uk", transport),
        )

    private companion object {
        const val FIXTURE_TAG = "siteskin_top_fixture"
        val CONFIGURATION = SiteSkinValidator.validate(
            """{"schemaVersion":"1.0","site":{"id":"site","name":"Site"},"branding":{"primaryColor":"#3F51B5","secondaryColor":"#5C6BC0","backgroundColor":"#FFFFFF","textColor":"#000000"}}"""
                .byteInputStream(),
            "https://example.co.uk",
        ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
    }
}
