package app.webora.browser.browser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The renderer-ownership rules that live in the view hierarchy, checked by reading the sources.
 *
 * The behaviour is instrumented in `TabRendererIsolationTest`, and instrumented results are evidence
 * rather than a gate claim — `A11Y-001`. This file is what the JVM gate can hold: the structural
 * facts each of those behaviours depends on. They are exactly the three that were wrong, and each is
 * a one-line edit away from being wrong again:
 *
 * - an `AndroidView` at one un-keyed call site serves every tab from one retained slot;
 * - `rememberUpdatedState` re-points the observer, which is only safe while the event names its own
 *   tab and the handler addresses that name;
 * - a detach that does not remove the view from its parent turns the next selection into
 *   `IllegalStateException: The specified child already has a parent`.
 *
 * Every assertion carries a negative control, because a source scan that matches nothing passes
 * forever.
 */
class RendererHostContractTest {

    @Test
    fun `the renderer host is keyed by tab, inside the page measurement region`() {
        val screen = source("app/webora/browser/browser/BrowserScreen.kt").readText()

        assertTrue("the WebView host must be keyed by the owning tab id", hostIsKeyedByTab(screen))
        assertFalse(
            "negative control: one un-keyed host serving every tab must be rejected",
            hostIsKeyedByTab(
                "Box(Modifier.testTag(BROWSER_CONTENT_TAG)) { HardenedWebView(controller = controller) }",
            ),
        )
        assertFalse(
            "negative control: keying outside the measured region moves CI-003's rectangle",
            hostIsKeyedByTab(
                "key(controller.tabId) { Box(Modifier.testTag(BROWSER_CONTENT_TAG)) { HardenedWebView() } }",
            ),
        )
    }

    @Test
    fun `a renderer event names its owner`() {
        val host = source("app/webora/browser/web/HardenedWebView.kt").readText()

        // The owner is read once, into a local, and every event constructed from it. Reading
        // `controller.tabId` per callback would be the same value today and an invitation to make
        // it dynamic tomorrow, which is the shape the defect had.
        assertTrue(host.contains("val owner = controller.tabId"))
        assertTrue(
            "every emitted event must carry the owner",
            Regex("""WebViewEvent\.\w+\(\s*owner""").findAll(host).count() == EMITTED_EVENTS,
        )
    }

    @Test
    fun `the renderer path cannot address the selected tab`() {
        // The first version of this assertion was `!screen.contains("update(activeTabId)")`, which
        // `BrowserSession.updateActive` — literally `update(activeId, …)` — satisfies. A regression
        // written as `session.updateActive { it.observe(rendererObservation) }` restored the whole
        // defect and passed. Banning `updateActive` from the screen outright is not available
        // either: four user-action call sites legitimately use it (Home reset, address edit, and
        // SiteSkin deactivation from the privacy toggle and from clear-data). So assert the *path*.
        // Code, not prose: the router's own KDoc says nothing in it may consult
        // `BrowserSession.activeId`, and the first version of this assertion failed on that
        // sentence. Third instance of the same trap in this ticket, after `handler.proceed(`
        // matching its own KDoc and `UX-002`'s wrapper exemption exempting the file describing it.
        val router = source("app/webora/browser/browser/RendererOwnership.kt").readText()
        val screen = source("app/webora/browser/browser/BrowserScreen.kt").readText()

        assertTrue("the router must not know which tab is selected", routerIgnoresSelection(router))
        assertFalse(
            "negative control: a router reading the selection must be rejected",
            routerIgnoresSelection("val tabId = session.activeId"),
        )
        assertFalse(
            "negative control: a router taking the active-addressed shortcut must be rejected",
            routerIgnoresSelection("session.updateActive { it.observe(event.toBrowserObservation()) }"),
        )
        assertTrue("the screen must route renderer events, not mutate for them", screenDelegates(screen))
        assertFalse(
            "negative control: a renderer handler that mutates the session itself must be rejected",
            // Deliberately keeps both required parts, so it can only fail on the mutation itself.
            // A control missing the routing call would fail on the wrong condition and prove
            // nothing about the rule being added.
            screenDelegates(
                "val applyRendererEvent: (WebViewEvent) -> Unit = { event ->\n" +
                    "    val routing = routeRendererEvent(session, book, event)\n" +
                    "    session = routing.session\n" +
                    "    session = session.updateActive { it.observe(event.toBrowserObservation()) }\n" +
                    "}\n",
            ),
        )
    }

    @Test
    fun `detaching removes the renderer from its parent and destroying detaches first`() {
        val controller = source("app/webora/browser/web/BrowserWebViewController.kt").readText()
        val host = source("app/webora/browser/web/HardenedWebView.kt").readText()

        assertTrue("detach must actually remove the view", detachRemovesFromParent(controller))
        assertFalse(
            "negative control: a detach that only compares the view must be rejected",
            detachRemovesFromParent("fun detachFromParent() { if (this.webView !== webView) return }"),
        )
        assertTrue("destroy must leave the hierarchy first", destroyDetachesFirst(controller))
        assertFalse(
            "negative control: destroying an attached view must be rejected",
            destroyDetachesFirst("fun destroy() { webView?.destroy(); detachFromParent() }"),
        )
        // The dispose path reads the one owner rather than a body-local `var` that every
        // recomposition reset to null — which is why the old detach was never called with a view.
        assertTrue(host.contains("onDispose(controller::detachFromParent)"))
        assertFalse(host.contains("var attachedWebView"))
    }

    private fun hostIsKeyedByTab(source: String): Boolean {
        val region = source.indexOf("testTag(BROWSER_CONTENT_TAG)")
        val key = source.indexOf("key(controller.tabId)")
        val host = source.indexOf("HardenedWebView(")
        return region >= 0 && key > region && host > key
    }

    /** Executable lines only, with comments dropped so prose cannot participate in a rule. */
    private fun code(text: String): String = text.lines()
        .map(String::trim)
        .filterNot { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }
        .joinToString("\n")

    /** The selection is a concept the router may not have — by either of its two spellings. */
    private fun routerIgnoresSelection(source: String): Boolean = code(source).let {
        !it.contains("updateActive") && !it.contains("activeId")
    }

    /**
     * `applyRendererEvent` delegates and performs; it does not decide.
     *
     * The only assignment it may make to `session` is the routing's own result, so a session
     * mutation written inside the renderer handler fails here whichever id it names.
     */
    private fun screenDelegates(source: String): Boolean {
        val handler = code(source)
            .substringAfter("val applyRendererEvent: (WebViewEvent) -> Unit = { event ->", "")
            .substringBefore("\nval downloadMessages")
        return handler.contains("routeRendererEvent(session, book, event)") &&
            handler.contains("session = routing.session") &&
            !Regex("""session = session\.""").containsMatchIn(handler)
    }

    private fun detachRemovesFromParent(source: String): Boolean =
        Regex("""fun detachFromParent\(\)[\s\S]{0,400}?removeView\(view\)""").containsMatchIn(source)

    private fun destroyDetachesFirst(source: String): Boolean =
        Regex("""fun destroy\(\)[\s\S]{0,200}?detachFromParent\(\)[\s\S]{0,200}?webView\?\.destroy\(\)""")
            .containsMatchIn(source)

    private fun source(relative: String): File = File(
        requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY))
            .split(File.pathSeparator)
            .map(::File)
            .single { it.invariantSeparatorsPath.endsWith("/src/main/java") },
        relative,
    )

    private companion object {
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"

        /** `PageStarted`, `PageChanged`, `MainFrameCompleted`, `MainFrameFailed`. */
        const val EMITTED_EVENTS = 4
    }
}
