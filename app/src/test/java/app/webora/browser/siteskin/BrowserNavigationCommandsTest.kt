package app.webora.browser.siteskin

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The browser's navigation commands are a compiled list, and this is where that is provable.
 *
 * `UX-024` moves Back, Forward and Refresh into one control drawn inside a header a manifest paints.
 * The wiring lives in a `@Composable`, so the decision has to leave it to be testable at all —
 * `BROWSE-009`'s reason for `routeRendererEvent` and `BROWSE-010`'s for `rendererMountAction`. What
 * leaves is this: the membership, the order and each command's enabled source.
 *
 * **Count/order and enabled state are separate assertions on purpose.** A function returning the
 * right three commands always enabled satisfies every membership check while reporting that Back is
 * available on a page with no history. Splitting them is what makes the second control bite.
 */
class BrowserNavigationCommandsTest {

    @Test
    fun `the list is always the three compiled commands in declared order`() {
        FLAG_COMBINATIONS.forEach { (back, forward, refresh) ->
            val actions = browserNavigationActions(back, forward, refresh)

            assertEquals(
                "canGoBack=$back canGoForward=$forward canRefresh=$refresh",
                ORDER,
                actions.map(BrowserNavigationAction::command),
            )
        }
    }

    @Test
    fun `each enabled flag tracks its own command and no other`() {
        // The anti-vacuity guard. Eight combinations, and for each one every command's enabled value
        // is compared against the input that owns it — so hard-coding `true`, hard-coding `false`,
        // or wiring Forward's flag to Back's all fail here while the order assertion stays green.
        FLAG_COMBINATIONS.forEach { (back, forward, refresh) ->
            val enabled = browserNavigationActions(back, forward, refresh)
                .associate { it.command to it.enabled }
            val label = "canGoBack=$back canGoForward=$forward canRefresh=$refresh"

            assertEquals(label, back, enabled[BrowserNavigationCommand.BACK])
            assertEquals(label, forward, enabled[BrowserNavigationCommand.FORWARD])
            assertEquals(label, refresh, enabled[BrowserNavigationCommand.REFRESH])
        }
    }

    @Test
    fun `every combination reaches both enabled states for every command`() {
        // Guards the guard above: if `FLAG_COMBINATIONS` were ever reduced to a single row, the
        // per-flag assertion would still pass and would be measuring nothing. Every command must be
        // observed both enabled and disabled across the set.
        ORDER.forEach { command ->
            val observed = FLAG_COMBINATIONS
                .flatMap { (back, forward, refresh) -> browserNavigationActions(back, forward, refresh) }
                .filter { it.command == command }
                .map(BrowserNavigationAction::enabled)
                .toSet()

            assertEquals("$command must be observed both enabled and disabled", setOf(true, false), observed)
        }
    }

    @Test
    fun `the command vocabulary is closed at three`() {
        // A fourth constant is a decision, and it should fail here rather than appear in a bouquet.
        // The issue's own words: "Tapping the hub opens exactly three actions."
        assertEquals(ORDER, BrowserNavigationCommand.entries.toList())
    }

    @Test
    fun `an action carries a command and an enabled flag and nothing a website could supply`() {
        // The one new impersonation path `UX-024` creates is a shared item model between the browser
        // bouquet and `UX-015`'s site bouquet, because the two look alike by design. A browser action
        // has no icon field, no label field and no `NavigationItem` — so there is nothing for a
        // manifest to populate even if a call site tried. Reflective so a field added later is
        // covered without anyone remembering, the shape `NET-004` uses for `BrandAssetTrace`.
        //
        // Instance fields only. The Compose compiler adds a `public static final int $stable` to
        // every stable class it sees, and it is not `isSynthetic` — a filter that only drops
        // synthetics reports it as a property of the model and fails on correct code. What this
        // assertion is about is what an *instance* carries, which is what a call site could populate.
        val properties = BrowserNavigationAction::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

        assertEquals(setOf("command", "enabled"), properties)
        assertTrue(
            "an action's command must be the closed enum",
            BrowserNavigationAction::class.java.getDeclaredField("command").type ==
                BrowserNavigationCommand::class.java,
        )
        assertTrue(
            "an action's enabled state must be a plain boolean",
            BrowserNavigationAction::class.java.getDeclaredField("enabled").type == java.lang.Boolean.TYPE,
        )
    }

    private companion object {
        val ORDER = listOf(
            BrowserNavigationCommand.BACK,
            BrowserNavigationCommand.FORWARD,
            BrowserNavigationCommand.REFRESH,
        )

        /** All eight, so no combination of history and refresh availability is unexercised. */
        val FLAG_COMBINATIONS: List<Triple<Boolean, Boolean, Boolean>> =
            listOf(false, true).flatMap { back ->
                listOf(false, true).flatMap { forward ->
                    listOf(false, true).map { refresh -> Triple(back, forward, refresh) }
                }
            }
    }
}
