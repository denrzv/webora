package app.webora.browser.browser

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allPagesProgressToCompletion() {
        var completions = 0
        compose.setContent { OnboardingScreen(onComplete = { completions += 1 }) }

        compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 3 of 3").assertIsDisplayed()
        compose.onNodeWithText("Start browsing").performClick()

        assertEquals(1, completions)
    }

    @Test fun skipCompletesWithoutAdvancing() {
        var completions = 0
        compose.setContent { OnboardingScreen(onComplete = { completions += 1 }) }

        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, completions)
    }
}
