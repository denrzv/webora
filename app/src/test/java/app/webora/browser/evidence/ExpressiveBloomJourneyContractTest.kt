package app.webora.browser.evidence

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveBloomJourneyContractTest {
    @Test
    fun `live journey pins the expressive product history and hub story`() {
        assertExpressiveJourney(String(Files.readAllBytes(journeySource())))
    }

    @Test
    fun `retired persistent site navigation cannot authorize the M9 journey`() {
        val unsafe = """
            waitUntilNodeExists(hasTestTag(SITESKIN_BOTTOM_NAV_TAG))
            captureRegularBrowsingEvidence()
        """.trimIndent()

        assertFalse(isExpressiveJourney(unsafe))
    }

    @Test
    fun `canonical frames are complete guarded and chronologically ordered`() {
        val source = String(Files.readAllBytes(journeySource()))

        assertEquals(CANONICAL_FRAMES, capturedFrames(source))
        CANONICAL_FRAMES.drop(2).forEach { frame ->
            assertTrue(
                "$frame must require rendered page content",
                source.contains("captureDeviceScreenshot(\"$frame\", requirePageContent = true)"),
            )
        }
    }

    @Test
    fun `a partial or reordered inventory cannot satisfy the evidence contract`() {
        val unsafe = CANONICAL_FRAMES.dropLast(1).reversed().joinToString("\n") { frame ->
            "captureDeviceScreenshot(\"$frame\")"
        }

        assertFalse(hasCanonicalFrames(unsafe))
    }

    private fun assertExpressiveJourney(source: String) {
        assertTrue(isExpressiveJourney(source))
        assertTrue(hasCanonicalFrames(source))
        assertFalse(source.contains("waitUntilNodeExists(hasTestTag(SITESKIN_BOTTOM_NAV_TAG))"))
    }

    private fun isExpressiveJourney(source: String): Boolean = REQUIRED_MARKERS.all(source::contains)

    private fun hasCanonicalFrames(source: String): Boolean =
        capturedFrames(source) == CANONICAL_FRAMES

    private fun capturedFrames(source: String): List<String> = CAPTURE.findAll(source)
        .map { match -> match.groupValues[1] }
        .toList()

    private fun journeySource(): Path = Path.of(
        "src/androidTest/java/app/webora/browser/visual/LiveSiteScreenshotTest.kt",
    )

    private companion object {
        val REQUIRED_MARKERS = listOf(
            "EXPRESSIVE_HEADER_TAG",
            "SITESKIN_DOCK_TAG",
            "PRODUCT_NAME = \"Happy Days Bouquet\"",
            "SITESKIN_DOCK_BACK_TAG",
            "SITESKIN_DOCK_FORWARD_TAG",
            "SITESKIN_DOCK_HUB_TAG",
            "HUB_ITEMS",
            "EXPRESSIVE_HEADER_TAG).assertDoesNotExist()",
            "SITESKIN_DOCK_TAG).assertDoesNotExist()",
            "siteskin_site_menu_heading",
            "siteskin_browser_menu_heading",
            "waitForProductLink(isPresent = false)",
            "waitForProductLink(isPresent = true)",
        )
        val CANONICAL_FRAMES = listOf(
            "01-home.png",
            "02-siteskin-consent.png",
            "03-bloom-storefront.png",
            "04-happy-days-product.png",
            "05-bloom-storefront-back.png",
            "06-happy-days-forward.png",
            "07-siteskin-hub.png",
            "08-regular-browsing.png",
        )
        val CAPTURE = Regex("captureDeviceScreenshot\\(\\\"([^\\\"]+\\.png)\\\"")
    }
}
