package app.webora.browser.siteskin

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveSiteSkinChromeContractTest {
    @Test fun `the resolved curve is bounded and non-degenerate at compact and broad widths`() {
        listOf(320f, 720f).forEach { width ->
            val size = Size(width, 120f)
            val curve = expressiveCurve(size, requestedDepth = 20f)
            val outline = ExpressiveHeaderShape.createOutline(size, LayoutDirection.Ltr, Density(1f))

            assertTrue("shape must resolve to a generic curved path", outline is Outline.Generic)
            assertTrue("curve must retain substantial content height at $width: $curve", curve.shoulderY >= 96f)
            assertTrue("curve control must stay centered at $width: $curve", curve.controlX == width / 2f)
            assertTrue("curve must extend below its shoulders at $width: $curve", curve.controlY > size.height)
        }
    }

    @Test fun `primitive source keeps remote policy and global insets outside`() {
        val production = source("app/webora/browser/siteskin/ExpressiveSiteSkinChrome.kt").readText()

        assertTrue(
            "production primitive boundary violations: ${violations(production)}",
            violations(production).isEmpty(),
        )
        assertFalse("negative control must reject configuration input", violations(UNSAFE_SOURCE).isEmpty())
    }

    @Test fun `presentation source is the only trusted configuration projection`() {
        val primitive = source("app/webora/browser/siteskin/ExpressiveSiteSkinChrome.kt").readText()
        val projection = source("app/webora/browser/siteskin/ExpressiveSiteSkinPresentation.kt").readText()

        assertFalse(primitive.contains("SiteSkinConfiguration"))
        assertTrue(projection.contains("configuration: SiteSkinConfiguration"))
        assertTrue(projection.contains("SiteSkinTheme.from(configuration).scheme(darkTheme)"))
    }

    @Test fun `negative geometry control detects a flattened edge`() {
        val curve = expressiveCurve(Size(320f, 120f), requestedDepth = 0f)

        assertFalse("a zero-depth edge must not satisfy the curve contract", curve.controlY > 120f)
    }

    private fun violations(source: String): List<String> = FORBIDDEN.filter(source::contains)

    private fun source(relative: String): File = File(
        requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY))
            .split(File.pathSeparator)
            .map(::File)
            .single { it.invariantSeparatorsPath.endsWith("/src/main/java") },
        relative,
    )

    private companion object {
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
        val FORBIDDEN = listOf(
            "SiteSkinConfiguration",
            "WindowInsets.safeDrawing",
            "MaterialTheme.colorScheme",
            "AnimationSpec",
            "durationMillis:",
            "shape: Shape",
        )
        const val UNSAFE_SOURCE =
            "fun Unsafe(configuration: SiteSkinConfiguration, shape: Shape, durationMillis: Int) { " +
                "WindowInsets.safeDrawing; MaterialTheme.colorScheme }"
    }
}
