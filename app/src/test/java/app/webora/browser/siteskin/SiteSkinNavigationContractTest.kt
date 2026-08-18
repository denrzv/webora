package app.webora.browser.siteskin

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinNavigationContractTest {
    /**
     * The dock's commands are compiled and ordered, and `UX-024` re-stated the list rather than
     * shortening it.
     *
     * `UX-015` compiled five; Back and Forward moved into the header's navigation hub, because issue
     * #122 forbids ending with two controls competing for the same browser command semantics.
     * Deleting two names from an ordered list is indistinguishable from weakening the rule, so the
     * re-statement ships with a second control: a fourth command driven by the site's action count
     * must be rejected by the same predicate that accepts the three.
     */
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
        assertFalse(
            "negative control: a fourth slot driven by the site's action count must be rejected",
            fixedDock(
                "ExpressiveSiteSkinDock(presentation) { BrandHubCommand(asset = brandAsset); " +
                    "BrandAsset.BitmapAsset; ic_tabs; ic_more; " +
                    "siteActions.forEach { DockCommand(ic_more) } }",
            ),
        )
        assertFalse(
            "the browser commands the hub now owns must not also live in the dock",
            listOf("SITESKIN_DOCK_BACK_TAG", "SITESKIN_DOCK_FORWARD_TAG").any { it in source },
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
        val source = source("app/webora/browser/siteskin/BrowserNavigationHub.kt").readText()

        assertTrue("integrated Back must use the browser-owned contract", browserOwnedBack(source))
        assertFalse(
            "negative control: a SiteSkin-coloured Back surface must be rejected",
            browserOwnedBack(
                "private fun NavigationBubble() { " +
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
     * `surfaceContainer` is deliberately no longer required here. `UX-024`'s navigation hub still
     * grounds on it and `browserOwnedBack` still checks that; requiring it in *this* predicate would
     * now be asserting one surface's ground while claiming to describe another's.
     */
    private fun expressiveIdentity(source: String): Boolean =
        source.contains("ExpressiveSiteSkinHeader(") &&
            source.contains("SiteSkinSecurityChip(model.security)") &&
            source.contains("MaterialTheme.colorScheme.primaryContainer") &&
            !source.contains("SiteSkinSecurityChip(model.security, presentation") &&
            !source.contains("if (model.security")

    private fun fixedDock(source: String): Boolean {
        val order = listOf("BrandHubCommand", "ic_tabs", "ic_more")
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
     * So the ground is checked where it is declared, and Back is required to reach it through the
     * shared mechanism. A Back that paints its own surface is still rejected, by the `colors.`
     * clause and by the missing bundled icon — two independent reasons, which is the direction to be
     * wrong in.
     *
     * `UX-024` re-pointed it once more, at the file rather than at a declaration. Back is no longer
     * its own composable: it is one row of a compiled command list rendered by one bubble, so the
     * rule "Back is browser-owned" is now the rule "every command in this file is", and the
     * mechanism is the closed icon/label maps plus the shared tile's ground. Following the
     * indirection rather than matching the previous spelling is `BROWSE-009`'s rule and the reason
     * `BROWSE-011` had to move this predicate the first time.
     */
    private fun browserOwnedBack(source: String): Boolean {
        val bubble = declaration(source, "private fun NavigationBubble(") ?: return false
        val icons = declaration(source, "private fun navigationIcon(")
        val labels = declaration(source, "private fun navigationLabel(")
        val tile = declaration(source, "private fun BrowserControlTile(")
        val browserGround = tile?.contains("MaterialTheme.colorScheme.surfaceContainer") == true &&
            !tile.contains("colors.")
        return bubble.contains("WeboraIconButton(") &&
            icons?.contains("R.drawable.ic_back") == true &&
            labels?.contains("R.string.back") == true &&
            bubble.contains("testTag(navigationTag(action.command))") &&
            browserGround &&
            !bubble.contains("colors.")
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
        val next = Regex("(private|internal) fun ").find(source, start + signature.length)
        return source.substring(start, next?.range?.first ?: source.length)
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
