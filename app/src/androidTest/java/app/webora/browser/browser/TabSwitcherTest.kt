package app.webora.browser.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.webora.browser.design.WeboraTheme
import org.junit.Rule
import org.junit.Test

class TabSwitcherTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun switcherExposesSelectedTabAndVisibleLimit() {
        val capped = generateSequence(BrowserSession.fresh()) { it.createTab() }
            .take(BrowserSession.MAX_TABS)
            .last()

        compose.setContent {
            WeboraTheme {
                TabSwitcher(capped, {}, {}, {}, {})
            }
        }

        compose.onNodeWithTag("$TAB_SELECT_TAG${capped.activeId}").assertIsSelected()
        compose.onNodeWithText("Eight tabs open. Close one to open another.").assertIsDisplayed()
        compose.onNodeWithTag(NEW_TAB_TAG).assertIsNotEnabled()
    }
}
