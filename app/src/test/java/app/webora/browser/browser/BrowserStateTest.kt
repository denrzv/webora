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

    @Test fun `global disable drops integrated chrome without losing page state`() {
        val origin = checkNotNull(SiteOrigin.parse("https://shop.example"))
        val integrated = BrowserState(
            mode = BrowserMode.Regular(origin),
            displayedUrl = "https://shop.example/catalog",
            addressText = "https://shop.example/catalog",
        ).activateSiteSkin(origin, configuration("https://shop.example"))

        val result = integrated.deactivateSiteSkin()

        assertEquals(BrowserMode.Regular(origin), result.mode)
        assertEquals(integrated.displayedUrl, result.displayedUrl)
        assertEquals(integrated.addressText, result.addressText)
    }

    // ---- UX-021: browser-owned transport state ----------------------------------------------
    //
    // Every case below is a transition, never a projection. The rule under test is that `SECURE` is
    // the one value requiring positive evidence, and that the evidence is a *successful completion*
    // observation rather than the scheme of whatever URL is currently in hand.

    @Test fun `a new tab claims nothing`() {
        assertEquals(TransportSecurity.UNKNOWN, BrowserState().transport)
    }

    @Test fun `an https page in progress is not yet secure`() {
        // Issue requirement 3, and the case the old scheme-only classification got wrong: the URL
        // says https from the first callback, and nothing has confirmed it.
        val started = BrowserState().observe(started("https://shop.example/catalog"))

        assertEquals(TransportSecurity.UNKNOWN, started.transport)

        val completed = started.observe(completed("https://shop.example/catalog"))

        assertEquals(TransportSecurity.SECURE, completed.transport)
    }

    @Test fun `leaving a secure page clears the claim before the next one is known`() {
        // The reset that makes every gap in the observation surface fail closed, and it must be
        // asserted from a state that is *already* SECURE — starting from a fresh `BrowserState`
        // cannot tell "reset to UNKNOWN" apart from "preserved UNKNOWN", which is how this case went
        // missing until the negative control found no test to fail.
        //
        // This is the window a user is in while a navigation is in flight. `mainFrameTlsFailure`
        // publishes nothing when it cannot identify the main frame, so if the previous page's green
        // survived a page start, "no news" would mean the previous page's good news on a page the
        // browser knows nothing about yet.
        val secure = BrowserState()
            .observe(started("https://shop.example"))
            .observe(completed("https://shop.example"))
        assertEquals(TransportSecurity.SECURE, secure.transport)

        val leaving = secure.observe(started("https://elsewhere.example"))

        assertEquals(TransportSecurity.UNKNOWN, leaving.transport)
    }

    @Test fun `reloading a secure page clears the claim until it is confirmed again`() {
        // The same-URL case, which is the one a preserve-on-start implementation passes by accident:
        // the origin has not changed, so an origin comparison would keep the green. A page start is
        // a new navigation whatever its URL, and the certificate it will present is not the one
        // already accepted.
        val secure = BrowserState()
            .observe(started("https://shop.example"))
            .observe(completed("https://shop.example"))

        val reloading = secure.observe(started("https://shop.example"))

        assertEquals(TransportSecurity.UNKNOWN, reloading.transport)
    }

    @Test fun `a completed http page is known to be insecure`() {
        val state = BrowserState()
            .observe(started("http://shop.example/catalog"))
            .observe(completed("http://shop.example/catalog"))

        assertEquals(TransportSecurity.NOT_SECURE, state.transport)
    }

    @Test fun `no green survives a certificate failure`() {
        // Issue requirement 4. The secure state is reached honestly first, so this asserts that a
        // failure *clears* it rather than that it was never set.
        val secure = BrowserState()
            .observe(started("https://shop.example"))
            .observe(completed("https://shop.example"))
        assertEquals(TransportSecurity.SECURE, secure.transport)

        val failed = secure.observe(
            BrowserObservation.PageFailed("https://shop.example", LoadErrorKind.TLS),
        )

        assertEquals(TransportSecurity.TLS_ERROR, failed.transport)
    }

    @Test fun `a non-TLS failure is unknown rather than a certificate claim`() {
        val secure = BrowserState()
            .observe(started("https://shop.example"))
            .observe(completed("https://shop.example"))

        listOf(LoadErrorKind.NETWORK, LoadErrorKind.CONNECTION, LoadErrorKind.UNKNOWN).forEach { kind ->
            val failed = secure.observe(BrowserObservation.PageFailed("https://shop.example", kind))

            assertEquals(
                "$kind leaves no confirmed transport, and did not observe a certificate problem",
                TransportSecurity.UNKNOWN,
                failed.transport,
            )
        }
    }

    @Test fun `a completion arriving after a recorded failure cannot go green`() {
        // Defence in depth behind `HardenedWebViewClient`'s URL-comparing suppression, which a
        // redirect failing and completing at different URLs can slip past.
        val failed = BrowserState()
            .observe(started("https://shop.example"))
            .observe(BrowserObservation.PageFailed("https://shop.example", LoadErrorKind.TLS))

        val completed = failed.observe(completed("https://shop.example"))

        assertEquals(TransportSecurity.UNKNOWN, completed.transport)
    }

    @Test fun `a scheme redirect resolves to the final committed transport in both directions`() {
        // Issue requirement 5. The originally requested URL never participates: only the URL the
        // main frame actually completed on does.
        val downgraded = BrowserState()
            .observe(started("https://shop.example"))
            .observe(completed("https://shop.example"))
            .observe(started("https://shop.example/go"))
            .observe(completed("http://shop.example/landing"))

        assertEquals(TransportSecurity.NOT_SECURE, downgraded.transport)

        val upgraded = downgraded
            .observe(started("http://shop.example/go"))
            .observe(completed("https://shop.example/landing"))

        assertEquals(TransportSecurity.SECURE, upgraded.transport)
    }

    @Test fun `a same-document change keeps the confirmed transport and a cross-origin one drops it`() {
        val secure = BrowserState()
            .observe(started("https://shop.example/catalog"))
            .observe(completed("https://shop.example/catalog"))

        val sameOrigin = secure.observe(
            BrowserObservation.Page("https://shop.example/catalog#reviews", false, true, false),
        )
        assertEquals(TransportSecurity.SECURE, sameOrigin.transport)

        // `SiteOrigin` equality is `ADR-004`'s full canonical tuple, so a different host — or the
        // same host on another scheme or port — is a different origin and carries no green over.
        val crossOrigin = secure.observe(
            BrowserObservation.Page("https://other.example/catalog", false, true, false),
        )
        assertEquals(TransportSecurity.UNKNOWN, crossOrigin.transport)
    }

    @Test fun `a requested destination is not a confirmed one`() {
        val state = BrowserState()
            .observe(started("https://shop.example"))
            .observe(completed("https://shop.example"))
            .navigateFromHome("https://bank.example")

        assertEquals(TransportSecurity.UNKNOWN, state.transport)
    }

    @Test fun `changing chrome does not change transport`() {
        // The same page over the same connection. Activation and deactivation are chrome decisions,
        // and neither observes anything about transport.
        val origin = checkNotNull(SiteOrigin.parse("https://shop.example"))
        val secure = BrowserState()
            .observe(started("https://shop.example"))
            .observe(completed("https://shop.example"))

        val activated = secure.activateSiteSkin(origin, configuration("https://shop.example"))
        assertEquals(TransportSecurity.SECURE, activated.transport)
        assertEquals(TransportSecurity.SECURE, activated.deactivateSiteSkin().transport)
    }

    private fun started(url: String) = BrowserObservation.PageStarted(url, false, false)

    private fun completed(url: String) = BrowserObservation.MainFrameCompleted(url, false, false)

    private fun configuration(origin: String) = SiteSkinValidator.validate(
        """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}""".byteInputStream(),
        origin,
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }
}
