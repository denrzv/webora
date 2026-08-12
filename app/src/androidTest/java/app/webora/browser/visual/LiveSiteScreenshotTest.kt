package app.webora.browser.visual

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import app.webora.browser.MainActivity
import app.webora.browser.R
import app.webora.browser.browser.BROWSER_CONTENT_TAG
import app.webora.browser.siteskin.SITESKIN_BOTTOM_NAV_TAG
import app.webora.browser.siteskin.SITESKIN_SECURITY_TAG
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
        waitUntilNodeExists(hasTestTag(SITESKIN_SECURITY_TAG))
        waitUntilNodeExists(hasTestTag(SITESKIN_BOTTOM_NAV_TAG))
        composeRule.onNodeWithTag(SITESKIN_SECURITY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SITESKIN_BOTTOM_NAV_TAG).assertIsDisplayed()
        // The only frame with a content requirement. Every assertion above is about the semantics
        // tree, and a region can have bounds with nothing painted into them — which is exactly the
        // frame run 9 published: SiteSkin chrome over an empty page, on a green job.
        captureDeviceScreenshot("03-siteskin-integrated.png", requirePageContent = true)
    }

    private fun waitUntilNodeExists(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.waitUntil(LIVE_SITE_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().size == 1
        }
    }

    /**
     * @param requirePageContent whether this frame's evidence includes a rendered page. Frame 01 is
     *   Home, which has no renderer and therefore no page rectangle; frame 02's evidence is the
     *   consent dialog and its canonical origin, not the dimmed page behind it.
     */
    private fun captureDeviceScreenshot(name: String, requirePageContent: Boolean = false) {
        composeRule.waitForIdle()
        val label = name.removeSuffix(".png")
        // Ownership first, content second, and the order is not arbitrary: CI-002 decides whether
        // this screen may be photographed at all, and only then is it worth asking whether anything
        // has been drawn on it.
        guard.requireAppOwnsScreen(label)
        val bitmap = guard.captureWhenRendered(label, pageRegionIfRequired(requirePageContent))
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

    private fun string(id: Int, vararg arguments: Any): String =
        targetContext.getString(id, *arguments)

    private val guard by lazy {
        ScreenEvidenceGuard(instrumentation.uiAutomation, targetContext.packageName, testStorage)
    }

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val targetContext
        get() = instrumentation.targetContext

    private val testStorage
        get() = PlatformTestStorageRegistry.getInstance()

    private companion object {
        const val LIVE_ORIGIN = "https://denrzv.github.io"
        const val SCREENSHOT_DIRECTORY = "screenshots"
        const val LIVE_SITE_TIMEOUT_MILLIS = 45_000L
        const val PNG_QUALITY = 100
    }
}
