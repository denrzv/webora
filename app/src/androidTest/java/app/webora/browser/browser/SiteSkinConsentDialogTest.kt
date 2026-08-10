package app.webora.browser.browser

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SiteSkinConsentDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun consentDialogNamesExactOriginAndBrowserOwnedBoundary() {
        compose.setContent {
            SiteSkinConsentDialog(
                origin = "https://checkout.shop.example:8443",
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

    @Test fun consentDialogExposesThreeBrowserOwnedDecisions() {
        var selected = ""
        compose.setContent {
            SiteSkinConsentDialog(
                origin = "https://shop.example",
                onAllow = { selected = "allow" },
                onNotNow = { selected = "not-now" },
                onNever = { selected = "never" },
            )
        }

        compose.onNodeWithText("Allow").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        compose.onNodeWithText("Never for this site").performClick()

        assertEquals("never", selected)
    }

    @Test fun externalUrlRequiresExplicitConfirmation() {
        var confirmed = false
        compose.setContent { ExternalUrlDialog(onConfirm = { confirmed = true }, onDismiss = {}) }

        assertEquals(false, confirmed)
        compose.onNodeWithText("Open").performClick()
        assertEquals(true, confirmed)
    }
}
