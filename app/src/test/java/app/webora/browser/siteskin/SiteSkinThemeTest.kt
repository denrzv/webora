package app.webora.browser.siteskin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinThemeTest {
    @Test fun `trusted branding maps to the closed light scheme`() {
        val theme = SiteSkinTheme.from(configuration("#D94F8A", "#FADADD", "#FFF7FA", "#2B1B24"))

        assertEquals("#D94F8A", theme.light.primary.hex())
        assertEquals("#FADADD", theme.light.secondary.hex())
        assertEquals("#FFF7FA", theme.light.background.hex())
        assertEquals("#2B1B24", theme.light.onBackground.hex())
        assertEquals(theme.light.onBackground, theme.light.onPrimary)
        assertEquals(theme.light.onBackground, theme.light.onSecondary)
    }

    @Test fun `missing and partial branding use deterministic complete defaults`() {
        val absent = SiteSkinTheme.from(configuration())
        val repeated = SiteSkinTheme.from(configuration())
        val partial = SiteSkinTheme.from(configuration(primary = "#D94F8A"))

        assertEquals(absent, repeated)
        assertEquals("#4C5EC2", absent.light.primary.hex())
        assertEquals("#5C6BC0", absent.light.secondary.hex())
        assertEquals("#FFFFFF", absent.light.background.hex())
        assertEquals("#1B1B1F", absent.light.onBackground.hex())
        assertEquals("#D94F8A", partial.light.primary.hex())
        assertEquals(absent.light.background, partial.light.background)
    }

    @Test fun `dark mode is branded deterministic and independently guarded`() {
        val first = SiteSkinTheme.from(configuration("#FFFFFF", "#FAFAFA", "#FFF7FA", "#000000"))
        val second = SiteSkinTheme.from(configuration("#FFFFFF", "#FAFAFA", "#FFF7FA", "#000000"))

        assertEquals(first.dark, second.dark)
        assertEquals("#FFFFFF", first.dark.onBackground.hex())
        assertTrue(first.dark.background != first.light.background)
        assertPairRatios(first.dark)
    }

    @Test fun `matching manifest text and background are AA before exposure`() {
        val theme = SiteSkinTheme.from(configuration(background = "#777777", text = "#777777"))

        assertTrue(contrastRatio(theme.light.background, theme.light.onBackground) >= BODY_RATIO)
        assertTrue(contrastRatio(theme.dark.background, theme.dark.onBackground) >= BODY_RATIO)
    }

    @Test fun `every exposed foreground container pair meets its threshold`() {
        val themes = listOf(
            SiteSkinTheme.from(configuration()),
            SiteSkinTheme.from(configuration("#FFFFFF", "#000000", "#808080", "#777777")),
            SiteSkinTheme.from(configuration("#123456", "#ABCDEF", "#F0F0F0", "#101010")),
        )

        themes.flatMap { listOf(it.light, it.dark) }.forEach(::assertPairRatios)
    }

    @Test fun `the system theme selects the projection and nothing else does`() {
        val theme = SiteSkinTheme.from(configuration("#D94F8A", "#FADADD", "#FFF7FA", "#2B1B24"))

        assertEquals(theme.light, theme.scheme(darkTheme = false))
        assertEquals(theme.dark, theme.scheme(darkTheme = true))
    }

    @Test fun `adversarial colours meet contrast in both projections`() {
        // Three handpicked themes prove the guard runs; they do not prove it holds. This corpus is
        // the known failure modes plus a seeded sweep, and it is seeded rather than random so a
        // failure is reproducible from the message alone instead of vanishing on the next run.
        val failures = adversarialCases().mapNotNull { (name, theme) ->
            val offending = listOf(theme.light to LIGHT, theme.dark to DARK).mapNotNull { (scheme, label) ->
                scheme.shortfall()?.let { "$label $it" }
            }
            if (offending.isEmpty()) null else "$name -> ${offending.joinToString("; ")}"
        }

        assertTrue("contrast shortfalls:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun adversarialCases(): List<Pair<String, SiteSkinTheme>> {
        val curated = listOf(
            // Text identical to its background, and one step away from it.
            listOf("#000000", "#000000", "#000000", "#000000"),
            listOf("#FFFFFF", "#FFFFFF", "#FFFFFF", "#FFFFFF"),
            listOf("#010101", "#010101", "#000000", "#000000"),
            // Mid grey against mid grey: the worst case for a guard that walks toward one extreme,
            // because neither direction is obviously the right one.
            listOf("#808080", "#808080", "#808080", "#808080"),
            listOf("#767676", "#777777", "#787878", "#797979"),
            // Saturated channels, where perceived luminance is dominated by one weight.
            listOf("#00FF00", "#00FF00", "#00FF00", "#00FF00"),
            listOf("#0000FF", "#0000FF", "#0000FF", "#0000FF"),
            listOf("#FF0000", "#FF0000", "#FF0000", "#FF0000"),
            // Extremes crossed over: the darkest possible text on the lightest possible surface
            // and the reverse, which must both survive the dark derivation.
            listOf("#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000"),
            listOf("#000000", "#000000", "#000000", "#FFFFFF"),
        )
        val random = Random(SWEEP_SEED)
        val swept = List(SWEEP_CASES) { List(COLOR_FIELDS) { random.nextHex() } }
        return (curated + swept).map { colors ->
            colors.joinToString() to SiteSkinTheme.from(configuration(colors[0], colors[1], colors[2], colors[3]))
        }
    }

    private fun Random.nextHex(): String = "#%06X".format(nextInt(CHANNEL_SPACE))

    /** The first pair that misses its threshold, or `null` when every pair clears it. */
    private fun SiteSkinColorScheme.shortfall(): String? = listOf(
        Triple("primary", contrastRatio(primary, onPrimary), UI_RATIO),
        Triple("secondary", contrastRatio(secondary, onSecondary), UI_RATIO),
        Triple("background", contrastRatio(background, onBackground), BODY_RATIO),
    ).firstOrNull { (_, ratio, target) -> ratio < target }
        ?.let { (role, ratio, target) -> "$role %.2f < %.1f".format(ratio, target) }

    private fun assertPairRatios(scheme: SiteSkinColorScheme) {
        assertTrue(contrastRatio(scheme.primary, scheme.onPrimary) >= UI_RATIO)
        assertTrue(contrastRatio(scheme.secondary, scheme.onSecondary) >= UI_RATIO)
        assertTrue(contrastRatio(scheme.background, scheme.onBackground) >= BODY_RATIO)
    }

    private fun configuration(
        primary: String? = null,
        secondary: String? = null,
        background: String? = null,
        text: String? = null,
    ) = SiteSkinValidator.validate(
        manifest(primary, secondary, background, text).byteInputStream(),
        "https://brand.example",
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private fun manifest(vararg colors: String?): String {
        val names = listOf("primaryColor", "secondaryColor", "backgroundColor", "textColor")
        val fields = names.zip(colors).mapNotNull { (name, value) -> value?.let { "\"$name\":\"$it\"" } }
        val branding = if (fields.isEmpty()) "" else ",\"branding\":{${fields.joinToString()}}"
        return """{"schemaVersion":"1.0","site":{"id":"brand","name":"Brand"}$branding}"""
    }

    private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

    private companion object {
        const val BODY_RATIO = 4.5
        const val UI_RATIO = 3.0
        const val LIGHT = "light"
        const val DARK = "dark"
        const val SWEEP_SEED = 20260810L
        const val SWEEP_CASES = 120
        const val COLOR_FIELDS = 4
        const val CHANNEL_SPACE = 0x1000000
    }
}
