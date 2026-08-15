package app.webora.browser.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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

    @Test fun dockPreservesCommandsAndHistoryEnabledStates() {
        var reload = false
        var home = false
        var settings = false
        compose.setContent {
            WeboraTheme {
                BrowserChrome(
                    state = state("https://example.com", canGoBack = false, canGoForward = true),
                    onAddressChanged = {}, onSubmit = {},
                )
                BrowserNavigationDock(
                    canGoBack = false, canGoForward = true, onBack = {}, onForward = {},
                    onReload = { reload = true }, onHome = { home = true }, onTabs = {},
                    onSettings = { settings = true }, onInspector = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Forward").assertIsEnabled()
        compose.onNodeWithContentDescription("Reload").performClick()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Settings").performClick()
        assertTrue(reload && home && settings)
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

    private fun state(address: String, canGoBack: Boolean = true, canGoForward: Boolean = false) = BrowserState(
        mode = BrowserMode.Regular(requireNotNull(SiteOrigin.parse("https://example.com/page"))),
        displayedUrl = "https://example.com/page",
        addressText = address,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
    )
}
