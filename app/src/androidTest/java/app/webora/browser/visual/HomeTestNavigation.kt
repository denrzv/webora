package app.webora.browser.visual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.webora.browser.browser.HOME_SCREEN_TAG
import app.webora.browser.siteskin.SITESKIN_BACK_TAG
import app.webora.browser.siteskin.SITESKIN_NAV_HUB_TAG

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

/**
 * Walks integrated browser history back to native Home without assuming which surface wins a handoff race.
 *
 * The final Back can replace SiteSkin chrome with native Home asynchronously. Home identity is the
 * root `HOME_SCREEN_TAG`, not [homeText] in a lazily composed child: Recent/Favourites can legitimately
 * push Suggested integrations outside the composed viewport. The text argument is retained only so
 * existing hosted callers keep their human-readable Home label while the synchronization contract is
 * rooted in screen identity. Each iteration accepts exactly two legal continuation states: native Home,
 * which completes the traversal, or an integrated Navigation Hub, which permits another Back.
 */
internal fun ComposeContentTestRule.returnIntegratedHistoryToHome(
    homeText: String,
    maxReturns: Int,
    timeoutMillis: Long,
    settleMillis: Long,
) {
    require(homeText.isNotBlank()) { "Home label must not be blank" }
    val home = hasTestTag(HOME_SCREEN_TAG)
    val navigationHub = hasTestTag(SITESKIN_NAV_HUB_TAG)

    repeat(maxReturns) {
        waitUntil(timeoutMillis) {
            onAllNodes(home).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodes(navigationHub).fetchSemanticsNodes().isNotEmpty()
        }
        if (onAllNodes(home).fetchSemanticsNodes().isNotEmpty()) return

        onNodeWithTag(SITESKIN_NAV_HUB_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitUntil(timeoutMillis) {
            onAllNodes(home).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodes(hasTestTag(SITESKIN_BACK_TAG)).fetchSemanticsNodes().isNotEmpty()
        }
        if (onAllNodes(home).fetchSemanticsNodes().isNotEmpty()) return

        onNodeWithTag(SITESKIN_BACK_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        waitForIdle()
        if (settleMillis > 0) Thread.sleep(settleMillis)
    }

    waitUntil(timeoutMillis) { onAllNodes(home).fetchSemanticsNodes().isNotEmpty() }
}
