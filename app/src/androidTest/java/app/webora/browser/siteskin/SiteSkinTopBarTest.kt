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
import androidx.compose.ui.test.assertWidthIsAtLeast
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

    /**
     * At 200% on a 320 dp host the chip is on its own row, and both it and the title survive.
     *
     * Renamed rather than deleted: `UX-023` moved the identity *out* of the brand row at this exact
     * scale and width, so the old name became a claim the layout no longer makes. Everything it
     * asserted still holds — the chip stays inside the header and inside the host, and the
     * `SECURITY_CHIP_FLOOR` assertion is untouched, which is criterion 6 — and the two additions are
     * what the wrap is for: the domain renders without an ellipsis, and the site's title is still
     * there rather than measured to zero.
     */
    @Test fun compactLargeTextGivesBrowserIdentityItsOwnRow() {
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
            // A real floor, not `> 0f`: that passed on a chip showing one character and an
            // ellipsis, which is close to the bare-shield layout this ticket deliberately rejected.
            // 160 dp is what the chip resolves to here (cap-bound, with 164 dp available), so 140
            // leaves margin while failing if the cap or the row budget is meaningfully shrunk.
            compose.onNodeWithTag(SITESKIN_SECURITY_TAG).assertWidthIsAtLeast(SECURITY_CHIP_FLOOR)

            // `UX-023`'s two criteria, and the reason the wrap exists. Inline, these fought over
            // 164 dp: the chip truncated to roughly `example.c…` and the title measured to zero.
            val brand = compose.onNodeWithTag(SITESKIN_BRAND_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "the chip must sit below the brand row, not inside it: $brand $identity",
                identity.top >= brand.bottom,
            )
            compose.onNodeWithText(LONG_TITLE, substring = true)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(TITLE_FLOOR)
        }
    }

    /**
     * The anti-vacuity guard for the wrap: at 100% it must **not** fire.
     *
     * A rule that always wrapped would satisfy every assertion in the case above while adding a
     * permanent row to the layout everyone else sees. This is the case that fails if
     * `headerIdentityPlacement` is made unconditional, and it is deliberately the same fixture at
     * the same width — only the font scale differs, so the scale is provably what moved the chip.
     */
    @Test fun defaultTextKeepsBrowserIdentityInsideTheBrandRow() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                Box(Modifier.width(320.dp).testTag(FIXTURE_TAG)) {
                    topBar(presentation = presentation(darkTheme = false, reducedMotion = false))
                }
            }
        }

        val brand = compose.onNodeWithTag(SITESKIN_BRAND_TAG).fetchSemanticsNode().boundsInRoot
        val identity = compose.onNodeWithTag(SITESKIN_SECURITY_TAG).fetchSemanticsNode().boundsInRoot

        compose.onNodeWithTag(SITESKIN_SECURITY_TAG).assertIsDisplayed()
        assertTrue(
            "at default scale the chip belongs inside the brand row: $brand $identity",
            identity.top >= brand.top && identity.bottom <= brand.bottom,
        )
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

    @Test fun browserRefreshIsAnAccessibleBrowserActionDistinctFromTheTrustMark() {
        // Issue requirements 9 and 12. The trust chip is a status display and the refresh control is
        // an action; a screen reader must be able to tell them apart, and the chip must not have
        // become tappable by sharing a row with something that is.
        var refreshes = 0
        compose.setContent { topBar(onRefresh = { refreshes++ }) }

        compose.onNodeWithTag(SITESKIN_CONTROLS_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SITESKIN_REFRESH_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Reload").assertIsEnabled().performClick()
        assertEquals(1, refreshes)

        // Same name the regular dock gives this command, and not the chip's sentence.
        compose.onNodeWithTag(SITESKIN_SECURITY_TAG)
            .assertContentDescriptionEquals("Secure connection to example.co.uk")
    }

    @Test fun browserRefreshIsVisibleAndDisabledWithNothingToReload() {
        // Issue requirement 7: visible-and-disabled, never absent. An absent control moves under
        // the user's finger as state changes; `UX-016` made the same choice for the regular dock.
        var refreshes = 0
        compose.setContent { topBar(canRefresh = false, onRefresh = { refreshes++ }) }

        compose.onNodeWithTag(SITESKIN_REFRESH_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Reload").assertIsNotEnabled()
        assertEquals(0, refreshes)
    }

    @Test fun theControlRowDoesNotTakeWidthFromBrowserIdentity() {
        // The whole reason `BROWSE-011` put Refresh on its own line. If it had gone in the brand
        // row, the chip would truncate and the site's title would measure to zero at this width —
        // so the assertion that matters is that the chip's floor from `UX-021` still holds with the
        // control composed, at the same 320 dp and 200% the measurement was taken at.
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                Box(Modifier.width(320.dp).testTag(FIXTURE_TAG)) { topBar() }
            }
        }

        val fixture = compose.onNodeWithTag(FIXTURE_TAG).fetchSemanticsNode().boundsInRoot
        val refresh = compose.onNodeWithTag(SITESKIN_REFRESH_TAG).fetchSemanticsNode().boundsInRoot

        compose.onNodeWithTag(SITESKIN_SECURITY_TAG).assertIsDisplayed().assertWidthIsAtLeast(SECURITY_CHIP_FLOOR)
        compose.onNodeWithTag(SITESKIN_REFRESH_TAG).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        assertTrue(
            "the refresh control must stay inside the 320 dp host: $fixture $refresh",
            refresh.right <= fixture.right && refresh.left >= fixture.left,
        )
    }

    @Composable
    private fun topBar(
        asset: BrandAsset = BrandAsset.Monogram("B"),
        canGoBack: Boolean = true,
        onBack: () -> Unit = {},
        presentation: ExpressiveSiteSkinPresentation = presentation(false, false),
        transport: TransportSecurity = TransportSecurity.SECURE,
        canRefresh: Boolean = true,
        onRefresh: () -> Unit = {},
    ) = SiteSkinTopBar(model(asset, transport), presentation, canGoBack, onBack, canRefresh, onRefresh)

    private val SECURITY_CHIP_FLOOR = 140.dp

    private fun presentation(darkTheme: Boolean, reducedMotion: Boolean) =
        ExpressiveSiteSkinPresentation.from(CONFIGURATION, darkTheme, reducedMotion)

    private fun model(asset: BrandAsset, transport: TransportSecurity = TransportSecurity.SECURE) =
        SiteSkinTopBarModel(
            title = LONG_TITLE,
            subtitle = "Fresh today",
            brandAsset = asset,
            security = SecurityPresentation("example.co.uk", transport),
        )

    private companion object {
        const val FIXTURE_TAG = "siteskin_top_fixture"
        const val LONG_TITLE = "A very long trusted brand title that must not replace security identity"

        /**
         * A real floor for the site's title, for `UX-021`'s reason one element along.
         *
         * At 200% on a 320 dp host the wrapped layout leaves the title column 164 dp, so 100 dp
         * leaves margin while failing if the chip ever competes for that width again. It has to be a
         * bounds assertion rather than `onNodeWithText(...).assertIsDisplayed()`: `UX-009` recorded
         * that clipping happens in the parent's draw while the semantics tree keeps the node's full
         * text and unclipped bounds, so a text-presence check passes over a title that measured to
         * zero — which is precisely the failure this ticket removes.
         */
        val TITLE_FLOOR = 100.dp
        val CONFIGURATION = SiteSkinValidator.validate(
            """{"schemaVersion":"1.0","site":{"id":"site","name":"Site"},"branding":{"primaryColor":"#3F51B5","secondaryColor":"#5C6BC0","backgroundColor":"#FFFFFF","textColor":"#000000"}}"""
                .byteInputStream(),
            "https://example.co.uk",
        ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
    }
}
