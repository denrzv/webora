package app.webora.browser.browser

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.webora.browser.siteskin.SiteSkinConsentModel
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SiteSkinConsentDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun consentDialogNamesExactOriginAndBrowserOwnedBoundary() {
        val origin = requireNotNull(SiteOrigin.parse("https://checkout.shop.example:8443/cart"))
        compose.setContent {
            SiteSkinConsentDialog(
                origin = origin.canonical,
                model = consentModel(),
                onAllow = {},
                onNotNow = {},
                onNever = {},
            )
        }

        compose.onNodeWithText("Allow https://checkout.shop.example:8443 to customise Webora?")
            .assertIsDisplayed()
        compose.onNodeWithText(
            "The site can customise navigation and appearance. " +
                "The address and security indicator stay under Webora control.",
        ).assertIsDisplayed()
    }

    /**
     * `HARDEN-002`'s requirement, pinned where it is read: the complete canonical origin — scheme,
     * host and a non-default port — reaches the frame, and the heading sits inside the window.
     *
     * **This case documents the requirement and is not the regression gate, which is worth stating
     * because it looks like one.** `UX-009` was a container shape clipping this very heading down to
     * `ow`, and every assertion in this file stayed green throughout: clipping happens in the
     * parent's draw, while the semantics tree carries the node's full text and its unclipped bounds
     * either way. That is `CI-003`'s "semantics precede pixels" one layer up. The assertion that
     * fails on that defect is `WeboraThemeTest.a container role never rounds a dialog into a
     * stadium`, which measures the resolved corner radius instead.
     *
     * The width is the device's own. An `AlertDialog` occupies a separate window that a composition
     * size override does not size, so forcing 360 dp here would be a claim this test cannot keep;
     * the screenshot journey's emulator is 360 dp wide and is where that width is actually observed.
     */
    @Test fun consentDialogRendersTheCompleteHeadingWithinTheWindow() {
        val origin = requireNotNull(SiteOrigin.parse("https://checkout.shop.example:8443/cart"))
        compose.setContent {
            SiteSkinConsentDialog(
                origin = origin.canonical,
                model = consentModel(),
                onAllow = {},
                onNotNow = {},
                onNever = {},
            )
        }

        val heading = compose.onNodeWithText("Allow ${origin.canonical} to customise Webora?")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot

        assertTrue("the heading is empty", heading.width > 0f && heading.height > 0f)
        assertTrue(
            "the heading is laid out outside the window: $heading against $window",
            heading.left >= window.left && heading.right <= window.right,
        )
    }

    @Test fun consentDialogExposesThreeBrowserOwnedDecisions() {
        var selected = ""
        compose.setContent {
            SiteSkinConsentDialog(
                origin = "https://shop.example",
                model = consentModel(),
                onAllow = { selected = "allow" },
                onNotNow = { selected = "not-now" },
                onNever = { selected = "never" },
            )
        }

        compose.onNodeWithText("Allow").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        compose.onNodeWithText("Allow").performClick()
        assertEquals("allow", selected)
        compose.onNodeWithText("Not now").performClick()
        assertEquals("not-now", selected)
        compose.onNodeWithText("Never for this site").performClick()

        assertEquals("never", selected)
    }

    @Test fun consentActionsHaveFixedStackedHierarchyAndMinimumTargets() {
        compose.setContent {
            SiteSkinConsentDialog(
                origin = "https://shop.example",
                model = consentModel(),
                onAllow = {},
                onNotNow = {},
                onNever = {},
            )
        }

        val allow = compose.onNodeWithText("Allow").assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val notNow = compose.onNodeWithText("Not now").assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot
        val never = compose.onNodeWithText("Never for this site").assertHeightIsAtLeast(48.dp)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(allow.top < notNow.top)
        assertTrue(notNow.top < never.top)
        assertEquals(allow.width, notNow.width, 1f)
        assertEquals(notNow.width, never.width, 1f)
    }

    @Test fun consentDialogRendersOnlyTheBoundedSiteProjection() {
        val boundedTitle = "A".repeat(64)
        compose.setContent {
            SiteSkinConsentDialog(
                origin = "https://shop.example",
                model = consentModel(title = boundedTitle, subtitle = "Fresh flowers"),
                onAllow = {},
                onNotNow = {},
                onNever = {},
            )
        }

        compose.onNodeWithText("Requested by this site").assertIsDisplayed()
        compose.onNodeWithText(boundedTitle).assertIsDisplayed()
        compose.onNodeWithText("Fresh flowers").assertIsDisplayed()
        compose.onNodeWithText("Sets a brand colour").assertIsDisplayed()
        compose.onNodeWithText("1 navigation tab").assertIsDisplayed()
        compose.onNodeWithText("2 quick actions").assertIsDisplayed()
        compose.onNodeWithText("3 menu items").assertIsDisplayed()
    }

    @Test fun externalUrlRequiresExplicitConfirmation() {
        var confirmed = false
        compose.setContent { ExternalUrlDialog(onConfirm = { confirmed = true }, onDismiss = {}) }

        assertEquals(false, confirmed)
        compose.onNodeWithText("Open").performClick()
        assertEquals(true, confirmed)
    }

    private fun consentModel(
        title: String = "Bloom Flowers",
        subtitle: String? = null,
    ) = SiteSkinConsentModel(
        title = title,
        subtitle = subtitle,
        brandColor = Color.Blue,
        navigationCount = 1,
        quickActionCount = 2,
        menuCount = 3,
    )
}
