package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.nav.NavMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The dock hides drawer rows; it never removes items from the model.
 *
 * Criterion 11 is a *presentation* rule and criterion 12 depends on it staying one. If projection
 * filtered `SiteSkinChromeModel`, `NavMatcher` would stop resolving the active route for a projected
 * item, and an item that failed to project would be absent from both surfaces — the one outcome the
 * issue names as unacceptable.
 */
class DockDrawerDeduplicationTest {
    @Test
    fun `the model keeps every item a projection hides`() {
        val configuration = configuration(BLOOM)
        val model = SiteSkinChromeModel.from(configuration, "$ORIGIN/catalog")
        val arrangement = dockArrangement(model, listOf("catalog", "cart", "profile"))

        assertEquals(setOf("catalog", "cart", "profile"), arrangement.projectedIds)
        assertEquals(
            "the chrome model is untouched by projection",
            listOf("home", "catalog", "cart", "profile"),
            model.bottomNavigation.map { it.id },
        )
    }

    /**
     * A projected item still resolves the active route.
     *
     * This is the assertion that would fail first if de-duplication were implemented by filtering
     * the model: `/catalog` selects `catalog`, and `catalog` is exactly what the dock is showing.
     */
    @Test
    fun `route matching still sees a projected item`() {
        val configuration = configuration(BLOOM)
        val model = SiteSkinChromeModel.from(configuration, "$ORIGIN/catalog/roses")

        assertEquals("catalog", model.bottomNavigation.single { it.isActive }.id)
        assertEquals(
            "catalog",
            NavMatcher.activeItem(configuration.bottomNavigation.orEmpty(), "$ORIGIN/catalog/roses")?.id,
        )
    }

    /** An id that failed to project is not in `projectedIds`, so the drawer still lists it. */
    @Test
    fun `an unprojected item is never hidden`() {
        val model = SiteSkinChromeModel.from(configuration(BLOOM), "$ORIGIN/")
        val arrangement = dockArrangement(model, listOf("catalog", "ghost"))

        assertFalse("ghost projected nothing", "ghost" in arrangement.projectedIds)
        assertFalse("home was never nominated", "home" in arrangement.projectedIds)
        assertEquals(setOf("catalog"), arrangement.projectedIds)
    }

    /**
     * The drawer subtracts, and the subtraction reads the rendered ids rather than the requested
     * ones.
     *
     * Written as a source contract because the filtering happens inside a composable. Its negative
     * control is re-pointing the subtraction at the configuration's requested `dock` list, which
     * would hide an id that never projected — criterion 12's exact failure.
     */
    @Test
    fun `the drawer hides only what the dock rendered`() {
        val source = executableLines(File("src/main/java/app/webora/browser/siteskin/SiteSkinHubDrawer.kt"))

        // Anchored on the call name alone. An earlier version required `HubGroup(R.string`, which a
        // line-wrap for detekt's line-length rule split in two — the anchor broke for a reason with
        // nothing to do with the rule it guards.
        assertTrue("the scan must see real code", "HubGroup(" in source)
        assertEquals("all three groups subtract", 3, source.split("- projectedIds").size - 1)
        assertFalse(
            "the drawer must not consult what was requested, only what was rendered",
            "presentation" in source || "configuration" in source,
        )
    }

    private fun configuration(manifest: String) =
        SiteSkinValidator.validate(manifest.byteInputStream(), ORIGIN)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private companion object {
        const val ORIGIN = "https://bloom.example"

        fun executableLines(file: File): String {
            check(file.exists()) { "source not found at ${file.absolutePath}" }
            return file.readLines()
                .filterNot { line ->
                    val trimmed = line.trimStart()
                    trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
                }
                .joinToString("\n")
        }

        val BLOOM = """
            {"schemaVersion":"1.0","site":{"id":"bloom","name":"Bloom","homeUrl":"/"},
             "presentation":{"hub":"drawer","dock":["catalog","cart","profile"]},
             "bottomNavigation":[
               {"id":"home","label":"Home","icon":"home","action":{"type":"internal_url","url":"/"},
                "match":["/"]},
               {"id":"catalog","label":"Catalog","icon":"catalog",
                "action":{"type":"internal_url","url":"/catalog"},"match":["/catalog","/catalog/**"]},
               {"id":"cart","label":"Cart","icon":"shopping_cart",
                "action":{"type":"internal_url","url":"/cart"},"match":["/cart/**"]},
               {"id":"profile","label":"Account","icon":"person",
                "action":{"type":"internal_url","url":"/account"},"match":["/account/**"]}]}
        """.trimIndent()
    }
}
