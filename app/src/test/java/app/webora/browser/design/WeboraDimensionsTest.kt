package app.webora.browser.design

import app.webora.browser.browser.MINIMUM_TOUCH_TARGET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry tokens, checked against the claim made for them.
 *
 * `ADR-013` names a 4 dp base, a 20 dp gutter, 20 dp cards and 999 dp pills. The plan records that
 * the intermediate steps are *derived from that base* rather than chosen — a claim which is
 * indistinguishable from taste in the resulting numbers unless something asserts it.
 */
class WeboraDimensionsTest {

    @Test
    fun `every spacing step is a multiple of the stated base`() {
        val offenders = WeboraSpacing.ALL.filterNot { it.value % WeboraSpacing.BASE.value == 0f }

        assertTrue("spacing steps that are not multiples of ${WeboraSpacing.BASE}: $offenders", offenders.isEmpty())
    }

    @Test
    fun `every radius below the pill is a multiple of the same base`() {
        // The pill is deliberately outside the progression: it is not a step on a scale but a value
        // chosen to exceed any height it can be applied to, which is what makes it a pill.
        val steps = WeboraRadius.ALL - WeboraRadius.PILL
        val offenders = steps.filterNot { it.value % WeboraSpacing.BASE.value == 0f }

        assertTrue("radii that are not multiples of ${WeboraSpacing.BASE}: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the gutter ADR-013 names is the gutter`() {
        assertEquals(GUTTER_DP, WeboraSpacing.GUTTER.value, TOLERANCE)
        assertEquals(GUTTER_DP, WeboraRadius.LARGE.value, TOLERANCE)
    }

    @Test
    fun `the chrome heights are the ones the direction was drawn at`() {
        assertEquals(ADDRESS_DP, WeboraChrome.ADDRESS_HEIGHT.value, TOLERANCE)
        assertEquals(DOCK_DP, WeboraChrome.DOCK_HEIGHT.value, TOLERANCE)
        assertEquals(SLOT_DP, WeboraChrome.SLOT_SIZE.value, TOLERANCE)
        assertEquals(ICON_DP, WeboraChrome.ICON_SIZE.value, TOLERANCE)
    }

    @Test
    fun `the touch target is the accessibility constant, not a copy of it`() {
        // Identity rather than equality of value. A second 48 dp literal would satisfy an equality
        // assertion and then drift the first time a design felt cramped — and the geometry layer is
        // precisely where someone would reach to make one fit.
        assertEquals(MINIMUM_TOUCH_TARGET, WeboraChrome.TOUCH_TARGET)
        assertTrue(
            "the slot a control sits in must not be smaller than the target it must present",
            WeboraChrome.TOUCH_TARGET >= WeboraChrome.SLOT_SIZE,
        )
    }

    private companion object {
        const val TOLERANCE = 0.001f
        const val GUTTER_DP = 20f
        const val ADDRESS_DP = 52f
        const val DOCK_DP = 60f
        const val SLOT_DP = 40f
        const val ICON_DP = 20f
    }
}
