package app.webora.browser.browser

import androidx.compose.ui.graphics.Color
import app.webora.browser.design.WeboraColorScheme
import app.webora.browser.design.WeboraColors
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab switcher's presentation contract, in `BrowserChromeContractTest`'s idiom.
 *
 * The gate is JVM-only and there is no Robolectric, so the assertions that must not be skippable
 * live here and the Compose behaviour lives in `TabSwitcherTest` as instrumented evidence. Each
 * assertion carries its own intentionally-broken counter-example: a structural scan that stops
 * matching passes forever, and a colour assertion that would hold for any two roles proves nothing.
 */
class TabSwitcherContractTest {

    @Test
    fun `selected and unselected rows separate in both projections`() {
        // The role pair is the one decision in this surface that can be wrong invisibly, so the
        // separation is asserted against the compiled palette rather than left to the call site.
        listOf(WeboraColors.LIGHT to "light", WeboraColors.DARK to "dark").forEach { (scheme, name) ->
            assertNotEquals(
                "the selected and unselected row containers collapse in the $name projection",
                scheme.selectedContainer(),
                scheme.unselectedContainer(),
            )
            assertNotEquals(
                "the selected and unselected row content colours collapse in the $name projection",
                scheme.selectedContent(),
                scheme.unselectedContent(),
            )
        }
    }

    @Test
    fun `the rejected role pair is rejected for a reason that is still true`() {
        // Negative control for the assertion above, and the record of why the obvious pairing was
        // not used. surfaceVariant/primaryContainer map from chrome/container, and those are the
        // same value in the dark projection — a selected state built from them passes every
        // light-theme check and is invisible in dark mode. If a palette edit ever separates them,
        // this fails and the choice can be revisited deliberately rather than by accident.
        assertTrue(
            "chrome and container no longer collapse in the dark projection; the pair this surface " +
                "rejected may now be usable, and the rejection should be re-argued rather than kept",
            WeboraColors.DARK.chrome == WeboraColors.DARK.container,
        )
        assertFalse(
            "the light projection must still separate them, or the collapse is not projection-specific",
            WeboraColors.LIGHT.chrome == WeboraColors.LIGHT.container,
        )
    }

    @Test
    fun `the switcher is a full-window modal, not a confirmation box`() {
        val source = switcher()

        assertTrue("current tab switcher must be a full-window modal", isFullWindowModal(source))
        assertFalse(
            "negative control: an AlertDialog switcher must be rejected",
            isFullWindowModal(
                "AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.tabs_title)) })",
            ),
        )
        assertFalse(
            "negative control: a platform-width dialog must be rejected",
            isFullWindowModal("Dialog(onDismissRequest = onDismiss) { Surface(Modifier.fillMaxSize()) {} }"),
        )
    }

    @Test
    fun `the row select affordance is not a filled button`() {
        val source = switcher()

        assertTrue("current select affordance must be a tonal surface", isTonalSelectAffordance(source))
        assertFalse(
            "negative control: a filled button carrying the select tag must be rejected",
            isTonalSelectAffordance(
                """WeboraButton(onClick = { onSelect(summary.id) },
                   modifier = Modifier.testTag("${'$'}TAB_SELECT_TAG${'$'}{summary.id}"))""",
            ),
        )
    }

    @Test
    fun `close is a named icon control held apart from select by an unmerged row`() {
        val source = switcher()

        // Surface(onClick) marks its node mergeDescendants. A close control nested inside the select
        // affordance would resolve to the same semantics node, and both tags are an instrumented
        // contract, so the sibling structure is the thing being asserted — not merely the presence
        // of an icon button.
        val selectStart = source.indexOf("onClick = { onSelect(")
        val selectEnd = source.indexOf("WeboraIconButton(", selectStart)
        assertTrue("the select surface must be declared before the close control", selectStart in 0 until selectEnd)
        assertFalse(
            "the close control must be a sibling of the select surface, never inside it",
            source.substring(selectStart, selectEnd).contains(TAB_CLOSE_TAG),
        )
        assertTrue(
            "the close control must be named from resources",
            source.contains("contentDescription = closeDescription"),
        )
        assertFalse(
            "no raw IconButton may reach this surface",
            // \b is what separates the two: `aI` is word-to-word, so this never matches the wrapper's
            // own name — which a plain `contains("IconButton(")` does, and did.
            Regex("""\bIconButton\(""").containsMatchIn(source),
        )
    }

    @Test
    fun `the instrumented tag contract survives the restyle`() {
        // These four constants are what TabSwitcherTest addresses. A restyle that renames them is a
        // silently invalidated instrumented suite rather than a failing one.
        assertTrue(TAB_LIST_TAG == "tab_list")
        assertTrue(NEW_TAB_TAG == "new_tab")
        assertTrue(TAB_SELECT_TAG == "select_tab_")
        assertTrue(TAB_CLOSE_TAG == "close_tab_")

        val source = switcher()
        listOf("testTag(TAB_LIST_TAG)", "testTag(NEW_TAB_TAG)", TAB_SELECT_TAG, TAB_CLOSE_TAG).forEach { tag ->
            assertTrue("$tag must still be applied in the switcher", source.contains(tag))
        }
    }

    @Test
    fun `no website-controlled value reaches the switcher`() {
        val source = switcher()

        // BROWSE-006's labelling policy: a row says Home, a registrable domain, or Page. The
        // restyle must not have opened a seam for manifest branding, a document title, an editable
        // address, or a per-tab remote asset.
        listOf(
            "SiteSkinConfiguration",
            "SiteSkinColorScheme",
            "SiteSkinTheme",
            "BrandAsset",
            "NavigationItem",
            "addressText",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not reach the tab switcher", source.contains(forbidden))
        }
    }

    private fun isFullWindowModal(source: String): Boolean =
        source.contains("Dialog(") &&
            source.contains("usePlatformDefaultWidth = false") &&
            source.contains("Modifier.fillMaxSize()") &&
            !source.contains("AlertDialog(")

    /**
     * `$` starts a template in a Kotlin raw string and there is no `\$` escape there, so a literal
     * dollar has to be spelled `${'$'}` — the first version of this read as `\` + the value of
     * `TAB_SELECT_TAG`, which regex-compiled to `\s` and matched nothing in either direction.
     */
    private fun selectTagWithin(construct: String): Regex =
        Regex("""$construct\([\s\S]{0,400}testTag\("\${'$'}TAB_SELECT_TAG""")

    private fun isTonalSelectAffordance(source: String): Boolean =
        selectTagWithin("Surface").containsMatchIn(source) &&
            !selectTagWithin("WeboraButton").containsMatchIn(source)

    private fun switcher(): String = source("app/webora/browser/browser/BrowserTabSummary.kt").readText()

    private fun source(relative: String): File = File(
        requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY))
            .split(File.pathSeparator)
            .map(::File)
            .single { it.invariantSeparatorsPath.endsWith("/src/main/java") },
        relative,
    )

    /** The four role reads `TabRow` performs, named once so the test and the surface cannot drift. */
    private fun WeboraColorScheme.selectedContainer(): Color = primary
    private fun WeboraColorScheme.selectedContent(): Color = onPrimary
    private fun WeboraColorScheme.unselectedContainer(): Color = chrome
    private fun WeboraColorScheme.unselectedContent(): Color = ink

    private companion object {
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
    }
}
