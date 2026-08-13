package app.webora.browser.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addressAndSuggestionActionsResolveToSafeDestinations() {
        val navigations = mutableListOf<String>()
        compose.setContent { HomeScreen(onNavigate = navigations::add) }

        compose.onNodeWithText("Search or enter address").performTextInput("example.com")
        compose.onNodeWithText("Search or enter address").performImeAction()
        compose.onNodeWithText("Open Bloom Flowers").performClick()

        assertEquals(listOf("https://example.com", "https://denrzv.github.io/"), navigations)
    }

    @Test fun emptyBrowserOwnedSectionsRemainVisible() {
        compose.setContent { HomeScreen(onNavigate = {}) }

        compose.onNodeWithText("Recent sites").assertIsDisplayed()
        compose.onNodeWithText("Favourites").assertIsDisplayed()
        compose.onNodeWithText("Suggested integrations").assertIsDisplayed()
    }
}
