package app.webora.browser.visual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode

/**
 * Scrolls the Home lazy list to text that may not be composed yet, then proves the target is visible.
 *
 * Looking up the child first and calling performScrollTo() only works while that lazy item already
 * has a semantics node. Recent/favourite sections can push Suggested integrations far enough down the
 * Home list that the Bloom card is not composed at all. Driving the scrollable parent lets LazyColumn
 * materialise the target before the test looks it up.
 */
internal fun ComposeContentTestRule.scrollHomeToText(text: String) {
    onNode(hasScrollAction()).performScrollToNode(hasText(text))
    onNodeWithText(text).assertIsDisplayed()
}
