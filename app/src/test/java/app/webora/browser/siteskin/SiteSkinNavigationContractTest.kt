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
                    "SiteSkinSecurityChip(model.security, presentation.colors) }",
            ),
        )
    }

    /**
     * `UX-014`'s ownership rule, re-pointed by `UX-021` at the surface that now carries it.
     *
     * The rule is unchanged — the header holds a security identity, it is browser-coloured, and no
     * manifest value gates or paints it. What changed is the shape it names: the full-width
     * `SecurityIdentity` row on `surfaceContainer` became `SiteSkinSecurityChip` on
     * `primaryContainer`, because `secure`/`notSecure` are measured only against `container` and
     * `surfaceContainer` maps to `chrome`.
     *
     * `surfaceContainer` is deliberately no longer required here. `BrowserBack` still uses it and
     * `browserOwnedBack` still checks that; requiring it in *this* predicate would now be asserting
     * one surface's ground while claiming to describe another's.
     */
    private fun expressiveIdentity(source: String): Boolean =
        source.contains("ExpressiveSiteSkinHeader(") &&
            source.contains("SiteSkinSecurityChip(model.security)") &&
            source.contains("MaterialTheme.colorScheme.primaryContainer") &&
            !source.contains("SiteSkinSecurityChip(model.security, presentation") &&
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

    /**
     * Integrated Back is a browser command on a browser-owned ground, wherever that ground is
     * declared.
     *
     * `BROWSE-011` is why this follows an indirection instead of matching one spelling. The rule had
     * been `MaterialTheme.colorScheme.surfaceContainer` *inside* `BrowserBack`, which was the same
     * thing as the rule only while Back was the header's one browser control. Adding Refresh gave
     * the two commands a shared `BrowserControlTile`, and Back's guarantee became just as true one
     * declaration away — while this assertion, reading a spelling, went red on code that had not
     * weakened. `BROWSE-009`: forbid the mechanism, not a spelling.
     *
     * So the tile is checked where it is declared, and Back is required to reach its ground through
     * it. A Back that paints its own surface is still rejected, by the `colors.` clause and by the
     * missing tile — two independent reasons, which is the direction to be wrong in.
     */
    private fun browserOwnedBack(source: String): Boolean {
        val back = declaration(source, "private fun BrowserBack(") ?: return false
        val tile = declaration(source, "private fun BrowserControlTile(")
        val browserGround = tile?.contains("MaterialTheme.colorScheme.surfaceContainer") == true &&
            !tile.contains("colors.")
        return back.contains("WeboraIconButton(") &&
            back.contains("R.drawable.ic_back") &&
            back.contains("stringResource(R.string.back)") &&
            back.contains("BrowserControlTile(SITESKIN_BACK_TAG)") &&
            browserGround &&
            !back.contains("colors.")
    }

    /**
     * One top-level declaration's text, ending at the next `private fun` or at the end of input.
     *
     * The end-of-input case is load-bearing for the negative control above, which is a one-line
     * fragment with nothing after it: the previous helper ended the slice at `private fun BrandLogo(`
     * and returned `false` for the fragment because that marker was absent, so the control was
     * passing for a reason unrelated to the violation it describes.
     */
    private fun declaration(source: String, signature: String): String? {
        val start = source.indexOf(signature)
        if (start < 0) return null
        val next = source.indexOf("private fun ", start + signature.length)
        return source.substring(start, if (next >= 0) next else source.length)
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
