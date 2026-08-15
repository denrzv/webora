package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserBackTest {
    @Test fun `history wins over native Home fallback`() {
        var historyCalls = 0
        var homeCalls = 0

        val consumed = navigateBrowserBack(
            mode = regularMode(),
            navigateHistory = { historyCalls += 1; true },
            navigateHome = { homeCalls += 1 },
        )

        assertTrue(consumed)
        assertTrue(historyCalls == 1)
        assertTrue(homeCalls == 0)
    }

    @Test fun `first page falls back to native Home`() {
        var homeReached = false

        val consumed = navigateBrowserBack(
            mode = regularMode(),
            navigateHistory = { false },
            navigateHome = { homeReached = true },
        )

        assertTrue(consumed)
        assertTrue(homeReached)
        assertTrue(regularMode().canNavigateBack())
    }

    @Test fun `Home delegates without consulting renderer history`() {
        var historyConsulted = false
        var homeCalled = false

        val consumed = navigateBrowserBack(
            mode = BrowserMode.Home,
            navigateHistory = { historyConsulted = true; true },
            navigateHome = { homeCalled = true },
        )

        assertFalse(consumed)
        assertFalse(historyConsulted)
        assertFalse(homeCalled)
        assertFalse(BrowserMode.Home.canNavigateBack())
    }

    private fun regularMode() = BrowserMode.Regular(SiteOrigin.parse("https://example.com"))
}
