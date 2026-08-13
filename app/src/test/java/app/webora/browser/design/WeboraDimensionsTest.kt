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
    fun `the geometry layer declares no touch target of its own`() {
        // `MINIMUM_TOUCH_TARGET` has one name and one owner. A second one here would sit exactly
        // where somebody would reach to shrink a target so a design fits, which is the change
        // `A11Y-001` exists to make impossible to do quietly — so the assertion is against the
        // numbers this layer *does* declare, not against a re-export it deliberately lacks.
        val chrome = listOf(
            WeboraChrome.ADDRESS_HEIGHT,
            WeboraChrome.DOCK_HEIGHT,
            WeboraChrome.SLOT_SIZE,
            WeboraChrome.ICON_SIZE,
        )
        val declared = WeboraSpacing.ALL + WeboraRadius.ALL + chrome

        assertTrue(
            "a 48 dp geometry token would be a second name for the accessibility minimum",
            declared.none { it == MINIMUM_TOUCH_TARGET },
        )
    }

    @Test
    fun `a control's visual slot fits inside the target it must present`() {
        // The dock draws 40 dp circles. The control is 48 dp; the circle is what you see. If the
        // drawn slot ever exceeded the target, the visible affordance would be making a promise the
        // touchable area does not keep.
        assertTrue(
            "the drawn slot must not exceed the touch target around it",
            WeboraChrome.SLOT_SIZE <= MINIMUM_TOUCH_TARGET,
        )
        assertTrue("the glyph must sit inside its slot", WeboraChrome.ICON_SIZE < WeboraChrome.SLOT_SIZE)
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
