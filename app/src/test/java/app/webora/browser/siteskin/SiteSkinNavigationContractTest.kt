package app.webora.browser.siteskin

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinNavigationContractTest {
    @Test
    fun `integrated back remains browser owned and cannot be suppressed by manifest styling`() {
        val source = source("app/webora/browser/siteskin/SiteSkinTopBar.kt").readText()

        assertTrue("integrated Back must use the browser-owned contract", browserOwnedBack(source))
        assertFalse(
            "negative control: a SiteSkin-coloured Back surface must be rejected",
            browserOwnedBack(
                "private fun BrowserBack() { " +
                    "WeboraIconButton(R.drawable.ic_back, stringResource(R.string.back), {}) " +
                    ".background(colors.background).testTag(SITESKIN_BACK_TAG) }",
            ),
        )
    }

    private fun browserOwnedBack(source: String): Boolean {
        val start = source.indexOf("private fun BrowserBack(")
        val end = source.indexOf("private fun BrandLogo(", start)
        if (start < 0 || end < 0) return false
        val back = source.substring(start, end)
        return back.contains("WeboraIconButton(") &&
            back.contains("R.drawable.ic_back") &&
            back.contains("stringResource(R.string.back)") &&
            back.contains("MaterialTheme.colorScheme.surfaceContainer") &&
            back.contains("testTag(SITESKIN_BACK_TAG)") &&
            !back.contains("colors.")
    }

    private fun source(relative: String): File = File(
        requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY))
            .split(File.pathSeparator)
            .map(::File)
            .single { it.invariantSeparatorsPath.endsWith("/src/main/java") },
        relative,
    )

    private companion object {
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
    }
}
