package app.webora.browser.visual

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
import app.webora.browser.siteskin.SITESKIN_HUB_DRAWER_TAG
import app.webora.browser.siteskin.SITESKIN_HUB_SCRIM_TAG
import app.webora.browser.siteskin.SITESKIN_BACK_TAG
import app.webora.browser.siteskin.SITESKIN_NAV_HUB_TAG
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
        val openBloom = string(R.string.home_open_site, bloomName)
        composeRule.scrollHomeToText(openBloom)
        composeRule.onNodeWithText(openBloom).performClick()

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

        captureBloomHub()
        captureBloomProfile()
        captureGoogleRegularBrowsing()
    }

    /**
     * The native hub, which `UX-022` made a full-window drawer.
     *
     * **No page-content requirement, and that is not a weakening.** `RenderedContentPolicy` asks
     * whether `BROWSER_CONTENT_TAG`'s rectangle is non-uniform, and the drawer's own window covers
     * it — so the check would measure the drawer, find it gloriously non-uniform and pass for the
     * wrong reason. That is `CI-005`'s defect exactly: a region something else owns. The consent
     * frame has been in this category since `CI-001` for the same reason.
     *
     * What replaces it is stricter than what a pixel fraction could say about this frame: the
     * drawer's own tag, and every one of the reference integration's five trusted action ids
     * present as a row. A blank or half-composed hub fails those, where a fraction over a covered
     * rectangle could not.
     */
    private fun captureBloomHub() {
        composeRule.onNodeWithTag(SITESKIN_DOCK_HUB_TAG).assertIsDisplayed().performClick()
        waitUntilNodeExists(hasTestTag(SITESKIN_HUB_DRAWER_TAG))
        composeRule.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).assertIsDisplayed()
        // `UX-025`: the drawer shows what the dock did not take. Asserting both halves is what
        // makes de-duplication evidence rather than an absence nobody checked — a drawer that had
        // simply lost its rows would satisfy a check for the projected three being gone.
        BloomReferenceContract.DRAWER_ACTION_IDS.forEach { id ->
            composeRule.onNodeWithTag(BloomReferenceContract.actionTag(id)).assertIsDisplayed()
        }
        BloomReferenceContract.DOCK_ACTION_IDS.forEach { id ->
            composeRule.onNodeWithTag(BloomReferenceContract.actionTag(id)).assertDoesNotExist()
            composeRule.onNodeWithTag(BloomReferenceContract.dockTag(id)).assertIsDisplayed()
        }
        captureDeviceScreenshot("04-bloom-actions.png")
    }

    /**
     * `UX-025` moved this click from the drawer to the dock, and the dismissal with it.
     *
     * `profile` is nominated by `presentation.dock`, so it is a persistent dock slot and no longer a
     * drawer row — the previous version's `actionTag` would now find nothing. The drawer is still
     * open from frame 04 and must be dismissed first: a dock slot is behind the drawer's window
     * while it is up, so clicking it without closing would land on the modal.
     *
     * **`UX-026` gave that dismissal a mechanism and this step a deterministic target.** `UX-025`
     * wrote `performClick()`, which taps the node's *centre* — and the scrim node is the whole
     * window, so its centre is the screen's centre. With `UX-026`'s content-sized panel that is
     * scrim for a two-row menu and *panel* for a menu taller than half the viewport: a step that
     * works for Bloom today and silently stops exercising dismissal for a site with a real menu.
     * `bottomCenter` is scrim at every permitted content height, because the panel is top-aligned
     * and `HUB_DRAWER_MAX_FRACTION` keeps a strip below it.
     *
     * The step is unchanged in what it proves. Frame 04's own acceptance checks are untouched.
     */
    private fun captureBloomProfile() {
        composeRule.onNodeWithTag(SITESKIN_HUB_SCRIM_TAG).performTouchInput { click(bottomCenter) }
        waitUntilNodeAbsent(hasTestTag(SITESKIN_HUB_DRAWER_TAG))
        composeRule.onNodeWithTag(
            BloomReferenceContract.dockTag(BloomReferenceContract.PROFILE_ACTION_ID),
        )
            .assertIsDisplayed()
            .performClick()
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
        // `UX-021` made this a wait rather than a point assertion. The chip appears as soon as an
        // origin is committed, but `Secure` is now earned by a successful main-frame completion, so
        // the identity node can legitimately exist reading `Not verified` for a moment. Waiting for
        // the confirmed text is synchronising on a state the app reaches on its own — `NET-004`'s
        // rule — and it makes the frame stronger evidence: it can no longer be captured while the
        // browser has confirmed nothing.
        val secure = string(R.string.security_secure)
        val secureIdentity = string(R.string.security_identity, secure, REGULAR_DOMAIN)
        waitUntilNodeExists(hasText(secureIdentity))
        composeRule.onNodeWithText(secureIdentity).assertIsDisplayed()
        composeRule.onNodeWithTag(BROWSER_NAVIGATION_SHELL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SITESKIN_SECURITY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(EXPRESSIVE_HEADER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SITESKIN_DOCK_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SITESKIN_HUB_DRAWER_TAG).assertDoesNotExist()
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

    /**
     * `UX-024`: integrated Back lives behind the browser navigation hub, so the traversal opens it
     * first. `CI-008`'s prerequisite is asserted on the bubble, immediately before the click, and no
     * frame is captured while the bouquet is open — its `Popup` overhangs `BROWSER_CONTENT_TAG`, and
     * `CI-005` records what measuring a modal as page content costs.
     */
    /**
     * Opens the browser-owned navigation hub, asserting its prerequisite before invoking it.
     *
     * `CI-008` requires a transition control's displayed and enabled semantics immediately before the
     * click, because a click followed by a destination timeout cannot say whether the action was
     * unavailable or whether the transition failed. `UX-024` put a **second** control between the
     * user and integrated Back, so the rule now applies twice: without this, a hub that composed but
     * never opened would fail at the bubble's wait and name the bubble.
     *
     * This can only make the evidence refuse earlier, which is `CI-008`'s own constraint on itself.
     */
    private fun openNavigationHub() {
        waitUntilNodeExists(hasTestTag(SITESKIN_NAV_HUB_TAG))
        composeRule.onNodeWithTag(SITESKIN_NAV_HUB_TAG).assertIsDisplayed().assertIsEnabled().performClick()
        waitUntilNodeExists(hasTestTag(SITESKIN_BACK_TAG))
    }

    private fun returnFromBloomHistoryToHome() {
        composeRule.returnIntegratedHistoryToHome(
            homeText = string(R.string.home_suggested_title),
            maxReturns = MAX_HISTORY_RETURNS,
            timeoutMillis = LIVE_SITE_TIMEOUT_MILLIS,
            settleMillis = NAVIGATION_SETTLE_MILLIS,
        )
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
        return listOf(SITESKIN_QUICK_ACTIONS_TAG, SITESKIN_HUB_DRAWER_TAG).flatMap { tag ->
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
