package app.webora.browser.browser

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import dev.siteskin.core.origin.SiteOrigin
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAccessibilityTest {

    @Test
    fun `the browser minimum meets platform guidance`() {
        assertTrue(
            "WCAG 2.2 target size (minimum) and the Material accessibility guidance both ask for " +
                "48 dp; found $MINIMUM_TOUCH_TARGET",
            MINIMUM_TOUCH_TARGET >= PLATFORM_MINIMUM,
        )
    }

    @Test
    fun `the browser minimum actually raises the Material default`() {
        // If this ever stops being true the wrapper is decoration: Material would already be
        // supplying the target and every call site could go back to using Button directly.
        assertTrue(
            "expected the wrapper to raise Material's ${ButtonDefaults.MinHeight} button minimum",
            MINIMUM_TOUCH_TARGET > ButtonDefaults.MinHeight,
        )
    }

    @Test
    fun `an icon-only control cannot be nameless`() {
        // C4 makes a hard-coded accessible name a build failure, so the remaining way to ship a
        // nameless icon button is to make the name optional or nullable at the wrapper. There is no
        // composition here to inspect — no Robolectric — so the guarantee is read off the
        // declaration, which is where it lives.
        val declaration = wrapperSource().readText()
            .substringAfter(ICON_BUTTON_DECLARATION)
            .substringBefore(')')

        assertTrue(
            "WeboraIconButton must take a non-optional, non-nullable accessible name; a glyph " +
                "contributes nothing to the semantics tree a screen reader actually reads:\n" +
                declaration,
            declaration.contains(REQUIRED_NAME),
        )
    }

    @Test
    fun `home announces nothing`() {
        assertNull(browserAnnouncement(BrowserState()))
    }

    @Test
    fun `a committed page with no failure announces that it loaded`() {
        assertEquals(BrowserAnnouncement.LOADED, browserAnnouncement(page()))
    }

    @Test
    fun `loading announces progress`() {
        assertEquals(BrowserAnnouncement.LOADING, browserAnnouncement(page().copy(isLoading = true)))
    }

    @Test
    fun `failure outranks loading`() {
        // A failed load can arrive while isLoading is still set. Announcing progress there would
        // tell the user the opposite of what happened.
        val failed = page().copy(isLoading = true, loadFailure = FAILURE)

        assertEquals(BrowserAnnouncement.FAILED, browserAnnouncement(failed))
    }

    @Test
    fun `a browser with no committed page announces nothing`() {
        // An empty announcement is not silence: assistive technology presents it as a current
        // claim about a page that does not exist.
        assertNull(browserAnnouncement(BrowserState(mode = BrowserMode.Regular(null))))
    }

    @Test
    fun `only failure interrupts`() {
        assertEquals(LiveRegionMode.Assertive, BrowserAnnouncement.FAILED.liveRegionMode())
        assertEquals(LiveRegionMode.Polite, BrowserAnnouncement.LOADING.liveRegionMode())
        assertEquals(LiveRegionMode.Polite, BrowserAnnouncement.LOADED.liveRegionMode())
    }

    private fun page() = BrowserState(
        mode = BrowserMode.Regular(SiteOrigin.parse(PAGE_URL)),
        displayedUrl = PAGE_URL,
    )

    private companion object {
        val PLATFORM_MINIMUM = 48.dp
        const val PAGE_URL = "https://example.test/page"
        val FAILURE = BrowserLoadFailure(LoadErrorKind.NETWORK, "example.test", PAGE_URL)

        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
        const val WRAPPER_PATH = "app/webora/browser/browser/BrowserAccessibility.kt"
        const val ICON_BUTTON_DECLARATION = "fun WeboraIconButton("
        const val REQUIRED_NAME = "contentDescription: String,"

        /** The one file `RAW_BUTTON_IMPORT` lets a Material button be reached from. */
        fun wrapperSource(): File =
            requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY)) {
                "$SOURCE_ROOT_PROPERTY is unset; app/build.gradle.kts must pass the app source roots"
            }
                .split(File.pathSeparator)
                .map { File(it, WRAPPER_PATH) }
                .firstOrNull(File::isFile)
                ?: error("no $WRAPPER_PATH under any scanned source root")
    }
}
