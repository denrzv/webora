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
    fun `the reload decision has one owner and both chromes reach it`() {
        // `BROWSE-011`. The same shape as the transport-label case below, and filed for the same
        // reason: the regular dock and the integrated header are two surfaces offering one command,
        // so a rule written at each call site is a rule that can be re-pointed in one of them with
        // nothing failing. The regular arm used to carry `state.displayedUrl.isNotBlank()` and
        // `controller::reload` inline; both now come from `refreshAction`.
        val screen = executableLines(source("app/webora/browser/browser/BrowserScreen.kt"))

        assertTrue(
            "the browser must dispatch reload through the one decision",
            screen.contains("when (val action = refreshAction(state))"),
        )
        // Both chromes reach it, asserted positively: the regular dock's two reload arguments name
        // the shared values rather than anything computed beside them, and the integrated header
        // receives the same pair. A negative on one old spelling would be satisfied by writing the
        // rule a second way, which is the mistake `BROWSE-009` records about `update(activeTabId)`.
        assertTrue("the regular dock's enabled state is the shared one", screen.contains("canReload = canRefresh"))
        assertTrue("and so is its callback", screen.contains("onReload = onRefresh"))
        assertTrue("the integrated header receives the same pair", screen.contains("canRefresh = canRefresh"))
        assertTrue("and the same callback", screen.contains("onRefresh = onRefresh"))
        assertFalse(
            "the regular dock must not reach the renderer directly for reload",
            screen.contains("onReload = controller::reload"),
        )

        // And the decision itself lives in exactly one file. `ResolvedAction.Refresh` is deliberately
        // excluded: that is the *site's* item, dispatched through `ActionResolver`, and collapsing it
        // into the browser's command would make a manifest able to reach browser chrome.
        val owners = listOf(
            "app/webora/browser/browser/RefreshAction.kt",
            "app/webora/browser/browser/BrowserScreen.kt",
            "app/webora/browser/browser/BrowserChrome.kt",
            "app/webora/browser/siteskin/SiteSkinTopBar.kt",
        ).filter { decidesReload(executableLines(source(it))) }

        assertEquals(listOf("app/webora/browser/browser/RefreshAction.kt"), owners)
        assertFalse(
            "negative control: dispatching a decision must not read as owning it",
            decidesReload("when (action) { is RefreshAction.Retry -> navigate(action.url) }"),
        )
        assertTrue(
            "negative control: a second file constructing one must be detected",
            decidesReload("val a = if (state.loadFailure != null) RefreshAction.Retry(u) else null"),
        )
        assertTrue(
            "and the real owner must be detected",
            decidesReload(executableLines(source("app/webora/browser/browser/RefreshAction.kt"))),
        )
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

    /**
     * Does this text *decide* what refreshing means, as opposed to dispatching a decision?
     *
     * Keyed on **constructing** a `RefreshAction`, which is the decision itself. The first version
     * of this predicate looked for `loadFailure` beside a `navigate(` and a `displayedUrl` and
     * reported `BrowserScreen` as a second owner — all three appear there for unrelated reasons,
     * including `BrowserErrorPage`'s own Retry. Co-occurrence in a whole file is not a mechanism.
     *
     * A `when` branch (`is RefreshAction.Retry ->`) is matching, not constructing, so the dispatcher
     * is correctly not an owner — otherwise this would forbid the wiring it exists to require.
     */
    private fun decidesReload(source: String): Boolean =
        source.contains("RefreshAction.Retry(") ||
            Regex("""return\s+RefreshAction\.(Reload|None)""").containsMatchIn(source)

    /**
     * Source with comment lines removed. `BROWSE-009`: a scan reads executable lines, never
     * `readText()`, or the prose above an assertion will satisfy or violate it on the code's behalf.
     * This file's new case names `onReload = controller::reload` in a comment, and would fail its own
     * assertion without this.
     */
    private fun executableLines(file: File): String = file.readLines()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
        }
        .joinToString("\n")

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
