package app.webora.browser.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPreferenceTest {
    @Test fun `positive finite animator scale keeps standard motion`() {
        assertFalse(reducedMotionEnabled(1f))
        assertFalse(reducedMotionEnabled(0.5f))
    }

    @Test fun `disabled invalid or unavailable animator scale fails closed`() {
        assertTrue(reducedMotionEnabled(0f))
        assertTrue(reducedMotionEnabled(-1f))
        assertTrue(reducedMotionEnabled(Float.NaN))
        assertTrue(reducedMotionEnabled(Float.POSITIVE_INFINITY))
        assertTrue(reducedMotionEnabled(null))
    }

    @Test fun `negative control rejects permissive unavailable preference`() {
        val unsafePolicy: (Float?) -> Boolean = { scale -> scale != null && scale <= 0f }

        assertFalse("unsafe fixture must demonstrate the missing-value gap", unsafePolicy(null))
        assertTrue("production must reduce motion when platform state is unavailable", reducedMotionEnabled(null))
    }
}
