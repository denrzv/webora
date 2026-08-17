package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecurityPresentationTest {
    @Test
    fun `committed origin supplies the domain and the observed transport supplies the state`() {
        val origin = requireNotNull(SiteOrigin.parse("https://shop.example.co.uk/cart"))

        assertEquals(
            SecurityPresentation("example.co.uk", TransportSecurity.SECURE),
            securityPresentation(BrowserMode.Regular(origin), TransportSecurity.SECURE),
        )
    }

    @Test
    fun `every transport state is carried through unchanged`() {
        // The projection classifies nothing. If it ever grew a branch that re-derived state from the
        // scheme, this is the test that would catch it: the origin is https in all four rows.
        val origin = requireNotNull(SiteOrigin.parse("https://example.com"))

        TransportSecurity.entries.forEach { transport ->
            assertEquals(
                "https origin must not override an observed $transport",
                transport,
                securityPresentation(BrowserMode.Regular(origin), transport)?.transportSecurity,
            )
        }
    }

    @Test
    fun `no committed origin yields no node rather than a blank one`() {
        // `A11Y-001`'s contract, and `BrowserScreen`'s `checkNotNull` on the integrated path relies
        // on it. Adding the transport parameter must not have changed the nullability.
        assertNull(securityPresentation(BrowserMode.Regular(null), TransportSecurity.SECURE))
        assertNull(securityPresentation(BrowserMode.Home, TransportSecurity.SECURE))
    }

    @Test
    fun `edited address cannot replace committed identity`() {
        val origin = requireNotNull(SiteOrigin.parse("https://example.com"))
        val state = BrowserState(mode = BrowserMode.Regular(origin))
            .observe(BrowserObservation.AddressEdited("https://attacker.example"))

        assertEquals(
            "example.com",
            securityPresentation(state.mode, state.transport)?.registrableDomain,
        )
    }

    @Test
    fun `a completed https main frame is the only thing that earns secure`() {
        val https = requireNotNull(SiteOrigin.parse("https://example.com"))
        val http = requireNotNull(SiteOrigin.parse("http://example.com"))

        assertEquals(TransportSecurity.SECURE, completedTransport(https, failure = null))
        assertEquals(TransportSecurity.NOT_SECURE, completedTransport(http, failure = null))
    }

    @Test
    fun `completion without evidence is unknown rather than a guess`() {
        val https = requireNotNull(SiteOrigin.parse("https://example.com"))
        val tlsFailure = BrowserLoadFailure(LoadErrorKind.TLS, "example.com", null)
        val networkFailure = BrowserLoadFailure(LoadErrorKind.NETWORK, "example.com", null)

        // A completion arriving with a failure already recorded. `HardenedWebViewClient` suppresses
        // this by comparing URLs, which a redirect that fails and completes at different URLs can
        // slip past — so the state layer refuses it too.
        assertEquals(TransportSecurity.UNKNOWN, completedTransport(https, tlsFailure))
        assertEquals(TransportSecurity.UNKNOWN, completedTransport(https, networkFailure))

        // A URL that did not parse into an origin at all.
        assertEquals(TransportSecurity.UNKNOWN, completedTransport(null, failure = null))
    }

    @Test
    fun `a scheme outside the allow-list cannot reach the classifier at all`() {
        // Worth pinning rather than assuming, because it is why `completedTransport` has no third
        // arm: `SiteOrigin`'s constructor is private, so a non-HTTP(S) origin is unconstructible and
        // arrives as the `null` row instead. A test asserting an `ftp` row would pass through that
        // null and prove nothing about the scheme check.
        assertNull(SiteOrigin.parse("ftp://example.com"))
        assertNull(SiteOrigin.parse("file:///etc/passwd"))
        assertEquals(TransportSecurity.UNKNOWN, completedTransport(null, failure = null))
    }

    @Test
    fun `unknown is the default so an unobserved transport claims nothing`() {
        assertEquals(TransportSecurity.UNKNOWN, BrowserState().transport)
        assertEquals(TransportSecurity.UNKNOWN, TransportSecurity.entries.first())
    }
}
