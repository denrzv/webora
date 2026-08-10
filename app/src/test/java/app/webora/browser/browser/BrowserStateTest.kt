package app.webora.browser.browser

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserStateTest {
    @Test
    fun `home navigation enters regular mode with resolved destination`() {
        val state = BrowserState().navigateFromHome("https://example.com/path")

        assertEquals("https://example.com/path", state.displayedUrl)
        assertEquals("https://example.com/path", state.addressText)
        assertTrue(state.mode is BrowserMode.Regular)
    }
    @Test
    fun `page observation creates regular mode and renderer state`() {
        val state = BrowserState().observe(
            BrowserObservation.Page(
                url = "https://Example.com/catalog",
                isLoading = true,
                canGoBack = true,
                canGoForward = false,
            ),
        )

        assertEquals("https://Example.com/catalog", state.displayedUrl)
        assertEquals(state.displayedUrl, state.addressText)
        assertTrue(state.isLoading)
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)
        assertEquals("example.com", (state.mode as BrowserMode.Regular).origin?.host)
    }

    @Test
    fun `malformed callback stays regular without trusted origin`() {
        val state = BrowserState().observe(
            BrowserObservation.Page("not a URL", false, false, false),
        )

        assertNull((state.mode as BrowserMode.Regular).origin)
    }

    @Test
    fun `address edit does not change observed browser mode`() {
        val initial = BrowserState(mode = BrowserMode.Regular(null))
        val state = initial.observe(BrowserObservation.AddressEdited("untrusted text"))

        assertEquals(initial.mode, state.mode)
        assertEquals("untrusted text", state.addressText)
    }

    @Test fun `integrated mode survives exact same origin and drops on subdomain`() {
        val origin = checkNotNull(SiteOrigin.parse("https://shop.example"))
        val integrated = BrowserState(mode = BrowserMode.Regular(origin))
            .activateSiteSkin(origin, configuration("https://shop.example"))

        val retained = integrated.observe(BrowserObservation.Page("https://shop.example/next", false, false, false))
        val dropped = retained.observe(BrowserObservation.PageStarted("https://admin.shop.example", false, false))

        assertTrue(retained.mode is BrowserMode.Integrated)
        assertTrue(dropped.mode is BrowserMode.Regular)
    }

    @Test fun `activation cannot apply configuration to a different observed origin`() {
        val observed = checkNotNull(SiteOrigin.parse("https://news.example"))
        val state = BrowserState(mode = BrowserMode.Regular(observed))

        val result = state.activateSiteSkin(
            checkNotNull(SiteOrigin.parse("https://shop.example")),
            configuration("https://shop.example"),
        )

        assertEquals(state, result)
    }

    private fun configuration(origin: String) = SiteSkinValidator.validate(
        """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}""".byteInputStream(),
        origin,
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
}
