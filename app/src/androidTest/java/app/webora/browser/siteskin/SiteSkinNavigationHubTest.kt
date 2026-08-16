package app.webora.browser.siteskin

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import app.webora.browser.design.WeboraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SiteSkinNavigationHubTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun browserSheetContainsNoSiteActionsAndDismissesBeforeSelection() {
        var dismissed = false
        var selected: BrowserMenuCommand? = null
        compose.setContent {
            WeboraTheme {
                IntegratedBrowserMenuSheet(
                    commands = browserMenuCommands(),
                    isFavourite = false,
                    onToggleFavourite = {},
                    onSelect = { command ->
                        check(dismissed)
                        selected = command
                    },
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText(WEBORA_CONTROLS).assertIsDisplayed()
        compose.onNodeWithText("Catalog").assertDoesNotExist()
        compose.onNodeWithText("Call").assertDoesNotExist()
        compose.onNodeWithText(SETTINGS).performClick()
        assertEquals(BrowserMenuCommand.SETTINGS, selected)
    }

    @Test fun systemBackDismissesBrowserSheetExactlyOnce() {
        var open by mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            WeboraTheme {
                if (open) {
                    IntegratedBrowserMenuSheet(
                        commands = browserMenuCommands(),
                        isFavourite = false,
                        onToggleFavourite = {},
                        onSelect = {},
                        onDismiss = {
                            dismissals += 1
                            open = false
                        },
                    )
                }
            }
        }

        pressBack()

        compose.onNodeWithText(WEBORA_CONTROLS).assertDoesNotExist()
        assertEquals(1, dismissals)
    }

    private companion object {
        const val WEBORA_CONTROLS = "Webora controls"
        const val SETTINGS = "Settings"
    }
}
