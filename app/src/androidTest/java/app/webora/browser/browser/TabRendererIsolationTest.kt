package app.webora.browser.browser

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import app.webora.browser.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket

/**
 * A renderer belongs to one tab, and a failed page in one tab is not another tab's problem.
 *
 * Instrumented **evidence**, never a gate claim — `A11Y-001`'s rule, and this suite needs a device.
 * The addressing half of `BROWSE-009` is proven in the JVM gate by `RendererOwnershipTest`; what can
 * only be observed here is the half that lives in the view hierarchy: that `key(tabId)` gives each
 * tab its own `AndroidView` host, and that `detachFromParent` lets a retained `WebView` be adopted
 * by a new host when its tab is selected again.
 */
class TabRendererIsolationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<BrowserRecoveryTestActivity>()

    @Test
    fun eachTabKeepsItsOwnRendererAndItsOwnFailureAcrossSwitches() {
        // Tab A fails on an unreachable address — the reported reproduction's starting state.
        navigate(closedLoopbackUrl())
        waitForErrorPage()
        openTabs()
        composeRule.onNodeWithTag(NEW_TAB_TAG).performClick()

        // Tab B reaches regular mode through the same failure, then renders real content, so the
        // two tabs differ in every observable way: B has a page, A has an error.
        composeRule.onNodeWithText(appName).assertIsDisplayed()
        navigate(closedLoopbackUrl())
        waitForErrorPage()
        loadSuccessPage()
        waitForNoErrorPage()

        // A → its own error page comes back, and B's renderer is not the one on screen.
        selectTab(FIRST_TAB)
        waitForErrorPage()

        // B → its own page comes back without reloading, which is what reattaching the retained
        // renderer means. A `WebView` that kept its parent would have thrown before reaching here.
        selectTab(SECOND_TAB)
        waitForNoErrorPage()
        composeRule.runOnIdle {
            assertEquals(SUCCESS_URL, attachedWebViews().single().url)
        }
    }

    @Test
    fun exactlyOneRendererIsAttachedAfterEverySwitch() {
        // Two live tabs mean two retained WebViews, and at most one of them may be in the window.
        // A second attached renderer is a page drawing where the selected tab's page should be.
        navigate(closedLoopbackUrl())
        waitForErrorPage()
        openTabs()
        composeRule.onNodeWithTag(NEW_TAB_TAG).performClick()
        navigate(closedLoopbackUrl())
        waitForErrorPage()

        selectTab(FIRST_TAB)
        waitForErrorPage()
        composeRule.runOnIdle { assertEquals(1, attachedWebViews().size) }

        selectTab(SECOND_TAB)
        waitForErrorPage()
        composeRule.runOnIdle { assertEquals(1, attachedWebViews().size) }
    }

    @Test
    fun closingTheFailedTabLeavesTheHealthyTabRendered() {
        navigate(closedLoopbackUrl())
        waitForErrorPage()
        openTabs()
        composeRule.onNodeWithTag(NEW_TAB_TAG).performClick()
        navigate(closedLoopbackUrl())
        waitForErrorPage()
        loadSuccessPage()
        waitForNoErrorPage()

        openTabs()
        composeRule.onNodeWithTag("$TAB_CLOSE_TAG$FIRST_TAB").performClick()
        composeRule.onNodeWithText(closeLabel).performClick()

        waitForNoErrorPage()
        composeRule.runOnIdle {
            val remaining = attachedWebViews()
            assertEquals(1, remaining.size)
            assertEquals(SUCCESS_URL, remaining.single().url)
        }
    }

    @Test
    fun refreshingOneTabLeavesTheOtherTabsPageAndStateAlone() {
        // `BROWSE-011`. A browser command reaching the wrong renderer is `BROWSE-009`'s defect
        // arriving through a new control, and the state that makes it visible is a *failed* tab:
        // its refresh re-issues a navigation, so a mis-addressed one replaces the other tab's page
        // rather than quietly re-fetching the same bytes.
        val failing = closedLoopbackUrl()
        navigate(failing)
        waitForErrorPage()

        // Tab B ends up with real content at a different URL, so any leak from A is observable.
        openTabs()
        composeRule.onNodeWithTag(NEW_TAB_TAG).performClick()
        navigate(closedLoopbackUrl())
        waitForErrorPage()
        loadSuccessPage()
        waitForNoErrorPage()

        selectTab(FIRST_TAB)
        waitForErrorPage()
        composeRule.onNodeWithContentDescription(reloadLabel).performClick()

        // Switched away while A's retry is still in flight: the late callbacks it produces are
        // addressed to A by the id fixed when its renderer was built, and must not settle on B.
        selectTab(SECOND_TAB)
        waitForNoErrorPage()
        composeRule.runOnIdle {
            assertEquals(SUCCESS_URL, attachedWebViews().single().url)
        }

        // And A's own failure is still A's, re-observed through the ordinary event pipeline.
        selectTab(FIRST_TAB)
        waitForErrorPage()

        selectTab(SECOND_TAB)
        waitForNoErrorPage()
        composeRule.runOnIdle {
            assertEquals(1, attachedWebViews().size)
            assertEquals(SUCCESS_URL, attachedWebViews().single().url)
        }
    }

    private fun navigate(url: String) {
        composeRule.onNodeWithText(addressLabel).performTextInput(url)
        composeRule.onNodeWithText(addressLabel).performImeAction()
    }

    private fun openTabs() {
        composeRule.onNodeWithContentDescription(tabsLabel).performClick()
        composeRule.onNodeWithTag(TAB_LIST_TAG).assertIsDisplayed()
    }

    private fun selectTab(id: Long) {
        openTabs()
        composeRule.onNodeWithTag("$TAB_SELECT_TAG$id").performClick()
    }

    /** Replaces the failure with real content, the way `BrowserRecoveryInstrumentedTest` does. */
    private fun loadSuccessPage() {
        composeRule.runOnIdle {
            attachedWebViews().single().loadDataWithBaseURL(SUCCESS_URL, SUCCESS_HTML, HTML_MIME_TYPE, UTF_8, null)
        }
    }

    private fun waitForErrorPage() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(errorTitle)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(errorTitle).assertIsDisplayed()
    }

    private fun waitForNoErrorPage() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(errorTitle)).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Every `WebView` currently in the window — the ones a detach did *not* remove. */
    private fun attachedWebViews(): List<WebView> =
        collectWebViews(composeRule.activity.window.decorView, mutableListOf())

    private fun collectWebViews(view: View, into: MutableList<WebView>): List<WebView> {
        if (view is WebView) into += view
        if (view is ViewGroup) repeat(view.childCount) { collectWebViews(view.getChildAt(it), into) }
        return into
    }

    private fun closedLoopbackUrl(): String {
        val port = ServerSocket(0).use(ServerSocket::getLocalPort)
        return "https://127.0.0.1:$port/"
    }

    private val addressLabel: String get() = composeRule.activity.getString(R.string.address_label)
    private val errorTitle: String get() = composeRule.activity.getString(R.string.error_title)
    private val appName: String get() = composeRule.activity.getString(R.string.app_name)
    private val tabsLabel: String get() = composeRule.activity.getString(R.string.tabs)
    private val closeLabel: String get() = composeRule.activity.getString(R.string.close)

    /** The one name this command has, in the regular dock and the integrated header alike. */
    private val reloadLabel: String get() = composeRule.activity.getString(R.string.reload)

    private companion object {
        const val FIRST_TAB = 1L
        const val SECOND_TAB = 2L
        const val SUCCESS_URL = "https://success.test/"
        const val SUCCESS_HTML = "<html><body>Loaded</body></html>"
        const val HTML_MIME_TYPE = "text/html"
        const val UTF_8 = "UTF-8"
        const val TIMEOUT_MILLIS = 15_000L
    }
}
