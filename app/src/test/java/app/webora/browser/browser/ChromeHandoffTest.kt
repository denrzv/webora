package app.webora.browser.browser

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChromeHandoffTest {
    @Test fun `Home regular integrated regular Home exposes exactly one intended layer set`() {
        val origin = origin("https://shop.example")
        val configuration = configuration("shop")
        val home = BrowserState()
        val regular = home.navigateFromHome("https://shop.example/catalog")
        val integrated = regular.activateSiteSkin(origin, configuration)
        val exited = integrated.observe(
            BrowserObservation.PageStarted("https://news.example", canGoBack = true, canGoForward = false),
        )
        val returnedHome = BrowserState()

        assertEquals(ChromeHandoff.HOME, home.mode.chromeHandoff())
        assertEquals(ChromeHandoff.REGULAR, regular.mode.chromeHandoff())
        assertEquals(ChromeHandoff.INTEGRATED, integrated.mode.chromeHandoff())
        assertEquals(ChromeHandoff.REGULAR, exited.mode.chromeHandoff())
        assertEquals(ChromeHandoff.HOME, returnedHome.mode.chromeHandoff())
    }

    @Test fun `declined or denied candidate cannot remove regular browser chrome`() {
        val regular = BrowserState().navigateFromHome("https://shop.example")

        assertEquals(ChromeHandoff.REGULAR, regular.mode.chromeHandoff())
        assertEquals(BottomChrome.BROWSER, regular.mode.chromeHandoff().bottom)
    }

    @Test fun `selected tab alone authorises chrome and stale integrated tab is a negative control`() {
        val integrated = BrowserState(
            mode = BrowserMode.Regular(origin("https://shop.example")),
            displayedUrl = "https://shop.example",
        ).activateSiteSkin(origin("https://shop.example"), configuration("shop"))
        val session = BrowserSession.fresh().updateActive { integrated }.createTab()
            .updateActive { it.navigateFromHome("https://news.example") }

        assertEquals(ChromeHandoff.REGULAR, session.chromeHandoff())
        assertNotEquals(
            "negative control: projecting the previous tab would leak its SiteSkin chrome",
            session.tabs.first().state.mode.chromeHandoff(),
            session.chromeHandoff(),
        )
        assertEquals(ChromeHandoff.INTEGRATED, session.select(session.tabs.first().id).chromeHandoff())
    }

    @Test fun `integrated top is an indivisible browser protected identity and escape layer`() {
        val protected = ChromeHandoff.INTEGRATED.top

        assertEquals(TopChrome.PROTECTED_INTEGRATED, protected)
        assertEquals(true, protected.showsSecurityIdentity)
        assertEquals(true, protected.showsBackEscape)
        assertEquals(false, TopChrome.REGULAR.showsBackEscape)
    }

    private fun origin(url: String) = checkNotNull(SiteOrigin.parse(url))

    private fun configuration(id: String) = SiteSkinValidator.validate(
        """{"schemaVersion":"1.0","site":{"id":"$id","name":"Shop"}}""".byteInputStream(),
        "https://shop.example",
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
}
