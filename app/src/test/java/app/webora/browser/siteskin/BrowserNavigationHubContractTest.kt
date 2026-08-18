package app.webora.browser.siteskin

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigation hub is browser chrome, structurally.
 *
 * `UX-024` puts three browser commands inside a header a manifest paints, drawn as a bubble cluster
 * that deliberately shares a visual language with `UX-015`'s *site* bouquet. That resemblance is the
 * point of the design and the whole of the new risk: two clusters that look alike are one shared
 * item model away from a manifest publishing something that renders identically to Back.
 *
 * Runtime tests can show the bubbles appear. Only a source scan can show that no path exists from
 * the SiteSkin palette, the site's model or `ActionResolver` into this file — the difference `UX-003`
 * records between *"runtime behaviour and source structure fail under different regressions"*.
 *
 * **The rule is scoped to the file, not to a declaration.** A rule written against
 * `BrowserNavigationHub`'s own body would have to be re-scoped every time a helper is added beside
 * it, and re-scoping a rule is how it stops covering the thing it was written for. Nothing in this
 * file may name a site value; the assertions say exactly that.
 *
 * **The scan reads executable lines only** — `BROWSE-009`'s rule. This file's subject explains in its
 * own KDoc why it does not use `presentation.colors`, and a `readText()` would fail on the
 * explanation.
 */
class BrowserNavigationHubContractTest {

    @Test
    fun `no site value reaches the hub or any of its bubbles`() {
        val source = hubSource()

        assertTrue("the scan must see real code", "fun BrowserNavigationHub(" in source)
        FORBIDDEN.forEach { forbidden ->
            assertFalse("a site-controlled value reached a browser command: '$forbidden'", forbidden in source)
        }
    }

    @Test
    fun `browser commands and site items are different types end to end`() {
        // The one new impersonation path this ticket creates. `UX-015`'s `SiteSkinItemModel` carries
        // an icon, a label and a `NavigationItem` because a website supplies all three; a browser
        // command carries a closed enum and a Boolean. Sharing the type is what would let a manifest
        // entry render as Back, and the negative control is a one-line edit: change the bubble's
        // parameter to `SiteSkinItemModel`.
        val source = hubSource()

        assertTrue("the hub's items are the compiled action type", "BrowserNavigationAction" in source)
        listOf("SiteSkinItemModel", "NavigationItem", "ActionResolver", "ResolvedAction").forEach {
            assertFalse("$it belongs to the site's dispatch path, not the browser's: '$it'", it in source)
        }
    }

    @Test
    fun `every command draws a bundled icon and a browser-authored name`() {
        val icons = declaration("private fun navigationIcon(")
        val labels = declaration("private fun navigationLabel(")

        listOf("R.drawable.ic_back", "R.drawable.ic_forward", "R.drawable.ic_reload").forEach {
            assertTrue("a browser command must draw a bundled vector: $it", it in icons)
        }
        // `DEVX-003`: one command does not acquire two names because it is drawn on a second
        // surface. These are the same three resources regular chrome uses.
        listOf("R.string.back", "R.string.forward", "R.string.reload").forEach {
            assertTrue("a browser command must be named by a browser resource: $it", it in labels)
        }
        listOf("getIdentifier(", "Uri.parse(", "File(", "URL(").forEach {
            assertFalse("icon selection must not be dynamic: $it", it in icons)
        }
    }

    @Test
    fun `the collapsed control does not claim to be Back`() {
        // Criterion 2. A hub labelled `back` is a Back button that hides Forward and Refresh behind
        // it, which is the misleading affordance this ticket exists to remove. The negative control
        // is replacing the resource with `R.string.back`.
        val hub = declaration("internal fun BrowserNavigationHub(")

        assertTrue("the hub must carry its own browser-authored name", "R.string.siteskin_open_navigation" in hub)
        assertFalse("and it must not borrow a command's name", "R.string.back" in hub)
        assertFalse("nor a command's icon", "R.drawable.ic_back" in hub)
        assertTrue("it draws the history glyph", "R.drawable.ic_history" in hub)
    }

    @Test
    fun `the collapsed control is never disabled`() {
        // A hub that greys out at the history root *is* a Back button, and a user who cannot open it
        // also loses Forward and Refresh. Enabled state lives on the three children, which is where
        // `A11Y-001` requires it to be readable from the semantics tree.
        val hub = declaration("internal fun BrowserNavigationHub(")
        val bubble = declaration("private fun NavigationBubble(")

        assertFalse("the collapsed control must not take an enabled argument", "enabled =" in hub)
        assertTrue("the bubbles carry the state instead", "enabled = action.enabled" in bubble)
    }

    @Test
    fun `the bouquet is its own window so Back and outside taps are consumed structurally`() {
        // `UX-022`'s mechanism. A `Popup` has its own window, so Android and predictive Back are
        // consumed here before `BrowserBackHandler` sees them and an outside tap dismisses without
        // reaching the page. A handler inside the browser's window would be a second answer to
        // `BROWSE-002`'s single Back contract, racing the first.
        val source = hubSource()

        assertTrue("Popup(" in source)
        assertTrue("dismissOnBackPress = true" in source)
        assertTrue("dismissOnClickOutside = true" in source)
        assertTrue("focusable = true" in source)
        assertFalse(
            "a back handler here would race BrowserBackHandler",
            "BackHandler" in source,
        )
    }

    @Test
    fun `the bouquet expands by a direction-aware alignment`() {
        // `Alignment.TopStart` resolves against `LayoutDirection`, so RTL is correct with no separate
        // path. An absolute alignment or a negative horizontal offset would pin the cluster to a
        // physical side and be silently wrong in half the world's locales.
        val source = hubSource()

        assertTrue("Alignment.TopStart" in source)
        listOf("Alignment.TopEnd", "AbsoluteAlignment", "Alignment.Absolute").forEach {
            assertFalse("$it pins the bouquet to a physical edge", it in source)
        }
    }

    @Test
    fun `selecting a command closes the bouquet before dispatching it`() {
        // Criterion 5, as an ordering fact. The reverse order leaves the cluster composed over
        // whatever the command navigated to, which is the stale-overlay failure `UX-022` records.
        val bubble = declaration("private fun NavigationBubble(")
        val close = bubble.indexOf("state.onExpandedChange(false)")
        val dispatch = bubble.indexOf("state.onCommand(action.command)")

        assertTrue("the bubble must close the bouquet", close >= 0)
        assertTrue("and dispatch after closing, not before", dispatch > close)
    }

    @Test
    fun `browser controls in the header share one Webora-token sub-surface`() {
        // `UX-014`: the header's colours are the site's, so a browser control drawn straight onto
        // them reads as the site's — "the visual boundary is the ownership boundary". Moved here
        // from `SiteSkinTopBarContractTest` with the tile itself, because the header's browser
        // controls all live in this file now. The negative control is grounding the tile on
        // `presentation.colors.secondary`.
        val tile = declaration("private fun BrowserControlTile(")

        assertTrue(
            "the browser tile must ground on a Webora token",
            "MaterialTheme.colorScheme.surfaceContainer" in tile,
        )
        assertTrue("the collapsed hub must use it", "BrowserControlTile(SITESKIN_NAV_HUB_TAG)" in hubSource())
        assertTrue("and the tile is what applies the tag", "testTag(tag)" in tile)
    }

    @Test
    fun `each command's tag is applied to a node rather than merely declared`() {
        // `UX-020`'s lesson: all five constants are declared in this same file, so a bare `contains`
        // over a name would be satisfied by its declaration and would pass over a bouquet that had
        // dropped every tag. `SITESKIN_BACK_TAG` and `SITESKIN_REFRESH_TAG` keep the exact values
        // they carried on the header's standalone controls, and the hosted journey asserts on them.
        val tags = declaration("private fun navigationTag(")
        val bubble = declaration("private fun NavigationBubble(")

        listOf("SITESKIN_BACK_TAG", "SITESKIN_FORWARD_TAG", "SITESKIN_REFRESH_TAG").forEach {
            assertTrue("$it must be mapped from a command", it in tags)
        }
        assertTrue("and applied to the bubble's node", "testTag(navigationTag(action.command))" in bubble)
        assertEquals(
            "the tag values must not drift from what the hosted journey asserts",
            listOf("siteskin_back", "siteskin_forward", "siteskin_refresh"),
            listOf(SITESKIN_BACK_TAG, SITESKIN_FORWARD_TAG, SITESKIN_REFRESH_TAG),
        )
    }

    @Test
    fun `the scan reads code and not prose`() {
        // Guards the guard. Every assertion above is a `contains` over this projection, so a
        // regression in `executableLines` would quietly turn the file into decoration. Anchored on a
        // KDoc phrase that cannot also become code — `UX-021` recorded what happens when the anchor
        // is a string a real regression would legitimately add.
        val raw = hubFile().readText()
        val stripped = hubSource()

        assertTrue("the KDoc explaining the ownership rule is still there", PROSE_ONLY in raw)
        assertFalse("but the scan must not see prose", PROSE_ONLY in stripped)
        assertTrue("executable lines must survive the strip", "fun BrowserNavigationHub(" in stripped)
    }

    private companion object {
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"

        /** A sentence from the hub's KDoc. Prose, and unable to become code under any regression. */
        const val PROSE_ONLY = "which is the mechanism and not the decoration"

        val FORBIDDEN = listOf(
            "presentation",
            "SiteSkinColorScheme",
            "colors.",
            "model.",
            "configuration",
            "SiteSkinConfiguration",
        )

        fun hubFile(): File {
            val roots = requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY)) {
                "$SOURCE_ROOT_PROPERTY is unset; app/build.gradle.kts must pass the app source roots"
            }.split(File.pathSeparator).map(::File)

            return roots
                .map { File(it, "app/webora/browser/siteskin/BrowserNavigationHub.kt") }
                .firstOrNull(File::exists)
                ?: error("BrowserNavigationHub.kt not found under any of $roots")
        }

        fun hubSource(): String = hubFile().readLines()
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
            }
            .joinToString("\n")

        /**
         * One top-level declaration's own text, ending at the next top-level `fun` or at the end of
         * input, so a value used legitimately elsewhere in the file cannot satisfy or violate its
         * rule.
         */
        fun declaration(signature: String): String {
            val source = hubSource()
            val start = source.indexOf(signature)
            check(start >= 0) { "declaration not found: $signature" }
            val next = Regex("\n(private|internal) fun ").find(source, start + signature.length)
            return source.substring(start, next?.range?.first ?: source.length)
        }
    }
}
