package app.webora.browser.siteskin

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinNavigationContractTest {
    @Test
    fun `expressive dock exposes only fixed browser owned commands`() {
        val source = source("app/webora/browser/siteskin/SiteSkinDock.kt").readText()

        assertTrue("production dock must keep the closed command contract", fixedDock(source))
        assertFalse(
            "negative control: manifest-derived generic commands must be rejected",
            fixedDock(
                "ExpressiveSiteSkinDock(presentation) { " +
                    "model.items.forEach { DockCommand(it.icon, it.label) } }",
            ),
        )
    }

    @Test
    fun `integrated composition replaces persistent site navigation with the dock`() {
        val source = source("app/webora/browser/browser/BrowserScreen.kt").readText()
        val regularBrowser = source.substringAfter("internal fun RegularBrowser(")

        assertTrue(regularBrowser.contains("SiteSkinDock("))
        assertFalse(regularBrowser.contains("SiteSkinBottomNavigation("))
        assertFalse(regularBrowser.contains("SiteSkinQuickActions("))
    }

    @Test fun `browser sheet cannot consume SiteSkin presentation or action models`() {
        val source = source("app/webora/browser/siteskin/SiteSkinChrome.kt").readText()
        val sheet = source.substringAfter("internal fun IntegratedBrowserMenuSheet(")
            .substringBefore("internal fun browserMenuLabel(")

        assertTrue(sheet.contains("ModalBottomSheet("))
        assertTrue(sheet.contains("MaterialTheme.colorScheme"))
        assertFalse(sheet.contains("SiteSkinChromeModel"))
        assertFalse(sheet.contains("SiteSkinColorScheme"))
        assertFalse(sheet.contains("SiteSkinItemModel"))
        assertFalse(sheet.contains("presentation.colors"))
    }

    @Test
    fun `integrated back remains browser owned and cannot be suppressed by manifest styling`() {
        val source = source("app/webora/browser/siteskin/SiteSkinTopBar.kt").readText()

        assertTrue("integrated Back must use the browser-owned contract", browserOwnedBack(source))
        assertFalse(
            "negative control: a SiteSkin-coloured Back surface must be rejected",
            browserOwnedBack(
                "private fun BrowserBack() { " +
                    "WeboraIconButton(R.drawable.ic_back, stringResource(R.string.back), {}) " +
                    ".background(colors.background).testTag(SITESKIN_BACK_TAG) }",
            ),
        )
    }

    @Test
    fun `expressive header keeps security identity browser owned and unconditional`() {
        val source = source("app/webora/browser/siteskin/SiteSkinTopBar.kt").readText()

        assertTrue("production must keep the expressive ownership contract", expressiveIdentity(source))
        assertFalse(
            "negative control: SiteSkin-coloured conditional identity must be rejected",
            expressiveIdentity(
                "ExpressiveSiteSkinHeader(presentation) { if (model.subtitle != null) " +
                    "SecurityIdentity(model.security, presentation.colors) }",
            ),
        )
    }

    private fun expressiveIdentity(source: String): Boolean =
        source.contains("ExpressiveSiteSkinHeader(") &&
            source.contains("SecurityIdentity(model.security)") &&
            source.contains("MaterialTheme.colorScheme.surfaceContainer") &&
            !source.contains("SecurityIdentity(model.security, presentation.colors)") &&
            !source.contains("if (model.security")

    private fun fixedDock(source: String): Boolean {
        val order = listOf("ic_back", "ic_forward", "BrandHubCommand", "ic_tabs", "ic_more")
        val positions = order.map(source::indexOf)
        return source.contains("ExpressiveSiteSkinDock(") &&
            positions.all { it >= 0 } && positions == positions.sorted() &&
            source.contains("asset = brandAsset") &&
            source.contains("BrandAsset.BitmapAsset") &&
            !source.contains("MaterialTheme.colorScheme.surfaceContainer") &&
            !source.contains("model.items") && !source.contains("forEach { DockCommand")
    }

    @Test fun `Bloom semantic icons stay in the closed local vocabulary`() {
        val expected = mapOf(
            "home" to app.webora.browser.R.drawable.ic_home,
            "grid_view" to app.webora.browser.R.drawable.ic_siteskin_catalog,
            "shopping_cart" to app.webora.browser.R.drawable.ic_siteskin_shopping_cart,
            "person" to app.webora.browser.R.drawable.ic_siteskin_person,
            "call" to app.webora.browser.R.drawable.ic_siteskin_call,
        )
        expected.forEach { (token, resource) ->
            org.junit.Assert.assertEquals(resource, siteSkinIconResource(token))
        }
        org.junit.Assert.assertEquals(
            app.webora.browser.R.drawable.ic_siteskin_generic,
            siteSkinIconResource("remote-resource://hostile"),
        )
    }

    private fun browserOwnedBack(source: String): Boolean {
        val start = source.indexOf("private fun BrowserBack(")
        val end = source.indexOf("private fun BrandLogo(", start)
        if (start < 0 || end < 0) return false
        val back = source.substring(start, end)
        return back.contains("WeboraIconButton(") &&
            back.contains("R.drawable.ic_back") &&
            back.contains("stringResource(R.string.back)") &&
            back.contains("MaterialTheme.colorScheme.surfaceContainer") &&
            back.contains("testTag(SITESKIN_BACK_TAG)") &&
            !back.contains("colors.")
    }

    private fun source(relative: String): File = File(
        requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY))
            .split(File.pathSeparator)
            .map(::File)
            .single { it.invariantSeparatorsPath.endsWith("/src/main/java") },
        relative,
    )

    private companion object {
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
    }
}
