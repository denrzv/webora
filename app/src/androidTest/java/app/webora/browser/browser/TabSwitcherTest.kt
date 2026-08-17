package app.webora.browser.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import app.webora.browser.design.WeboraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TabSwitcherTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun switcherExposesSelectedTabAndVisibleLimit() {
        val capped = cappedSession()

        compose.setContent {
            WeboraTheme {
                TabSwitcherContent(capped, {}, {}, {}, {})
            }
        }

        compose.onNodeWithTag("$TAB_SELECT_TAG${capped.activeId}").assertIsSelected()
        compose.onNodeWithText("Eight tabs open. Close one to open another.").assertIsDisplayed()
        compose.onNodeWithTag(NEW_TAB_TAG).assertIsNotEnabled()
    }

    @Test fun selectAndCloseAddressDifferentNodesForTheSameTab() {
        // Surface(onClick) merges its descendants, so the two tags would resolve to one node if the
        // close control were nested inside the select affordance. Driving both for the same tab id
        // is what proves they did not merge.
        val session = BrowserSession.fresh().createTab()
        val first = session.tabs.first().id
        var selected: Long? = null
        var closed: Long? = null

        compose.setContent {
            WeboraTheme {
                TabSwitcherContent(session, { selected = it }, { closed = it }, {}, {})
            }
        }

        compose.onNodeWithTag("$TAB_SELECT_TAG$first").performClick()
        compose.onNodeWithTag("$TAB_CLOSE_TAG$first").performClick()

        assertEquals(first, selected)
        assertEquals(first, closed)
    }

    @Test fun closeDismissesTheSwitcher() {
        var open by mutableStateOf(true)
        compose.setContent {
            WeboraTheme {
                if (open) TabSwitcher(BrowserSession.fresh(), {}, {}, {}, { open = false })
            }
        }

        compose.onNodeWithTag(TAB_LIST_TAG).assertIsDisplayed()
        compose.onNodeWithText(CLOSE_LABEL).performClick()

        compose.onNodeWithTag(TAB_LIST_TAG).assertDoesNotExist()
    }

    @Test fun systemBackDismissesTheSwitcher() {
        var dismissals = 0
        var open by mutableStateOf(true)
        compose.setContent {
            WeboraTheme {
                if (open) {
                    TabSwitcher(
                        BrowserSession.fresh(),
                        {},
                        {},
                        {},
                        {
                            dismissals += 1
                            open = false
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag(TAB_LIST_TAG).assertIsDisplayed()
        pressBack()
        compose.waitForIdle()

        assertEquals(1, dismissals)
        compose.onNodeWithTag(TAB_LIST_TAG).assertDoesNotExist()
    }

    private fun cappedSession(): BrowserSession = generateSequence(BrowserSession.fresh()) { it.createTab() }
        .take(BrowserSession.MAX_TABS)
        .last()

    private companion object {
        const val CLOSE_LABEL = "Close"
    }
}
