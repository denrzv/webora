package app.webora.browser.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import app.webora.browser.siteskin.SiteConsentDecision
import app.webora.browser.siteskin.StoredSiteConsent
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrivacySettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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

        compose.onNodeWithText(PRIVACY_TITLE).assertIsDisplayed()
        compose.onNodeWithText("Clear browsing data").performClick()
        assertTrue(clearRequested)
    }

    @Test fun closeDismissesPrivacyDialog() {
        var open by mutableStateOf(true)
        compose.setContent {
            if (open) {
                PrivacySettingsDialog(
                    siteSkinEnabled = true,
                    decisions = emptyList(),
                    onSiteSkinEnabledChange = {},
                    onRemoveDecision = {},
                    onClearBrowsingData = {},
                    onDismiss = { open = false },
                )
            }
        }

        compose.onNodeWithText(PRIVACY_TITLE).assertIsDisplayed()
        compose.onNodeWithText(CLOSE_LABEL).performClick()

        compose.onNodeWithText(PRIVACY_TITLE).assertDoesNotExist()
    }

    @Test fun systemBackDismissesPrivacyDialog() {
        var dismissals = 0
        var open by mutableStateOf(true)
        compose.setContent {
            if (open) {
                PrivacySettingsDialog(
                    siteSkinEnabled = true,
                    decisions = emptyList(),
                    onSiteSkinEnabledChange = {},
                    onRemoveDecision = {},
                    onClearBrowsingData = {},
                    onDismiss = {
                        dismissals += 1
                        open = false
                    },
                )
            }
        }

        pressBack()

        compose.onNodeWithText(PRIVACY_TITLE).assertDoesNotExist()
        assertEquals(1, dismissals)
    }

    @Test fun clearDialogRequiresExplicitConfirmation() {
        var confirmed = false
        compose.setContent { ClearBrowsingDataDialog({ confirmed = true }, {}) }

        compose.onNodeWithText("Clear data").performClick()

        assertTrue(confirmed)
    }

    @Test fun globalToggleIsOneNamedControlWithExplicitState() {
        var enabled = true
        compose.setContent {
            PrivacySettingsScreen(
                siteSkinEnabled = enabled,
                decisions = emptyList(),
                onSiteSkinEnabledChange = { enabled = it },
                onRemoveDecision = {},
                onClearBrowsingData = {},
                onClose = {},
            )
        }

        compose.onNodeWithContentDescription("Allow SiteSkin customisation")
            .assertIsOn()
            .performClick()

        assertEquals(false, enabled)
    }

    @Test fun canonicalOriginStaysVisibleAndNamesItsResetAction() {
        val stored = StoredSiteConsent(
            checkNotNull(SiteOrigin.parse("https://www.example.com:8443")),
            SiteConsentDecision.ALLOW,
        )
        var removed: StoredSiteConsent? = null
        compose.setContent {
            PrivacySettingsScreen(
                siteSkinEnabled = true,
                decisions = listOf(stored),
                onSiteSkinEnabledChange = {},
                onRemoveDecision = { removed = it },
                onClearBrowsingData = {},
                onClose = {},
            )
        }

        compose.onNodeWithText("https://www.example.com:8443").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reset decision for https://www.example.com:8443")
            .performClick()

        assertEquals(stored, removed)
    }

    private companion object {
        const val PRIVACY_TITLE = "Privacy settings"
        const val CLOSE_LABEL = "Close"
    }
}
