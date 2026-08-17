package app.webora.browser.visual

import android.graphics.Bitmap
import android.graphics.Rect
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
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import app.webora.browser.MainActivity
import app.webora.browser.R
import app.webora.browser.browser.BROWSER_CONTENT_TAG
import app.webora.browser.browser.BROWSER_NAVIGATION_SHELL_TAG
import app.webora.browser.browser.BROWSER_SECURITY_TAG
import app.webora.browser.inspector.BrandAssetStage
import app.webora.browser.inspector.BrandAssetTrace
import app.webora.browser.inspector.inspectorRecorder
import app.webora.browser.siteskin.SITESKIN_BACK_TAG
import app.webora.browser.siteskin.EXPRESSIVE_HEADER_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_BACK_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_FORWARD_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_HUB_TAG
import app.webora.browser.siteskin.SITESKIN_DOCK_TAG
import app.webora.browser.siteskin.SITESKIN_ACTION_BOUQUET_TAG
import app.webora.browser.siteskin.SITESKIN_ACTION_TAG_PREFIX
import app.webora.browser.siteskin.SITESKIN_QUICK_ACTIONS_TAG
import app.webora.browser.siteskin.SITESKIN_SECURITY_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class LiveSiteScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesLiveBloomFlowersSiteSkinJourney() {
        composeRule.onNodeWithText(string(R.string.onboarding_skip)).performClick()
        composeRule.onNodeWithText(string(R.string.home_suggested_title)).assertIsDisplayed()
        captureDeviceScreenshot("01-home.png")

        val bloomName = string(R.string.suggested_bloom_name)
        val openBloom = string(R.string.home_open_site, bloomName)
        composeRule.onNodeWithText(openBloom).performScrollTo().performClick()

        val consentTitle = string(R.string.siteskin_consent_title, LIVE_ORIGIN)
        waitUntilNodeExists(hasText(consentTitle))
        composeRule.onNodeWithText(consentTitle).assertIsDisplayed()
        captureDeviceScreenshot("02-siteskin-consent.png")

        composeRule.onNodeWithText(string(R.string.siteskin_allow)).performClick()
        requireIntegratedChrome()
        val brandAsset = awaitBrandAssetDecision()
        guard.recordDiagnostic("brand-asset-03-bloom-storefront.txt", describe(brandAsset))
        captureDeviceScreenshot("03-bloom-storefront.png", requirePageContent = true)
        // Asserted after the capture on purpose: a run that fails here must still publish the frame
        // that shows what it looked like, and asserting first would destroy the evidence.
        assertEquals(
                "the reference integration's logo did not reach the 40 dp slot; see " +
                "diagnostics/brand-asset-03-bloom-storefront.txt",
            BrandAssetStage.DECODED,
            brandAsset?.stage,
        )
        exerciseExpressiveBloomJourney()
        captureRegularBrowsingEvidence()
    }

    private fun exerciseExpressiveBloomJourney() {
        val product = findStorefrontProductLink()
        requireNotNull(product) {
            "Bloom did not expose the clickable $PRODUCT_LINK_TEXT link for $PRODUCT_NAME"
        }
        product.click()

        waitForProductLink(isPresent = false)
        requireIntegratedChrome()
        waitUntilNodeExists(hasTestTag(SITESKIN_DOCK_BACK_TAG))
        captureDeviceScreenshot("04-happy-days-product.png", requirePageContent = true)
        composeRule.onNodeWithTag(SITESKIN_DOCK_BACK_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitForProductLink(isPresent = true)
        requireIntegratedChrome()
        waitUntilNodeExists(hasTestTag(SITESKIN_DOCK_FORWARD_TAG))
        captureDeviceScreenshot("05-bloom-storefront-back.png", requirePageContent = true)
        composeRule.onNodeWithTag(SITESKIN_DOCK_FORWARD_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        waitForProductLink(isPresent = false)
        requireIntegratedChrome()
        captureDeviceScreenshot("06-happy-days-forward.png", requirePageContent = true)

        composeRule.onNodeWithTag(SITESKIN_DOCK_HUB_TAG).assertIsDisplayed().performClick()
        waitUntilNodeExists(hasTestTag(SITESKIN_ACTION_BOUQUET_TAG))
        HUB_ITEM_IDS.forEach { id ->
            composeRule.onNodeWithTag("$SITESKIN_ACTION_TAG_PREFIX$id").assertIsDisplayed()
        }
        captureDeviceScreenshot("07-siteskin-hub.png", requirePageContent = true)
        composeRule.onNodeWithTag("${SITESKIN_ACTION_TAG_PREFIX}home").performClick()
        returnFromBloomHistoryToHome()
    }

    /**
     * Finds the real storefront title link as a user would: the deployed page puts Popular Picks
     * below the first viewport, so the hosted journey must scroll rather than assume the product is
     * already exposed to accessibility. The full product name remains page copy on the detail route;
     * the storefront's clickable text contract is the shorter "Happy Days" label.
     */
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
            if (composeRule.onAllNodes(hasText(string(R.string.home_suggested_title)))
                    .fetchSemanticsNodes().isNotEmpty()
            ) return
            waitUntilNodeExists(hasTestTag(SITESKIN_BACK_TAG))
            composeRule.onNodeWithTag(SITESKIN_BACK_TAG).performClick()
        }
        waitUntilNodeExists(hasText(string(R.string.home_suggested_title)))
    }

    /**
     * Leaves the already-proven integrated origin through browser-owned UI, then uses Webora's
     * address input. Page prose is deliberately not an assertion: the remote document is untrusted
     * content, while these tags are the browser's closed chrome-handoff contract from UX-012.
     */
    private fun captureRegularBrowsingEvidence() {
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
        captureDeviceScreenshot("08-regular-browsing.png", requirePageContent = true)
    }

    /**
     * Waits for the brand-asset pipeline to *decide*, and records what it decided.
     *
     * Every other wait in this journey is about the semantics tree or about pixels; the logo is
     * neither. `NET-003` publishes a monogram on every failure, so a frame taken before the asset
     * lands and a frame taken after a refusal are the same picture — which is how runs 11 and 15 both
     * shipped a monogram with nobody able to say why. The absence of a trace is "still loading"; its
     * presence is the pipeline's own answer, read from the recorder the browser wrote it to.
     *
     * This waits for a state the app reaches on its own. It suppresses nothing, seeds nothing and
     * changes nothing about what is drawn — `DEVX-003` refused a screenshot mode on exactly those
     * grounds, and `CI-005`'s rule that a harness change may only ever make the run refuse *more*
     * holds here: a frame that passes today with a monogram now fails, and no frame that fails today
     * can start passing.
     */
    private fun awaitBrandAssetDecision(): BrandAssetTrace? {
        val recorder = inspectorRecorder()
        val deadline = SystemClock.uptimeMillis() + BRAND_ASSET_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            recorder?.latestBrandAsset(LIVE_ORIGIN)?.let { return it }
            Thread.sleep(BRAND_ASSET_POLL_MILLIS)
        }
        return null
    }

    /**
     * The record, written on success as well as failure.
     *
     * `CI-003` established the rule: a check that records nothing when it passes cannot be told apart
     * from one that barely passed for the wrong reason. The elapsed time is the field that settles
     * whether a monogram was a refusal or a race.
     */
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

    /**
     * @param requirePageContent whether this frame's evidence includes a rendered page. Home has no
     *   renderer; consent evidences the dialog and canonical origin rather than the dimmed page.
     */
    private fun captureDeviceScreenshot(name: String, requirePageContent: Boolean = false) {
        composeRule.waitForIdle()
        val label = name.removeSuffix(".png")
        // Ownership first, content second, and the order is not arbitrary: CI-002 decides whether
        // this screen may be photographed at all, and only then is it worth asking whether anything
        // has been drawn on it.
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

    /** The page rectangle from the semantics tree, so the check measures the region — not the chrome. */
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

    /**
     * Browser-owned overlays that are children of the page rectangle, so their pixels must not count
     * as page content.
     *
     * `SiteSkinQuickActions` is composed *inside* the very `Box` that bounds the renderer
     * (`BrowserScreen.kt`), and in run 10 that one floating button was enough to clear the rendered
     * threshold over a completely blank page. Excluding it is the difference between measuring the
     * page and measuring Webora's own chrome.
     */
    private fun chromeInsidePageRegion(region: Rect?): List<Rect> {
        if (region == null) return emptyList()
        return composeRule.onAllNodes(hasTestTag(SITESKIN_QUICK_ACTIONS_TAG))
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
        const val REGULAR_ADDRESS = "example.com"
        const val REGULAR_DOMAIN = "example.com"
        const val PRODUCT_NAME = "Happy Days Bouquet"
        const val PRODUCT_LINK_TEXT = "Happy Days"
        val HUB_ITEM_IDS = listOf("home", "catalog", "cart", "profile", "call")
        const val MAX_HISTORY_RETURNS = 4
        const val MAX_PRODUCT_SCROLL_ATTEMPTS = 5
        const val PRODUCT_SCROLL_STEPS = 24
        const val PRODUCT_SCROLL_SETTLE_MILLIS = 350L
        const val PRODUCT_LINK_FINAL_WAIT_MILLIS = 5_000L
        const val SCREENSHOT_DIRECTORY = "screenshots"
        const val LIVE_SITE_TIMEOUT_MILLIS = 45_000L
        const val BRAND_ASSET_TIMEOUT_MILLIS = 30_000L
        const val BRAND_ASSET_POLL_MILLIS = 100L
        const val PNG_QUALITY = 100
    }
}
