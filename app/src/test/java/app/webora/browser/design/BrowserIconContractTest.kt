package app.webora.browser.design

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The browser-owned icon set, checked by reading the drawables.
 *
 * `C6` in `docs/design/AUDIT.md` bounds the set deliberately: "an icon a direction needs is an icon
 * someone has to draw and check at 200% font scale." A bound nothing counts is a suggestion, so the
 * budget is asserted — an eleventh icon is a decision to raise it, which is what `UX-005` will do
 * when the SiteSkin chrome's semantic icons replace `SiteSkinChrome.kt`'s Unicode glyphs.
 *
 * The set is the ten Direction A actually draws across its four surfaces, not the eight `C6` listed
 * before a direction was chosen — that list named `stop`, which Direction A does not draw, and
 * omitted `search`, `more` and `warning`, which it does. Deriving the set from the selected
 * direction rather than the pre-selection estimate is the point of having selected one.
 *
 * Every icon is stroke-only and declares one colour. That is what makes the token layer responsible
 * for colour: Compose's `Icon` applies `ColorFilter.tint` over the whole painter, so a drawable that
 * mixed strokes with fills, or declared two colours, would still render in one tint — and would have
 * lost the distinction it was drawn with rather than kept it.
 */
class BrowserIconContractTest {

    @Test
    fun `the icon set is exactly the one Direction A draws`() {
        assertEquals(EXPECTED, iconNames())
    }

    @Test
    fun `the set stays inside its budget`() {
        // Separate from the assertion above on purpose. That one pins the current set; this one is
        // the rule a later ticket has to reckon with, and it should be the thing that fails first
        // when an icon is added casually.
        //
        // Counted by distinct name rather than by file: a `drawable-night` variant of an icon that
        // already exists is one icon drawn twice, and the budget is about how many icons someone has
        // to draw and check at 200% font scale, not about file count.
        assertTrue("the browser icon budget is $BUDGET; found ${iconNames().size}", iconNames().size <= BUDGET)
    }

    @Test
    fun `SiteSkin icon selection cannot dynamically address resources or glyphs`() {
        val source = siteSkinChromeSource().readText()

        assertTrue("SiteSkin icons must use a closed resource mapping", "siteSkinIconResource" in source)
        FORBIDDEN_ICON_LOOKUPS.forEach { forbidden ->
            assertTrue("SiteSkin icon selection contains forbidden '$forbidden'", forbidden !in source)
        }
        assertTrue("prototype SiteSkin glyphs remain in the renderer", PROTOTYPE_GLYPHS.none { it in source })
    }

    @Test
    fun `every icon is drawn at the viewport the direction used`() {
        val offenders = drawables().filterNot { icon ->
            val text = icon.readText()
            VIEWPORT.all { text.contains(it) }
        }

        assertTrue(
            "an icon drawn at another viewport does not sit on the same optical grid as the rest, " +
                "and no call site can correct for it: ${offenders.map(File::getName)}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every icon is stroked at the weight the direction specifies`() {
        val offenders = drawables().filterNot { it.readText().contains(STROKE_WIDTH) }

        assertTrue("icons not stroked at 1.9: ${offenders.map(File::getName)}", offenders.isEmpty())
    }

    @Test
    fun `no icon carries a fill`() {
        // A filled path and a stroked path tint to the same colour, so a mixed icon loses the
        // distinction it was drawn with. It would look right in review and wrong on the screen.
        val offenders = drawables().filter { it.readText().contains(FILL_ATTRIBUTE) }

        assertTrue("icons mixing fill with stroke: ${offenders.map(File::getName)}", offenders.isEmpty())
    }

    @Test
    fun `every icon declares exactly one colour`() {
        val offenders = drawables().mapNotNull { icon ->
            val colors = COLOR_VALUE.findAll(icon.readText()).map { it.value }.toSet()
            if (colors.size == 1) null else "${icon.name} declares $colors"
        }

        assertTrue(
            "an icon with two colours cannot survive a single tint:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every icon has some geometry`() {
        // The cheapest way to have a green scan and an invisible icon.
        val offenders = drawables().filterNot { it.readText().contains(PATH_DATA) }

        assertTrue("icons with no path data: ${offenders.map(File::getName)}", offenders.isEmpty())
    }

    private companion object {
        const val RESOURCE_ROOT_PROPERTY = "webora.app.res"
        /**
         * Raised 18 → 20 by `UX-021`, deliberately, with `UX-005` as precedent.
         *
         * The two shields are the cost of a decision recorded in that ticket's plan: `ic_lock` and
         * `ic_warning` keep their meaning in regular chrome, where the glyph supports the words
         * `Secure · example.com`, while the integrated header's mark stands beside a
         * manifest-supplied title and has to read as the browser's own. Reusing the lock would have
         * been free and would have made one glyph do two jobs on two surfaces with different
         * amounts of supporting text.
         *
         * A raise is a decision someone makes on purpose. The alternative — quietly bumping this
         * number whenever a surface wants a new picture — is what the budget exists to prevent.
         */
        const val BUDGET = 20
        const val STROKE_WIDTH = """android:strokeWidth="1.9""""
        const val FILL_ATTRIBUTE = "android:fillColor"
        const val PATH_DATA = "android:pathData"
        const val DRAWABLE = "drawable"

        val VIEWPORT = listOf(
            """android:width="24dp"""",
            """android:height="24dp"""",
            """android:viewportWidth="24"""",
            """android:viewportHeight="24"""",
        )

        val COLOR_VALUE = Regex("""#[0-9A-Fa-f]{6,8}""")

        val EXPECTED = setOf(
            "ic_back",
            "ic_forward",
            "ic_reload",
            "ic_home",
            "ic_tabs",
            "ic_menu",
            "ic_more",
            "ic_lock",
            "ic_close",
            "ic_search",
            "ic_warning",
            "ic_siteskin_catalog",
            "ic_siteskin_flower",
            "ic_siteskin_shopping_cart",
            "ic_siteskin_person",
            "ic_siteskin_call",
            "ic_siteskin_share",
            "ic_siteskin_generic",
            "ic_shield_secure",
            "ic_shield_unverified",
        )

        val FORBIDDEN_ICON_LOOKUPS = listOf("getIdentifier(", "Uri.parse(", "File(", "URL(")
        val PROTOTYPE_GLYPHS = listOf("⌂", "▦", "▣", "●", "☎", "•")

        /** Distinct icons, so one icon drawn for two configurations still counts once. */
        fun iconNames(): Set<String> = drawables().map { it.nameWithoutExtension }.toSet()

        fun siteSkinChromeSource(): File {
            val resources = File(requireNotNull(System.getProperty(RESOURCE_ROOT_PROPERTY)))
            val mainSource = requireNotNull(resources.parentFile) { "resource root has no parent: $resources" }
            return mainSource.resolve("java/app/webora/browser/siteskin/SiteSkinChrome.kt")
        }

        /**
         * Every drawable directory, not just the unqualified one.
         *
         * `res/drawable-night/` and `res/drawable-v24/` are ordinary things to add and are drawable
         * directories in every sense Android cares about. Scanning only `res/drawable` would let a
         * qualifier sidestep both the budget and the geometry contract without anyone intending to.
         */
        fun drawables(): List<File> {
            val root = requireNotNull(System.getProperty(RESOURCE_ROOT_PROPERTY)) {
                "$RESOURCE_ROOT_PROPERTY is unset; app/build.gradle.kts must pass the app resource root"
            }
            val directories = File(root).listFiles().orEmpty()
                .filter { it.isDirectory && (it.name == DRAWABLE || it.name.startsWith("$DRAWABLE-")) }
            require(directories.isNotEmpty()) { "no drawable directory under $root" }
            return directories
                .flatMap { it.listFiles().orEmpty().asIterable() }
                .filter { it.extension == "xml" }
                .sortedBy(File::getName)
        }
    }
}
