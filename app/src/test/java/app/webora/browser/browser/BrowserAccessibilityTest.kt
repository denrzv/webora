package app.webora.browser.browser

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.dp
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

    private companion object {
        val PLATFORM_MINIMUM = 48.dp
    }
}
