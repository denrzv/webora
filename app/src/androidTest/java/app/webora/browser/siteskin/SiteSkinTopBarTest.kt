package app.webora.browser.siteskin

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /**
     * Back is still browser-owned, still browser-observed, and now one tap further in.
     *
     * `UX-024` replaced the standalone tile with the navigation hub, so the assertion opens the hub
     * first. What it asserts is unchanged: Back is present in both history states, enabled only when
     * the browser says so, and dispatches nothing when it is not.
     */
    @Test fun browserBackIsStableAndDispatchesOnlyWhenAvailable() {
        val commands = mutableListOf<BrowserNavigationCommand>()
        compose.setContent { topBar(canGoBack = true, onCommand = { commands += it }) }

        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag(SITESKIN_BACK_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsEnabled().performClick()
        assertEquals(listOf(BrowserNavigationCommand.BACK), commands)

        compose.setContent { topBar(canGoBack = false, onCommand = { commands += it }) }
        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).performClick()
        compose.onNodeWithTag(SITESKIN_BACK_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        assertEquals(listOf(BrowserNavigationCommand.BACK), commands)
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
            compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
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

            // The assertion that catches the inline cap surviving the wrap.
            //
            // `SECURITY_CHIP_FLOOR` is 140 dp and could not: a chip still capped at
            // `SECURITY_CHIP_MAX_WIDTH` measures exactly 160 dp, clears the floor, and shows
            // `example.c…` — the ellipsis this ticket exists to remove, on a green test. Requiring
            // *more* than the inline cap is the direct statement that the cap is no longer binding
            // here, and it is why `SiteSkinIdentityRow` passes `Dp.Unspecified`.
            compose.onNodeWithTag(SITESKIN_SECURITY_TAG)
                .assertWidthIsAtLeast(SECURITY_CHIP_MAX_WIDTH + 1.dp)
        }
    }

    /**
     * The 40 dp `UX-024` returned to the page, measured rather than scanned.
     *
     * `the standalone refresh row is gone and does not return` reads the source and compares the set
     * of rows the header composes against the three it has ever composed. That catches
     * `BrowserControlRow` coming back and would not catch a differently-named second browser row, or
     * a hub that grew vertical chrome of its own. `UX-009` is the precedent: an assertion about where
     * a value *came from* answered a different question from whether it *fits*, and only running the
     * real layout told them apart.
     *
     * The bound is deliberately loose rather than exact. Content is 20 dp of gutter + a 48 dp brand
     * row + 20 dp of reserved curve = 88 dp against `EXPRESSIVE_HEADER_MIN_HEIGHT`'s 96, so the
     * header should measure its floor — but title and subtitle metrics can move the brand row, and
     * an exact assertion would be flaky for a reason unrelated to what it asserts. A second browser
     * row costs 48 dp, so anything under the old 136 dp separates the two layouts with room to spare.
     */
    @Test fun theHeaderNoLongerReservesARowForASingleBrowserCommand() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                Box(Modifier.width(320.dp).testTag(FIXTURE_TAG)) { topBar() }
            }
        }

        val header = compose.onNodeWithTag(EXPRESSIVE_HEADER_TAG).fetchSemanticsNode().boundsInRoot
        val height = with(compose.density) { header.height.toDp() }

        assertTrue(
            "the header must not have regrown a browser control row: measured $height",
            height < TWO_ROW_HEADER_HEIGHT,
        )
        // Paired so the bound cannot pass by the header failing to compose at all — a zero-height
        // node clears any upper bound, which is how an assertion of this shape goes green for the
        // worst possible reason.
        compose.onNodeWithTag(SITESKIN_BRAND_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
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
        // `BROWSE-011`'s requirement, re-pointed by `UX-024` at the surface that now carries it. The
        // trust chip is a status display and Refresh is an action; a screen reader must be able to
        // tell them apart, and the chip must not have become tappable by sharing a header with
        // something that is.
        val commands = mutableListOf<BrowserNavigationCommand>()
        compose.setContent { topBar(onCommand = { commands += it }) }

        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag(SITESKIN_NAV_BOUQUET_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SITESKIN_REFRESH_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Reload").assertIsEnabled().performClick()
        assertEquals(listOf(BrowserNavigationCommand.REFRESH), commands)

        // Same name the regular dock gives this command, and not the chip's sentence.
        compose.onNodeWithTag(SITESKIN_SECURITY_TAG)
            .assertContentDescriptionEquals("Secure connection to example.co.uk")
    }

    @Test fun browserRefreshIsVisibleAndDisabledWithNothingToReload() {
        // Visible-and-disabled, never absent. An absent control moves under the user's finger as
        // state changes; `UX-016` made the same choice for the regular dock. The *hub* is never
        // disabled — it opens whatever the history state — which is `UX-024`'s criterion 2.
        val commands = mutableListOf<BrowserNavigationCommand>()
        compose.setContent { topBar(canRefresh = false, onCommand = { commands += it }) }

        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsEnabled().performClick()
        compose.onNodeWithTag(SITESKIN_REFRESH_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Reload").assertIsNotEnabled()
        assertEquals(emptyList<BrowserNavigationCommand>(), commands)
    }

    /**
     * The hub takes Back's footprint and no more, which is what keeps `UX-023`'s budget intact.
     *
     * `BROWSE-011` gave Refresh its own row because a sixth child in the brand row truncates the
     * domain and measures the site's title to zero. `UX-024` returns Refresh to the leading slot by
     * putting it *behind* the control that was already there — so the row's arithmetic is unchanged
     * and `HEADER_FIXED_WIDTH` did not move. This is the rendered half of that claim: at the same
     * 320 dp and 200% scale the measurement was taken at, the chip still clears `UX-021`'s floor and
     * the collapsed hub keeps a 48 dp target inside the host.
     */
    @Test fun theCollapsedHubDoesNotTakeWidthFromBrowserIdentity() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                Box(Modifier.width(320.dp).testTag(FIXTURE_TAG)) { topBar() }
            }
        }

        val fixture = compose.onNodeWithTag(FIXTURE_TAG).fetchSemanticsNode().boundsInRoot
        val hub = compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG).fetchSemanticsNode().boundsInRoot

        compose.onNodeWithTag(SITESKIN_SECURITY_TAG).assertIsDisplayed().assertWidthIsAtLeast(SECURITY_CHIP_FLOOR)
        compose.onNodeWithTag(SITESKIN_NAV_HUB_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        assertTrue(
            "the collapsed hub must stay inside the 320 dp host: $fixture $hub",
            hub.right <= fixture.right && hub.left >= fixture.left,
        )
    }

    /**
     * `UX-024`: the header's browser commands arrive as one compiled list plus one dispatcher, so
     * the fixture builds them the same way production does — through `browserNavigationActions`,
     * never as a hand-written list, or the test would be exercising a shape the browser never uses.
     */
    @Composable
    @Suppress("LongParameterList")
    private fun topBar(
        asset: BrandAsset = BrandAsset.Monogram("B"),
        canGoBack: Boolean = true,
        canGoForward: Boolean = true,
        canRefresh: Boolean = true,
        onCommand: (BrowserNavigationCommand) -> Unit = {},
        presentation: ExpressiveSiteSkinPresentation = presentation(false, false),
        transport: TransportSecurity = TransportSecurity.SECURE,
    ) {
        var expanded by remember { mutableStateOf(false) }
        SiteSkinTopBar(
            model = model(asset, transport),
            presentation = presentation,
            navigation = BrowserNavigationHubState(
                actions = browserNavigationActions(canGoBack, canGoForward, canRefresh),
                expanded = expanded,
                onExpandedChange = { expanded = it },
                onCommand = onCommand,
            ),
        )
    }

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
        /**
         * What the header measured with `BROWSE-011`'s row: 20 dp gutter + 48 brand + 48 controls +
         * 20 curve. `UX-024` removed the third band, and `EXPRESSIVE_HEADER_MIN_HEIGHT`'s 96 dp floor
         * absorbs the removal, so the two layouts are 40 dp apart and any bound between them works.
         */
        val TWO_ROW_HEADER_HEIGHT = 136.dp

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
