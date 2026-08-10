package app.webora.browser.browser

import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import app.webora.browser.R
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket

class BrowserRecoveryInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<BrowserRecoveryTestActivity>()

    @Test
    fun browserRootKeepsHomeContentBelowStatusBar() {
        val statusBarBottom = composeRule.activity.window.decorView.rootWindowInsets
            .getInsets(WindowInsets.Type.statusBars()).top
        val contentTop = composeRule.onNodeWithText(appName).fetchSemanticsNode().boundsInRoot.top

        assertTrue("Browser content starts beneath the status bar", contentTop >= statusBarBottom)
    }

    @Test
    fun mainFrameConnectionFailureKeepsLiveWebViewBehindRecoveryUi() {
        val failureUrl = closedLoopbackUrl()

        composeRule.onNodeWithText(addressLabel).performTextInput(failureUrl)
        composeRule.onNodeWithText(addressLabel).performImeAction()
        waitForRecoveryUi()

        composeRule.onNodeWithText(errorTitle).assertIsDisplayed()
        composeRule.runOnIdle {
            assertNotNull(findWebView(composeRule.activity.window.decorView))
        }

        composeRule.onNodeWithTag(BROWSER_ERROR_RETRY_TAG).performClick()
        waitForRecoveryUi()

        composeRule.runOnIdle {
            findWebView(composeRule.activity.window.decorView)?.loadDataWithBaseURL(
                SUCCESS_URL,
                SUCCESS_HTML,
                HTML_MIME_TYPE,
                UTF_8,
                null,
            )
        }
        composeRule.waitUntil(ERROR_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(errorTitle)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(errorTitle).assertDoesNotExist()

        composeRule.runOnIdle {
            findWebView(composeRule.activity.window.decorView)?.loadUrl(failureUrl)
        }
        waitForRecoveryUi()
        composeRule.onNodeWithTag(BROWSER_ERROR_HOME_TAG).performClick()
        composeRule.onNodeWithText(appName).assertIsDisplayed()
    }

    private fun waitForRecoveryUi() {
        composeRule.waitUntil(ERROR_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(errorTitle)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private val addressLabel: String
        get() = composeRule.activity.getString(R.string.address_label)

    private val errorTitle: String
        get() = composeRule.activity.getString(R.string.error_title)

    private val appName: String
        get() = composeRule.activity.getString(R.string.app_name)

    private fun closedLoopbackUrl(): String {
        val port = ServerSocket(0).use(ServerSocket::getLocalPort)
        return "https://127.0.0.1:$port/"
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        repeat(view.childCount) { index ->
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private companion object {
        const val SUCCESS_URL = "https://success.test/"
        const val SUCCESS_HTML = "<html><body>Loaded</body></html>"
        const val HTML_MIME_TYPE = "text/html"
        const val UTF_8 = "UTF-8"
        const val ERROR_TIMEOUT_MILLIS = 15_000L
    }
}
