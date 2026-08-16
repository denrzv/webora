package app.webora.browser.evidence

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
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

    private fun assertExpressiveJourney(source: String) {
        assertTrue(isExpressiveJourney(source))
        assertFalse(source.contains("waitUntilNodeExists(hasTestTag(SITESKIN_BOTTOM_NAV_TAG))"))
    }

    private fun isExpressiveJourney(source: String): Boolean = REQUIRED_MARKERS.all(source::contains)

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
        )
    }
}
