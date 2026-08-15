package app.webora.browser.siteskin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveSiteSkinPresentationTest {
    @Test fun `trusted branding selects the existing guarded light and dark projections`() {
        val configuration = configuration()

        val light = ExpressiveSiteSkinPresentation.from(configuration, darkTheme = false, reducedMotion = false)
        val dark = ExpressiveSiteSkinPresentation.from(configuration, darkTheme = true, reducedMotion = false)

        assertEquals("#D94F8A", light.colors.primary.hex())
        assertEquals("#FFF7FA", light.colors.background.hex())
        assertEquals(SiteSkinTheme.from(configuration).dark, dark.colors)
        assertTrue(light.colors != dark.colors)
    }

    @Test fun `missing branding is total and deterministic`() {
        val configuration = configuration(branding = "")

        val first = ExpressiveSiteSkinPresentation.from(configuration, darkTheme = false, reducedMotion = false)
        val second = ExpressiveSiteSkinPresentation.from(configuration, darkTheme = false, reducedMotion = false)

        assertEquals(first, second)
        assertEquals(SiteSkinTheme.from(configuration).light, first.colors)
    }

    @Test fun `reduced motion is a closed browser owned choice`() {
        val configuration = configuration()

        assertEquals(
            ExpressiveMotionPolicy.STANDARD,
            ExpressiveSiteSkinPresentation.from(configuration, false, reducedMotion = false).motion,
        )
        assertEquals(
            ExpressiveMotionPolicy.REDUCED,
            ExpressiveSiteSkinPresentation.from(configuration, false, reducedMotion = true).motion,
        )
        assertEquals(setOf("STANDARD", "REDUCED"), ExpressiveMotionPolicy.entries.map { it.name }.toSet())
    }

    @Test fun `presentation cannot carry remote layout or native capability data`() {
        val unsafe = forbiddenCapabilities(UnsafeProjection::class.java)
        val production = forbiddenCapabilities(ExpressiveSiteSkinPresentation::class.java)

        assertTrue("negative control must detect remote URI and raw CSS: $unsafe", unsafe.size >= 2)
        assertTrue("production projection exposes forbidden capabilities: $production", production.isEmpty())
    }

    private fun forbiddenCapabilities(type: Class<*>): List<String> = type.declaredFields.mapNotNull { field ->
        val forbidden = field.type == URI::class.java ||
            field.type == String::class.java ||
            Map::class.java.isAssignableFrom(field.type) ||
            Function::class.java.isAssignableFrom(field.type)
        field.name.takeIf { forbidden }
    }

    private fun configuration(branding: String = BRANDING) = SiteSkinValidator.validate(
        """{"schemaVersion":"1.0","site":{"id":"brand","name":"Brand"}$branding}""".byteInputStream(),
        "https://brand.example",
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

    @Suppress("unused")
    private class UnsafeProjection(val remoteResource: URI, val rawCss: String)

    private companion object {
        const val BRANDING =
            ",\"branding\":{\"primaryColor\":\"#D94F8A\",\"secondaryColor\":\"#FADADD\"," +
                "\"backgroundColor\":\"#FFF7FA\",\"textColor\":\"#2B1B24\"}"
    }
}
