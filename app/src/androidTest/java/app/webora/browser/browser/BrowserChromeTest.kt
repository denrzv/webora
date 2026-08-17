package app.webora.browser.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import app.webora.browser.design.WeboraTheme
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowserChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun identitySurvivesAddressEditingAndUsesCommittedOrigin() {
        var address = "https://example.com/page"
        compose.setContent {
            WeboraTheme {
                BrowserChrome(
                    state = state(address),
                    onAddressChanged = { address = it },
                    onSubmit = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Search or enter address")
            .performTextReplacement("https://attacker.test")
        compose.onNodeWithTag(BROWSER_SECURITY_TAG).assertIsDisplayed()
        compose.onNodeWithText("Secure · example.com").assertIsDisplayed()
        assertEquals("https://attacker.test", address)
    }

    @Test fun identityReportsObservedTransportRatherThanTheCommittedScheme() {
        // Every row here holds the same https committed origin. Only the browser's observation
        // differs, so a chip that re-derived its label from the scheme would read `Secure` in all
        // four and fail three of them.
        listOf(
            TransportSecurity.SECURE to "Secure · example.com",
            TransportSecurity.NOT_SECURE to "Not secure · example.com",
            TransportSecurity.UNKNOWN to "Not verified · example.com",
            TransportSecurity.TLS_ERROR to "Certificate error · example.com",
        ).forEach { (transport, expected) ->
            compose.setContent {
                WeboraTheme {
                    BrowserChrome(
                        state = state("https://example.com/page", transport = transport),
                        onAddressChanged = {},
                        onSubmit = {},
                    )
                }
            }

            compose.onNodeWithTag(BROWSER_SECURITY_TAG).assertIsDisplayed()
            compose.onNodeWithText(expected).assertIsDisplayed()
        }
    }

    @Test fun aLoadingPageIsNotAnnouncedAsSecure() {
        // The window `UX-021` exists to close, on the surface that already had words for it: while a
        // navigation is in flight the browser has confirmed nothing, and the accessible description
        // must not say otherwise.
        compose.setContent {
            WeboraTheme {
                BrowserChrome(
                    state = state("https://example.com/page", transport = TransportSecurity.UNKNOWN),
                    onAddressChanged = {},
                    onSubmit = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Not verified connection to example.com").assertIsDisplayed()
    }

    @Test fun dockPreservesCommandsAndHistoryEnabledStates() {
        var reload = false
        var home = false
        var tabs = false
        var settings = false
        compose.setContent {
            WeboraTheme {
                BrowserChrome(
                    state = state("https://example.com", canGoBack = false, canGoForward = true),
                    onAddressChanged = {}, onSubmit = {},
                )
                BrowserNavigationDock(
                    canGoBack = false, canGoForward = true, canReload = true, onBack = {}, onForward = {},
                    onReload = { reload = true }, onHome = { home = true }, onTabs = { tabs = true },
                    onSettings = { settings = true }, onInspector = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Forward").assertIsEnabled()
        compose.onNodeWithContentDescription("Reload").performClick()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Tabs").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Settings").performClick()
        assertTrue(reload && home && tabs && settings)
    }

    @Test fun dockDisablesUnavailableReloadAndKeepsEveryTargetAccessible() {
        compose.setContent {
            BrowserNavigationDock(
                canGoBack = false, canGoForward = false, canReload = false,
                onBack = {}, onForward = {}, onReload = {}, onHome = {}, onTabs = {},
                onSettings = {}, onInspector = {},
            )
        }

        listOf("Back", "Forward", "Reload", "Home", "Tabs", "More").forEach { label ->
            compose.onNodeWithContentDescription(label)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription("Reload").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Tabs").assertIsEnabled()
    }

    @Test fun sharedShellContainsEveryTargetAtCompactWidth() {
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                BrowserNavigationShell(
                    false, false, false, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }

        val shell = compose.onNodeWithTag(BROWSER_NAVIGATION_SHELL_TAG).fetchSemanticsNode().boundsInRoot
        listOf("Back", "Forward", "Reload", "Home", "Tabs", "More").forEach { label ->
            val control = compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("$label starts before the shell", control.boundsInRoot.left >= shell.left)
            assertTrue("$label ends after the shell", control.boundsInRoot.right <= shell.right)
        }
    }

    @Test fun compactDockFillsItsPillWithSixEvenSymmetricSlots() {
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                BrowserNavigationShell(false, false, false, {}, {}, {}, {}, {}, {}, {})
            }
        }

        val dock = compose.onNodeWithTag(BROWSER_NAVIGATION_DOCK_TAG).fetchSemanticsNode().boundsInRoot
        val slots = listOf("back", "forward", "reload", "home", "tabs", "more").map { name ->
            compose.onNodeWithTag("$BROWSER_NAVIGATION_SLOT_TAG_PREFIX$name")
                .fetchSemanticsNode().boundsInRoot
        }
        val tolerance = 1f
        val expectedWidth = dock.width / slots.size

        slots.forEach { slot ->
            assertEquals(expectedWidth, slot.width, tolerance)
        }
        slots.zipWithNext().forEach { (left, right) ->
            assertEquals(expectedWidth, right.center.x - left.center.x, tolerance)
        }
        assertEquals(dock.left, slots.first().left, tolerance)
        assertEquals(dock.right, slots.last().right, tolerance)
        assertEquals(
            slots.first().center.x - dock.left,
            dock.right - slots.last().center.x,
            tolerance,
        )
    }

    @Test fun browserOwnedFavouriteActionReflectsStateAndInvokesOnlyBrowserCallback() {
        var toggled = false
        compose.setContent {
            BrowserNavigationDock(
                canGoBack = false, canGoForward = false, canReload = true, onBack = {}, onForward = {},
                onReload = {}, onHome = {}, onTabs = {}, onSettings = {}, onInspector = {},
                isFavourite = true, onToggleFavourite = { toggled = true },
            )
        }

        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Remove favourite").performClick()

        assertTrue(toggled)
    }

    @Test fun errorPagePreservesRecoveryActionsAndBoundedIdentity() {
        var retried = false
        var home = false
        compose.setContent {
            WeboraTheme {
                BrowserErrorPage(
                    failure = BrowserLoadFailure(
                        kind = LoadErrorKind.TLS,
                        registrableDomain = "example.com",
                        retryUrl = "https://example.com/page",
                    ),
                    onRetry = { retried = true },
                    onHome = { home = true },
                )
            }
        }

        compose.onNodeWithText("Webora could not establish a secure connection.").assertIsDisplayed()
        compose.onNodeWithText("example.com").assertIsDisplayed()
        compose.onNodeWithTag(BROWSER_ERROR_RETRY_TAG).performClick()
        compose.onNodeWithTag(BROWSER_ERROR_HOME_TAG).performClick()
        assertTrue(retried && home)
    }

    private fun state(
        address: String,
        canGoBack: Boolean = true,
        canGoForward: Boolean = false,
        // Stated rather than defaulted, because `UX-021` made this the difference between "the
        // browser confirmed this connection" and "the URL starts with https". A test asserting
        // `Secure · example.com` has to say which one it is exercising.
        transport: TransportSecurity = TransportSecurity.SECURE,
    ) = BrowserState(
        mode = BrowserMode.Regular(requireNotNull(SiteOrigin.parse("https://example.com/page"))),
        displayedUrl = "https://example.com/page",
        addressText = address,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        transport = transport,
    )
}
