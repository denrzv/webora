package app.webora.browser.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrivacySettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun globalToggleAndClearActionAreBrowserOwnedControls() {
        var clearRequested = false
        compose.setContent {
            PrivacySettingsScreen(
                siteSkinEnabled = true,
                decisions = emptyList(),
                onSiteSkinEnabledChange = {},
                onRemoveDecision = {},
                onClearBrowsingData = { clearRequested = true },
                onClose = {},
            )
        }

        compose.onNodeWithText("Privacy settings").assertIsDisplayed()
        compose.onNodeWithText("Clear browsing data").performClick()
        assertTrue(clearRequested)
    }

    @Test fun clearDialogRequiresExplicitConfirmation() {
        var confirmed = false
        compose.setContent { ClearBrowsingDataDialog({ confirmed = true }, {}) }

        compose.onNodeWithText("Clear data").performClick()

        assertTrue(confirmed)
    }
}
