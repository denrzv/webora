package app.webora.browser.browser

import app.webora.browser.design.WeboraChrome
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserChromeContractTest {

    @Test
    fun `direction A chrome geometry is named without a touch target alias`() {
        assertTrue(WeboraChrome.ADDRESS_HEIGHT.value == 52f)
        assertTrue(WeboraChrome.DOCK_HEIGHT.value == 60f)
        assertTrue(WeboraChrome.SLOT_SIZE.value == 40f)
        assertTrue(WeboraChrome.ICON_SIZE.value == 20f)

        val dimensions = source("app/webora/browser/design/WeboraDimensions.kt").readText()
        assertFalse(
            "chrome geometry must not duplicate the accessibility layer's touch-target owner",
            Regex("val\\s+\\w*TOUCH_TARGET", RegexOption.IGNORE_CASE).containsMatchIn(dimensions),
        )
    }

    @Test
    fun `identity and editable address are structurally separate`() {
        val source = source("app/webora/browser/browser/BrowserChrome.kt").readText()

        assertTrue("current browser chrome must keep a separate identity", hasSeparateIdentity(source))
        assertFalse(
            "negative control: an address field used as identity must be rejected",
            hasSeparateIdentity(
                "BasicTextField(value = state.addressText, " +
                    "modifier = Modifier.testTag(BROWSER_SECURITY_TAG))",
            ),
        )
    }

    @Test
    fun `browser navigation cannot become page-render evidence`() {
        val source = source("app/webora/browser/browser/BrowserScreen.kt").readText()

        assertTrue("page must sit between top chrome and the regular dock", chromeFramesPage(source))
        assertFalse(
            "negative control: chrome nested after the page tag must be rejected",
            chromeFramesPage(
                "Box(Modifier.testTag(BROWSER_CONTENT_TAG)) { BrowserNavigationDock() }",
            ),
        )
        assertFalse(
            "negative control: a top dock must be rejected",
            chromeFramesPage(
                "BrowserChrome(); BrowserNavigationDock(); " +
                    "Box(Modifier.testTag(BROWSER_CONTENT_TAG))",
            ),
        )
    }

    @Test
    fun `regular dock has a fixed browser-owned command contract`() {
        val source = source("app/webora/browser/browser/BrowserChrome.kt").readText()
        val dock = source
            .substringAfter("internal fun BrowserNavigationDock(")
            .substringBefore("@Composable\nprivate fun BrowserOverflowMenu")

        val controls = listOf("ic_back", "ic_forward", "ic_reload", "ic_home", "ic_tabs", "ic_more")
        assertTrue(controls.zipWithNext().all { (first, second) -> dock.indexOf(first) < dock.indexOf(second) })
        assertTrue(dock.contains("canReload: Boolean"))
        assertFalse(dock.contains("SiteSkinConfiguration"))
        assertFalse(dock.contains("NavigationItem"))
        assertFalse(dock.contains("configuration"))
    }

    @Test
    fun `the transport label mapping has one owner`() {
        // UX-021 shipped this `when` twice, verbatim, in regular chrome and the integrated chip,
        // while its own documentation claimed one guarantee could not be worded two ways. Sharing
        // the four strings was never enough: a fifth state produced two compile errors that could
        // be resolved differently, and a re-pointed branch drifted with nothing failing. Assert the
        // mechanism — exactly one file maps the enum to resources.
        val owners = listOf(
            "app/webora/browser/browser/TransportLabel.kt",
            "app/webora/browser/browser/BrowserChrome.kt",
            "app/webora/browser/siteskin/SiteSkinTopBar.kt",
        ).filter { mapsTransport(source(it).readText()) }

        assertEquals(listOf("app/webora/browser/browser/TransportLabel.kt"), owners)
        assertTrue(
            "negative control: a second inline mapping must be detected",
            mapsTransport("when (t) { TransportSecurity.SECURE -> R.string.security_secure }"),
        )
    }

    private fun mapsTransport(source: String): Boolean =
        source.lines()
            .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
            .any { it.contains("TransportSecurity.SECURE ->") }

    private fun hasSeparateIdentity(source: String): Boolean =
        source.contains("BasicTextField(") &&
            source.contains("BrowserSecurityIdentity(") &&
            !Regex("BasicTextField\\([\\s\\S]{0,500}BROWSER_SECURITY_TAG").containsMatchIn(source)

    private fun chromeFramesPage(source: String): Boolean {
        val chrome = source.indexOf("BrowserChrome(")
        val page = source.indexOf("testTag(BROWSER_CONTENT_TAG)")
        val dock = source.indexOf("BrowserNavigationShell(", page)
        return chrome >= 0 && page > chrome && dock > page
    }

    @Test
    fun `Home and regular mode share one shell inside one safe drawing boundary`() {
        val screen = source("app/webora/browser/browser/BrowserScreen.kt").readText()
        val home = screen
            .substringAfter("if (state.mode == BrowserMode.Home)")
            .substringBefore("\n    } else {")

        assertTrue(home.contains("BrowserNavigationShell("))
        assertTrue(screen.substringAfter("internal fun RegularBrowser(").contains("BrowserNavigationShell("))
        assertTrue(screen.countSubstring("windowInsetsPadding(WindowInsets.safeDrawing)") == 1)
        assertFalse(home.contains("SiteSkinConfiguration"))
        assertFalse(home.contains("configuration ="))
    }

    @Test
    fun `regular renderer consumes one handoff projection and keeps protected top manifest independent`() {
        val screen = source("app/webora/browser/browser/BrowserScreen.kt").readText()
            .substringAfter("internal fun RegularBrowser(")
        val handoff = source("app/webora/browser/browser/ChromeHandoff.kt").readText()

        assertTrue(screen.contains("val handoff = state.mode.chromeHandoff()"))
        assertTrue(screen.contains("when (handoff.top)"))
        assertFalse(screen.contains("handoff.contentActions == ContentActions.SITESKIN"))
        assertFalse(screen.contains("SiteSkinQuickActions("))
        assertTrue(screen.contains("handoff.bottom == BottomChrome.SITESKIN"))
        assertFalse(handoff.contains("SiteSkinConfiguration"))
        assertFalse(handoff.contains("NavigationItem"))
    }

    private fun source(relative: String): File = File(
        requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY))
            .split(File.pathSeparator)
            .map(::File)
            .single { it.invariantSeparatorsPath.endsWith("/src/main/java") },
        relative,
    )

    private fun String.countSubstring(value: String): Int = windowed(value.length).count { it == value }

    private companion object {
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
    }
}
