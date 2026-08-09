package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecurityPresentationTest {
    @Test
    fun `committed https origin exposes browser owned tls identity`() {
        val origin = requireNotNull(SiteOrigin.parse("https://shop.example.co.uk/cart"))

        assertEquals(
            SecurityPresentation("example.co.uk", TransportSecurity.SECURE),
            securityPresentation(BrowserMode.Regular(origin)),
        )
    }

    @Test
    fun `http and absent origins cannot claim a secure identity`() {
        val http = requireNotNull(SiteOrigin.parse("http://example.com"))

        assertEquals(
            SecurityPresentation("example.com", TransportSecurity.NOT_SECURE),
            securityPresentation(BrowserMode.Regular(http)),
        )
        assertNull(securityPresentation(BrowserMode.Regular(null)))
        assertNull(securityPresentation(BrowserMode.Home))
    }

    @Test
    fun `edited address cannot replace committed identity`() {
        val origin = requireNotNull(SiteOrigin.parse("https://example.com"))
        val state = BrowserState(mode = BrowserMode.Regular(origin))
            .observe(BrowserObservation.AddressEdited("https://attacker.example"))

        assertEquals("example.com", securityPresentation(state.mode)?.registrableDomain)
    }
}
