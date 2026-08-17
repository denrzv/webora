package app.webora.browser.web

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The renderer host's structure, in `BrowserChromeContractTest`'s idiom.
 *
 * The mount decision is a pure function the gate drives directly; what a source scan adds is that
 * the host actually *asks* it, and that the two placements the decision depends on are still where
 * the decision assumes they are. Each assertion carries an intentionally-broken counter-example,
 * because a structural scan that stops matching passes forever.
 */
class RendererHostContractTest {

    @Test
    fun `the host asks the decision rather than testing for a new renderer`() {
        val source = host()

        assertTrue("the host must consult rendererMountAction", asksTheDecision(source))
        assertFalse(
            "negative control: the original new-renderer condition must be rejected",
            asksTheDecision("val existing = controller.attached()\nif (existing == null) loadUrl(initialUrl)"),
        )
    }

    @Test
    fun `a failed navigation does not move the hosted url`() {
        // The row that keeps an error tab from reloading on every switch. onMainFrameFailed must
        // report the failure and leave the browser's last request standing.
        val failure = callbackBlock(host(), "onMainFrameFailed")

        assertTrue("onMainFrameFailed must still report the failure", failure.contains("MainFrameFailed("))
        assertFalse("onMainFrameFailed must not record a hosted url", failure.contains("observed("))
    }

    @Test
    fun `every reporting callback records what the renderer reported`() {
        // The half that makes in-page navigation safe. Losing it on any one callback reintroduces a
        // reload on switch for the pages that callback is the only reporter of.
        val source = host()

        listOf("onPageStarted", "onPageChanged", "onMainFrameCompleted").forEach { callback ->
            assertTrue(
                "$callback must record the reported url",
                callbackBlock(source, callback).contains("controller.observed("),
            )
        }
    }

    @Test
    fun `no page-authored value reaches the mount decision`() {
        // The decision chooses whether the browser re-issues a navigation and to where. A document
        // title or a manifest field reaching it would let a page cause a navigation.
        val decision = executableLines(source("app/webora/browser/web/RendererMountAction.kt"))

        listOf("title", "SiteSkinConfiguration", "NavigationItem", "WebView").forEach { forbidden ->
            assertFalse("$forbidden must not reach the mount decision", decision.contains(forbidden))
        }
    }

    private fun asksTheDecision(source: String): Boolean =
        source.contains("rendererMountAction(") &&
            !NEW_RENDERER_LOAD.containsMatchIn(source)

    /** The body of one `HardenedWebViewClient` callback, from its lambda header to the next one. */
    private fun callbackBlock(source: String, callback: String): String {
        val start = source.indexOf("$callback = {")
        require(start >= 0) { "$callback is no longer a callback of the hardened client" }
        val next = source.indexOf(" = {", start + callback.length + 4)
        return if (next < 0) source.substring(start) else source.substring(start, next)
    }

    private fun host(): String = executableLines(source("app/webora/browser/web/HardenedWebView.kt"))

    /**
     * Source with comments removed.
     *
     * `BROWSE-009` hit the inverse three times in one ticket: `handler.proceed(` matched its own
     * KDoc, and `activeId` matched the router comment forbidding it. Prose must not satisfy or
     * violate a rule on the code's behalf, in either direction.
     */
    private fun executableLines(file: File): String = file.readLines()
        .filterNot { line -> COMMENT_PREFIXES.any { line.trimStart().startsWith(it) } }
        .joinToString("\n")

    private fun source(relative: String): File = File(
        requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY))
            .split(File.pathSeparator)
            .map(::File)
            .single { it.invariantSeparatorsPath.endsWith("/src/main/java") },
        relative,
    )

    private companion object {
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"

        val COMMENT_PREFIXES = listOf("*", "//", "/*")

        /** The condition `BROWSE-010` removed: a load gated on the `WebView` being new. */
        val NEW_RENDERER_LOAD = Regex("""if\s*\(existing == null\)\s*loadUrl""")
    }
}
