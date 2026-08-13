package app.webora.browser.browser

import app.webora.browser.design.WeboraChrome
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserChromeContractTest {

    @Test
    fun `direction A chrome geometry is named without a touch target alias`() {
        assertTrue(WeboraChrome.ADDRESS_HEIGHT.value == 52f)
        assertTrue(WeboraChrome.DOCK_HEIGHT.value == 60f)
        assertTrue(WeboraChrome.SLOT_SIZE.value == 40f)
        assertTrue(WeboraChrome.ICON_SIZE.value == 20f)

        val dimensions = source("app/webora/browser/design/WeboraDimensions.kt").readText()
        assertFalse(
            "chrome geometry must not duplicate the accessibility layer's touch-target owner",
            Regex("val\\s+\\w*TOUCH_TARGET", RegexOption.IGNORE_CASE).containsMatchIn(dimensions),
        )
    }

    @Test
    fun `identity and editable address are structurally separate`() {
        val source = source("app/webora/browser/browser/BrowserChrome.kt").readText()

        assertTrue("current browser chrome must keep a separate identity", hasSeparateIdentity(source))
        assertFalse(
            "negative control: an address field used as identity must be rejected",
            hasSeparateIdentity(
                "BasicTextField(value = state.addressText, " +
                    "modifier = Modifier.testTag(BROWSER_SECURITY_TAG))",
            ),
        )
    }

    @Test
    fun `browser navigation cannot become page-render evidence`() {
        val source = source("app/webora/browser/browser/BrowserScreen.kt").readText()

        assertTrue("page must sit between top chrome and the regular dock", chromeFramesPage(source))
        assertFalse(
            "negative control: chrome nested after the page tag must be rejected",
            chromeFramesPage(
                "Box(Modifier.testTag(BROWSER_CONTENT_TAG)) { BrowserNavigationDock() }",
            ),
        )
        assertFalse(
            "negative control: a top dock must be rejected",
            chromeFramesPage(
                "BrowserChrome(); BrowserNavigationDock(); " +
                    "Box(Modifier.testTag(BROWSER_CONTENT_TAG))",
            ),
        )
    }

    private fun hasSeparateIdentity(source: String): Boolean =
        source.contains("BasicTextField(") &&
            source.contains("BrowserSecurityIdentity(") &&
            !Regex("BasicTextField\\([\\s\\S]{0,500}BROWSER_SECURITY_TAG").containsMatchIn(source)

    private fun chromeFramesPage(source: String): Boolean {
        val chrome = source.indexOf("BrowserChrome(")
        val page = source.indexOf("testTag(BROWSER_CONTENT_TAG)")
        val dock = source.indexOf("BrowserNavigationDock(", page)
        return chrome >= 0 && page > chrome && dock > page
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
