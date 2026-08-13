package app.webora.browser.siteskin

import app.webora.browser.browser.BrowserMode
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `NET-003`'s "superseded work cannot publish" guard, now somewhere a test can reach it.
 *
 * It used to run only inside `BrowserScreen`'s `LaunchedEffect`, while `BrandAssetCoordinator` — the
 * one class with a test for it — was wired into nothing. `NET-004` deleted the unused class and
 * extracted the guard the browser actually runs.
 */
class BrandAssetPublicationTest {

    @Test fun `the configuration the load was started for publishes`() {
        val configuration = configuration()

        assertTrue(publishesBrandAsset(BrowserMode.Integrated(origin(), configuration), configuration))
    }

    /**
     * The case the guard exists for.
     *
     * Two acceptances of the *same bytes for the same origin* are still two navigations, and a load
     * started before the user left must not publish into the chrome after they came back.
     *
     * This does not distinguish `===` from `==`: `SiteSkinConfiguration` has no `equals`, so the two
     * are the same comparison today. The control that does work is deleting the guard — see the
     * tasklist. Written here so a future reader does not mistake this for a test of the operator.
     */
    @Test fun `a second acceptance of identical bytes does not publish`() {
        val superseded = configuration()
        val current = configuration()

        assertFalse(publishesBrandAsset(BrowserMode.Integrated(origin(), current), superseded))
    }

    @Test fun `a browser that has left integrated mode never publishes`() {
        val configuration = configuration()

        assertFalse(publishesBrandAsset(BrowserMode.Regular(origin()), configuration))
        assertFalse(publishesBrandAsset(BrowserMode.Home, configuration))
    }

    private fun origin() = requireNotNull(SiteOrigin.parse(ORIGIN))

    private fun configuration(): SiteSkinConfiguration =
        SiteSkinValidator.validate(MANIFEST.byteInputStream(), ORIGIN)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private companion object {
        const val ORIGIN = "https://brand.example"
        const val MANIFEST = """{"schemaVersion":"1.0","site":{"id":"brand","name":"Bloom"}}"""
    }
}
