package app.webora.browser.siteskin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
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
    }
}
