package app.webora.browser.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
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

    @Test fun HomeContentAndSharedShellExposeOnlyAvailableBrowserActions() {
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                HomeScreen(onNavigate = {}, modifier = androidx.compose.ui.Modifier.weight(1f))
                BrowserNavigationShell(
                    false, false, false, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }

        compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Forward").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Reload").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Tabs").assertIsDisplayed()
        compose.onNodeWithContentDescription("More").assertIsDisplayed()
    }

    @Test fun populatedRecordsOpenExactStoredUrlsAndFavouriteCanBeRemoved() {
        val opened = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val recent = BrowsingRecord("https://recent.example/path", "https://recent.example", "Recent", 2, 2)
        val favourite = BrowsingRecord("https://saved.example/exact?q=1", "https://saved.example", "Saved", 1, 1)
        compose.setContent {
            HomeScreen(
                onNavigate = opened::add,
                recents = listOf(recent),
                favourites = listOf(favourite),
                onRemoveFavourite = removed::add,
            )
        }

        compose.onNodeWithText("Open Recent").performClick()
        compose.onNodeWithText("Open Saved").performClick()
        compose.onNodeWithText("Remove Saved from favourites").performClick()

        assertEquals(listOf(recent.url, favourite.url), opened)
        assertEquals(listOf(favourite.url), removed)
    }
}
