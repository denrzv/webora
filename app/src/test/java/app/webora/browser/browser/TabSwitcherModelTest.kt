package app.webora.browser.browser

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSwitcherModelTest {
    @Test fun `summaries use browser observed domains and preserve selection and order`() {
        val first = BrowserSession.fresh().updateActive { it.navigateFromHome("https://shop.example/path") }
        val session = first.createTab()

        val summaries = tabSummaries(session)

        assertEquals(listOf("shop.example", "Home"), summaries.map { it.label })
        assertEquals(listOf(1, 2), summaries.map { it.position })
        assertFalse(summaries.first().selected)
        assertTrue(summaries.last().selected)
    }

    @Test fun `manifest branding and editable address cannot label a tab`() {
        val origin = checkNotNull(SiteOrigin.parse("https://safe.example"))
        val state = BrowserState(
            mode = BrowserMode.Regular(origin),
            displayedUrl = "https://safe.example",
            addressText = "attacker.test",
        ).activateSiteSkin(origin, configuration())

        val summary = tabSummaries(BrowserSession.fresh().updateActive { state }).single()

        assertEquals("safe.example", summary.label)
        assertFalse(summary.label.contains("Remote Bank"))
        assertFalse(summary.label.contains("attacker"))
    }

    @Test fun `untrusted or absent origin has generic browser fallback`() {
        val state = BrowserState(
            mode = BrowserMode.Regular(null),
            displayedUrl = "not a url",
            addressText = "Remote title",
        )

        assertEquals("Page", tabSummaries(BrowserSession.fresh().updateActive { state }).single().label)
    }

    private fun configuration() = SiteSkinValidator.validate(
        """{"schemaVersion":"1.0","site":{"id":"bank","name":"Remote Bank"}}""".byteInputStream(),
        "https://safe.example",
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
}
