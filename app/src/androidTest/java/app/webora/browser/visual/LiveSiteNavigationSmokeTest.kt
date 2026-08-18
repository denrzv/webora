package app.webora.browser.visual

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import app.webora.browser.MainActivity
import app.webora.browser.R
import app.webora.browser.browser.BROWSER_NAVIGATION_SHELL_TAG
import app.webora.browser.browser.BROWSER_SECURITY_TAG
import app.webora.browser.siteskin.EXPRESSIVE_HEADER_TAG
import app.webora.browser.siteskin.SITESKIN_HUB_DRAWER_TAG
import app.webora.browser.siteskin.SITESKIN_BACK_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_BACK_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_FORWARD_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_HUB_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_TAG
import app.webora.browser.siteskin.SITESKIN_SECURITY_TAG
import org.junit.Rule
import org.junit.Test

/**
 * Deep live-site regression coverage that deliberately publishes no screenshots.
 *
 * The canonical screenshot test is a concise product showcase. This test retains the more diagnostic
 * M9 traversal — product navigation, browser Back/Forward, the native action bouquet and exact-origin
 * teardown — so making the visual artifact easier to review does not reduce runtime coverage.
 */
class LiveSiteNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun productHistoryHubAndRegularModeHandoffRemainFunctional() {
        ensureHome()
        openBloomIntegrated()

        val product = findStorefrontProductLink()
        requireNotNull(product) {
            "Bloom did not expose the clickable $PRODUCT_LINK_TEXT link for $PRODUCT_NAME"
        }
        product.click()

        waitForProductLink(isPresent = false)
        requireIntegratedChrome()
        composeRule.onNodeWithTag(SITESKIN_DOCK_BACK_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitForProductLink(isPresent = true)
        requireIntegratedChrome()
        composeRule.onNodeWithTag(SITESKIN_DOCK_FORWARD_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitForProductLink(isPresent = false)
        requireIntegratedChrome()

        composeRule.onNodeWithTag(SITESKIN_DOCK_HUB_TAG).assertIsDisplayed().performClick()
        waitUntilNodeExists(hasTestTag(SITESKIN_HUB_DRAWER_TAG))
        BloomReferenceContract.ACTION_IDS.forEach { id ->
            composeRule.onNodeWithTag(BloomReferenceContract.actionTag(id)).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(
            BloomReferenceContract.actionTag(BloomReferenceContract.HOME_ACTION_ID),
        ).performClick()
        waitUntilNodeAbsent(hasTestTag(SITESKIN_HUB_DRAWER_TAG))
        requireIntegratedChrome()

        returnFromBloomHistoryToHome()
        openRegularSmokeOrigin()
    }

    private fun ensureHome() {
        val skip = string(R.string.onboarding_skip)
        if (composeRule.onAllNodes(hasText(skip)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(skip).performClick()
        }
        if (isHome()) return

        repeat(MAX_HISTORY_RETURNS) {
            val regularHome = hasContentDescription(string(R.string.home))
            when {
                composeRule.onAllNodes(regularHome).fetchSemanticsNodes().isNotEmpty() ->
                    composeRule.onNode(regularHome).performClick()

                composeRule.onAllNodes(hasTestTag(SITESKIN_BACK_TAG)).fetchSemanticsNodes().isNotEmpty() ->
                    composeRule.onNodeWithTag(SITESKIN_BACK_TAG).performClick()
            }
            composeRule.waitForIdle()
            SystemClock.sleep(NAVIGATION_SETTLE_MILLIS)
            if (isHome()) return
        }
        waitUntilNodeExists(hasText(string(R.string.home_suggested_title)))
    }

    private fun openBloomIntegrated() {
        val bloomName = string(R.string.suggested_bloom_name)
        composeRule.onNodeWithText(string(R.string.home_open_site, bloomName))
            .performScrollTo()
            .performClick()

        val consentTitle = string(R.string.siteskin_consent_title, LIVE_ORIGIN)
        composeRule.waitUntil(LIVE_SITE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(consentTitle)).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasTestTag(SITESKIN_SECURITY_TAG)).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasText(consentTitle)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(string(R.string.siteskin_allow)).performClick()
        }
        requireIntegratedChrome()
    }

    private fun findStorefrontProductLink(): UiObject2? {
        val selector = productLinkSelector()
        repeat(MAX_PRODUCT_SCROLL_ATTEMPTS) {
            uiDevice.findObject(selector)?.let { candidate ->
                val bounds = candidate.visibleBounds
                if (bounds.width() > 0 && bounds.height() > 0) return candidate
            }
            scrollStorefrontTowardsProduct()
        }
        return uiDevice.wait(Until.findObject(selector), PRODUCT_LINK_FINAL_WAIT_MILLIS)
            ?.takeIf { candidate ->
                val bounds = candidate.visibleBounds
                bounds.width() > 0 && bounds.height() > 0
            }
    }

    private fun scrollStorefrontTowardsProduct() {
        val width = uiDevice.displayWidth
        val height = uiDevice.displayHeight
        uiDevice.swipe(
            width / 2,
            height * 3 / 4,
            width / 2,
            height * 2 / 5,
            PRODUCT_SCROLL_STEPS,
        )
        SystemClock.sleep(PRODUCT_SCROLL_SETTLE_MILLIS)
    }

    private fun waitForProductLink(isPresent: Boolean) {
        val selector = productLinkSelector()
        val reached = if (isPresent) {
            uiDevice.wait(Until.hasObject(selector), LIVE_SITE_TIMEOUT_MILLIS)
        } else {
            uiDevice.wait(Until.gone(selector), LIVE_SITE_TIMEOUT_MILLIS)
        }
        require(reached) { "Bloom product link presence did not become $isPresent" }
    }

    private fun productLinkSelector() = By.text(PRODUCT_LINK_TEXT).clickable(true)

    private fun requireIntegratedChrome() {
        waitUntilNodeExists(hasTestTag(SITESKIN_SECURITY_TAG))
        waitUntilNodeExists(hasTestTag(EXPRESSIVE_HEADER_TAG))
        waitUntilNodeExists(hasTestTag(SITESKIN_DOCK_TAG))
        composeRule.onNodeWithTag(SITESKIN_SECURITY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(EXPRESSIVE_HEADER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SITESKIN_DOCK_TAG).assertIsDisplayed()
    }

    private fun returnFromBloomHistoryToHome() {
        repeat(MAX_HISTORY_RETURNS) {
            if (isHome()) return
            waitUntilNodeExists(hasTestTag(SITESKIN_BACK_TAG))
            composeRule.onNodeWithTag(SITESKIN_BACK_TAG).performClick()
            composeRule.waitForIdle()
            SystemClock.sleep(NAVIGATION_SETTLE_MILLIS)
        }
        waitUntilNodeExists(hasText(string(R.string.home_suggested_title)))
    }

    private fun openRegularSmokeOrigin() {
        val addressLabel = string(R.string.address_label)
        val addressInput = hasText(addressLabel) or hasContentDescription(addressLabel)
        waitUntilNodeExists(addressInput)
        composeRule.onNode(addressInput).performTextClearance()
        composeRule.onNode(addressInput).performTextInput(REGULAR_SMOKE_ADDRESS)
        composeRule.onNode(addressInput).performImeAction()

        waitUntilNodeExists(hasTestTag(BROWSER_SECURITY_TAG))
        waitUntilNodeExists(hasTestTag(BROWSER_NAVIGATION_SHELL_TAG))
        composeRule.onNodeWithTag(BROWSER_SECURITY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BROWSER_NAVIGATION_SHELL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SITESKIN_SECURITY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(EXPRESSIVE_HEADER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SITESKIN_DOCK_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).assertDoesNotExist()
    }

    private fun isHome(): Boolean =
        composeRule.onAllNodes(hasText(string(R.string.home_suggested_title)))
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun waitUntilNodeExists(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.waitUntil(LIVE_SITE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().size == 1
        }
    }

    private fun waitUntilNodeAbsent(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.waitUntil(LIVE_SITE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun string(id: Int, vararg arguments: Any): String =
        targetContext.getString(id, *arguments)

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val uiDevice by lazy { UiDevice.getInstance(instrumentation) }

    private val targetContext
        get() = instrumentation.targetContext

    private companion object {
        const val LIVE_ORIGIN = "https://denrzv.github.io"
        const val REGULAR_SMOKE_ADDRESS = "example.com"
        const val PRODUCT_NAME = "Happy Days Bouquet"
        const val PRODUCT_LINK_TEXT = "Happy Days"
        const val MAX_HISTORY_RETURNS = 8
        const val MAX_PRODUCT_SCROLL_ATTEMPTS = 5
        const val PRODUCT_SCROLL_STEPS = 24
        const val PRODUCT_SCROLL_SETTLE_MILLIS = 350L
        const val PRODUCT_LINK_FINAL_WAIT_MILLIS = 5_000L
        const val NAVIGATION_SETTLE_MILLIS = 350L
        const val LIVE_SITE_TIMEOUT_MILLIS = 45_000L
    }
}
