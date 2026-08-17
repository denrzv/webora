package app.webora.browser.visual

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.webora.browser.MainActivity
import app.webora.browser.R
import app.webora.browser.browser.BROWSER_CONTENT_TAG
import app.webora.browser.browser.BROWSER_NAVIGATION_SHELL_TAG
import app.webora.browser.browser.BROWSER_SECURITY_TAG
import app.webora.browser.inspector.BrandAssetStage
import app.webora.browser.inspector.BrandAssetTrace
import app.webora.browser.inspector.inspectorRecorder
import app.webora.browser.siteskin.EXPRESSIVE_HEADER_TAG
import app.webora.browser.siteskin.SITESKIN_ACTION_BOUQUET_TAG
import app.webora.browser.siteskin.SITESKIN_BACK_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_HUB_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_TAG
import app.webora.browser.siteskin.SITESKIN_QUICK_ACTIONS_TAG
import app.webora.browser.siteskin.SITESKIN_SECURITY_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Human-facing canonical visual story. Deeper navigation regression coverage lives in the smoke test. */
class LiveSiteScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesCanonicalBrowserShowcase() {
        composeRule.onNodeWithText(string(R.string.onboarding_skip)).performClick()
        composeRule.onNodeWithText(string(R.string.home_suggested_title)).assertIsDisplayed()
        captureDeviceScreenshot("01-home.png")

        val bloomName = string(R.string.suggested_bloom_name)
        composeRule.onNodeWithText(string(R.string.home_open_site, bloomName))
            .performScrollTo()
            .performClick()

        val consentTitle = string(R.string.siteskin_consent_title, LIVE_ORIGIN)
        waitUntilNodeExists(hasText(consentTitle))
        composeRule.onNodeWithText(consentTitle).assertIsDisplayed()
        captureDeviceScreenshot("02-siteskin-consent.png")

        composeRule.onNodeWithText(string(R.string.siteskin_allow)).performClick()
        requireIntegratedChrome()
        val brandAsset = awaitBrandAssetDecision()
        guard.recordDiagnostic("brand-asset-03-bloom-storefront.txt", describe(brandAsset))
        captureDeviceScreenshot("03-bloom-storefront.png", requirePageContent = true)
        // Keep the frame even when the brand asset failed so the artifact shows the failure state.
        assertEquals(
            "the reference integration's logo did not reach the 40 dp slot; see " +
                "diagnostics/brand-asset-03-bloom-storefront.txt",
            BrandAssetStage.DECODED,
            brandAsset?.stage,
        )

        captureBloomActionBouquet()
        captureBloomProfile()
        captureGoogleRegularBrowsing()
    }

    private fun captureBloomActionBouquet() {
        composeRule.onNodeWithTag(SITESKIN_DOCK_HUB_TAG).assertIsDisplayed().performClick()
        waitUntilNodeExists(hasTestTag(SITESKIN_ACTION_BOUQUET_TAG))
        BloomReferenceContract.ACTION_IDS.forEach { id ->
            composeRule.onNodeWithTag(BloomReferenceContract.actionTag(id)).assertIsDisplayed()
        }
        captureDeviceScreenshot("04-bloom-actions.png", requirePageContent = true)
    }

    private fun captureBloomProfile() {
        composeRule.onNodeWithTag(
            BloomReferenceContract.actionTag(BloomReferenceContract.PROFILE_ACTION_ID),
        )
            .assertIsDisplayed()
            .performClick()
        waitUntilNodeAbsent(hasTestTag(SITESKIN_ACTION_BOUQUET_TAG))
        require(
            uiDevice.wait(Until.hasObject(By.text(PROFILE_PAGE_HEADING)), LIVE_SITE_TIMEOUT_MILLIS),
        ) { "Bloom did not expose the $PROFILE_PAGE_HEADING profile page" }
        requireIntegratedChrome()
        captureDeviceScreenshot("05-bloom-profile.png", requirePageContent = true)
    }

    private fun captureGoogleRegularBrowsing() {
        returnFromBloomHistoryToHome()

        val addressLabel = string(R.string.address_label)
        val addressInput = hasText(addressLabel) or hasContentDescription(addressLabel)
        waitUntilNodeExists(addressInput)
        composeRule.onNode(addressInput).performTextClearance()
        composeRule.onNode(addressInput).performTextInput(REGULAR_ADDRESS)
        composeRule.onNode(addressInput).performImeAction()

        waitUntilNodeExists(hasTestTag(BROWSER_SECURITY_TAG))
        waitUntilNodeExists(hasTestTag(BROWSER_NAVIGATION_SHELL_TAG))
        composeRule.onNodeWithTag(BROWSER_SECURITY_TAG).assertIsDisplayed()
        val secure = string(R.string.security_secure)
        composeRule.onNodeWithText(string(R.string.security_identity, secure, REGULAR_DOMAIN))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(BROWSER_NAVIGATION_SHELL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SITESKIN_SECURITY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(EXPRESSIVE_HEADER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SITESKIN_DOCK_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SITESKIN_ACTION_BOUQUET_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SITESKIN_QUICK_ACTIONS_TAG).assertDoesNotExist()
        captureDeviceScreenshot("06-google-regular.png", requirePageContent = true)
    }

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

    private fun isHome(): Boolean =
        composeRule.onAllNodes(hasText(string(R.string.home_suggested_title)))
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun awaitBrandAssetDecision(): BrandAssetTrace? {
        val recorder = inspectorRecorder()
        val deadline = SystemClock.uptimeMillis() + BRAND_ASSET_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            recorder?.latestBrandAsset(LIVE_ORIGIN)?.let { return it }
            Thread.sleep(BRAND_ASSET_POLL_MILLIS)
        }
        return null
    }

    private fun describe(trace: BrandAssetTrace?): String = if (trace == null) {
        "NEVER DECIDED after ${BRAND_ASSET_TIMEOUT_MILLIS}ms origin=$LIVE_ORIGIN"
    } else {
        buildString {
            appendLine("origin=$LIVE_ORIGIN")
            appendLine("stage=${trace.stage}")
            appendLine("rejection=${trace.rejection}")
            appendLine("httpStatus=${trace.httpStatus}")
            appendLine("redirects=${trace.redirects}")
            appendLine("pixels=${trace.width}x${trace.height}")
            appendLine("elapsedMillis=${trace.elapsedMillis}")
            appendLine("attempts=${trace.attempts}")
        }
    }

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

    private fun captureDeviceScreenshot(name: String, requirePageContent: Boolean = false) {
        composeRule.waitForIdle()
        val label = name.removeSuffix(".png")
        guard.requireAppOwnsScreen(label)
        val region = pageRegionIfRequired(requirePageContent)
        val bitmap = guard.captureWhenRendered(label, region, chromeInsidePageRegion(region))
        val png = ByteArrayOutputStream().use { buffer ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, buffer)
            assertTrue("PNG compression failed for $name", compressed)
            buffer.toByteArray()
        }
        assertTrue("Screenshot is empty: $name", png.isNotEmpty())

        testStorage.openOutputFile("$SCREENSHOT_DIRECTORY/$name").use { output ->
            output.write(png)
        }
    }

    private fun pageRegionIfRequired(required: Boolean): Rect? {
        if (!required) return null
        val bounds = composeRule.onNodeWithTag(BROWSER_CONTENT_TAG).fetchSemanticsNode().boundsInWindow
        return Rect(
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.right.roundToInt(),
            bounds.bottom.roundToInt(),
        )
    }

    /** Browser-owned overlays inside the WebView rectangle must never satisfy the rendered-page gate. */
    private fun chromeInsidePageRegion(region: Rect?): List<Rect> {
        if (region == null) return emptyList()
        return listOf(SITESKIN_QUICK_ACTIONS_TAG, SITESKIN_ACTION_BOUQUET_TAG).flatMap { tag ->
            composeRule.onAllNodes(hasTestTag(tag))
                .fetchSemanticsNodes()
                .map { node ->
                    val bounds = node.boundsInWindow
                    Rect(
                        bounds.left.roundToInt(),
                        bounds.top.roundToInt(),
                        bounds.right.roundToInt(),
                        bounds.bottom.roundToInt(),
                    )
                }
        }
    }

    private fun string(id: Int, vararg arguments: Any): String =
        targetContext.getString(id, *arguments)

    private val guard by lazy {
        ScreenEvidenceGuard(instrumentation.uiAutomation, targetContext.packageName, testStorage)
    }

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val uiDevice by lazy { UiDevice.getInstance(instrumentation) }

    private val targetContext
        get() = instrumentation.targetContext

    private val testStorage
        get() = PlatformTestStorageRegistry.getInstance()

    private companion object {
        const val LIVE_ORIGIN = "https://denrzv.github.io"
        const val PROFILE_PAGE_HEADING = "Account"
        const val REGULAR_ADDRESS = "https://www.google.com/ncr"
        const val REGULAR_DOMAIN = "google.com"
        const val MAX_HISTORY_RETURNS = 5
        const val NAVIGATION_SETTLE_MILLIS = 350L
        const val SCREENSHOT_DIRECTORY = "screenshots"
        const val LIVE_SITE_TIMEOUT_MILLIS = 45_000L
        const val BRAND_ASSET_TIMEOUT_MILLIS = 30_000L
        const val BRAND_ASSET_POLL_MILLIS = 100L
        const val PNG_QUALITY = 100
    }
}
