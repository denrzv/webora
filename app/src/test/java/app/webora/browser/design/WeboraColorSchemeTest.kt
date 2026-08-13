package app.webora.browser.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The browser palette, measured.
 *
 * `SiteSkinTheme` guards a website's colours at runtime because they arrive from the network and can
 * fail. Webora's own colours cannot change after compilation, so the guard runs here instead — over
 * every pair that exists, once, before the code can be committed. That is what `C5` asked for when
 * it said the browser palette should be asserted "in a JVM test", and it is stronger than a runtime
 * correction, which would let a failing token ship silently repaired with nothing red anywhere.
 *
 * The table below is hand-written and the completeness assertion is not.
 */
class WeboraColorSchemeTest {

    @Test
    fun `every measured pair meets its WCAG threshold in both projections`() {
        val shortfalls = PROJECTIONS.flatMap { (label, scheme) ->
            MEASURED.mapNotNull { pair ->
                val ratio = contrastRatio(scheme.role(pair.foreground), scheme.role(pair.background))
                if (ratio >= pair.target) {
                    null
                } else {
                    "%s: %s on %s is %.2f, below %.1f".format(
                        label, pair.foreground, pair.background, ratio, pair.target,
                    )
                }
            }
        }

        assertTrue("contrast shortfalls:\n${shortfalls.joinToString("\n")}", shortfalls.isEmpty())
    }

    @Test
    fun `every declared role is measured or explicitly exempt`() {
        // Both directions, so neither a role added without a pair nor a table entry naming a role
        // that no longer exists can pass. Without the first this assertion decays the moment the
        // palette grows; without the second the table rots into fiction.
        val declared = WeboraColors.LIGHT.colorRoles().keys
        val accounted = MEASURED.flatMap { listOf(it.foreground, it.background) }.toSet() + DECORATIVE.keys

        assertEquals(
            "every colour role must be measured against something, or named as decorative with a " +
                "reason; an unaccounted role is one nothing checks",
            declared,
            accounted,
        )
    }

    @Test
    fun `every decorative exemption carries a reason`() {
        // The exemption list is the escape hatch from the assertion above. A blank reason would make
        // it a silent one, which is the shape this repository does not keep.
        val unexplained = DECORATIVE.filterValues { it.isBlank() }.keys

        assertTrue("decorative exemptions with no stated reason: $unexplained", unexplained.isEmpty())
    }

    @Test
    fun `the figures ADR-013 published come out of the code`() {
        // The ADR measured these when it chose Direction A over B, and they are what a reader would
        // check the palette against. Asserting them means a mistyped hex digit shows up as a changed
        // figure rather than as a plausible colour that still clears its threshold.
        assertEquals(12.44, WeboraColors.LIGHT.ratio("onContainer", "container"), TOLERANCE)
        assertEquals(6.80, WeboraColors.LIGHT.ratio("secure", "container"), TOLERANCE)
        assertEquals(7.65, WeboraColors.LIGHT.ratio("notSecure", "container"), TOLERANCE)
        assertEquals(10.49, WeboraColors.DARK.ratio("onContainer", "container"), TOLERANCE)
        assertEquals(7.94, WeboraColors.DARK.ratio("secure", "container"), TOLERANCE)
        assertEquals(8.50, WeboraColors.DARK.ratio("notSecure", "container"), TOLERANCE)
    }

    @Test
    fun `the identity chip separates from its ground below 3 to 1, and that is the decision`() {
        // Not an oversight awaiting a fix. ADR-013 measured this at 1.27 light / 1.29 dark and ruled
        // on it: the chip is a status display rather than an interactive control, so WCAG 1.4.11
        // does not require its boundary to carry contrast, and its identity is carried by text at
        // 10.49:1 or better plus a glyph and a word. Pinned so that "improving" it is a deliberate
        // reversal of a recorded decision rather than a tidy-up.
        assertEquals(1.27, WeboraColors.LIGHT.ratio("container", "ground"), TOLERANCE)
        assertEquals(1.29, WeboraColors.DARK.ratio("container", "ground"), TOLERANCE)
    }

    @Test
    fun `the system theme selects the projection and nothing else does`() {
        assertEquals(WeboraColors.LIGHT, WeboraColors.scheme(darkTheme = false))
        assertEquals(WeboraColors.DARK, WeboraColors.scheme(darkTheme = true))
    }

    @Test
    fun `the two projections are not the same palette`() {
        // A selector that returns one projection for both answers would satisfy every assertion
        // above if the projections happened to be equal. They are not, and this says so.
        assertTrue(WeboraColors.LIGHT != WeboraColors.DARK)
    }

    private fun WeboraColorScheme.ratio(foreground: String, background: String): Double =
        contrastRatio(role(foreground), role(background))

    private fun WeboraColorScheme.role(name: String): Color =
        requireNotNull(colorRoles()[name]) { "no such colour role: $name" }

    private data class MeasuredPair(val foreground: String, val background: String, val target: Double)

    private companion object {
        /** Restated rather than imported: a test sharing its threshold stops asserting it. */
        const val BODY = 4.5
        const val NON_TEXT = 3.0
        const val TOLERANCE = 0.005

        val PROJECTIONS = listOf("light" to WeboraColors.LIGHT, "dark" to WeboraColors.DARK)

        /**
         * Every foreground/background combination the theme derivation can produce.
         *
         * Body pairs carry text; non-text pairs carry an icon or identify a control's state.
         */
        val MEASURED = listOf(
            MeasuredPair("ink", "ground", BODY),
            MeasuredPair("ink", "surface", BODY),
            MeasuredPair("ink", "chrome", BODY),
            MeasuredPair("ink", "container", BODY),
            MeasuredPair("muted", "ground", BODY),
            MeasuredPair("muted", "surface", BODY),
            MeasuredPair("muted", "chrome", BODY),
            MeasuredPair("muted", "container", BODY),
            MeasuredPair("onChrome", "chrome", BODY),
            MeasuredPair("onContainer", "container", BODY),
            MeasuredPair("onPrimary", "primary", BODY),
            MeasuredPair("secure", "container", BODY),
            MeasuredPair("notSecure", "container", BODY),
            MeasuredPair("primary", "ground", NON_TEXT),
            MeasuredPair("primary", "surface", NON_TEXT),
            MeasuredPair("primary", "chrome", NON_TEXT),
            MeasuredPair("primary", "container", NON_TEXT),
        )

        /**
         * Roles that carry no information, with why.
         *
         * WCAG 1.4.11 governs visual information required to identify a component or its state.
         * Neither of these identifies anything, and both sit behind content that clears its own
         * threshold.
         */
        val DECORATIVE = mapOf(
            "divider" to
                "a hairline separator; it identifies no component or state, and every region it " +
                    "separates carries text measured above",
            "scrim" to
                "an overlay behind a modal; nothing is drawn on it, because the modal's own " +
                    "surface sits on top",
        )
    }
}
