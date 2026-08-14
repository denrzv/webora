package app.webora.browser.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "No browser surface reads a Material default", made mechanical.
 *
 * The acceptance criterion is easy to state and hard to keep: `MaterialTheme.colorScheme.surfaceDim`
 * reads the same at a call site whether it was supplied or defaulted, so nothing at the point of use
 * can tell. Rather than trusting that all forty-eight arguments were passed — and re-auditing every
 * time a Compose release adds a role — the assertion is a **closure** property: every value in the
 * derived Material types must be a value the browser layer declares.
 *
 * A role left to a default fails it, because Material's baseline purple is not in Webora's palette.
 * So does a hand-written literal, which is the more likely mistake: a literal is a colour nothing
 * has measured.
 */
class WeboraThemeTest {

    @Test
    fun `every Material colour comes from the browser palette`() {
        val strays = PROJECTIONS.flatMap { (label, scheme) ->
            val declared = scheme.colorRoles().values.toSet()
            materialColorScheme(scheme).colorRoles()
                .filterValues { it !in declared }
                .map { (role, color) -> "$label: $role is $color, which no browser token declares" }
        }

        assertTrue(
            "a Material role holding a colour the browser palette does not declare is either a " +
                "default this ticket exists to remove, or a literal nothing has measured:\n" +
                strays.joinToString("\n"),
            strays.isEmpty(),
        )
    }

    @Test
    fun `every Material foreground meets its background`() {
        // Membership is not pairing. The assertion above would accept `onSurface = divider` — a
        // declared browser colour, and a 1.3:1 hairline under every piece of body text on a surface.
        // Material's own naming supplies what is missing: for each role `onX` there is a role `x`,
        // and the two are a foreground and its background by construction.
        val shortfalls = PROJECTIONS.flatMap { (label, scheme) ->
            val roles = materialColorScheme(scheme).colorRoles()
            roles.namedPairs().mapNotNull { (foreground, background) ->
                val ratio = contrastRatio(requireNotNull(roles[foreground]), requireNotNull(roles[background]))
                if (ratio >= BODY) null else "%s: %s on %s is %.2f".format(label, foreground, background, ratio)
            }
        }

        assertTrue(
            "a Material foreground carries text on its own background, so the pairing matters as " +
                "much as the membership:\n" + shortfalls.joinToString("\n"),
            shortfalls.isEmpty(),
        )
    }

    @Test
    fun `the pairing scan finds the pairs it is meant to`() {
        // A prefix rule that matched nothing would pass the assertion above forever. The floor is
        // the guard; a renamed Material role shows up here rather than as silence.
        val pairs = materialColorScheme(WeboraColors.LIGHT).colorRoles().namedPairs()

        assertTrue("expected Material's on-role pairs; found ${pairs.size}", pairs.size >= ON_PAIRS)
    }

    @Test
    fun `the Material scheme is fully populated`() {
        // The closure assertion above would pass vacuously if reflection found nothing. It would also
        // pass on a scheme with three roles. Material's own surface is what it is; this pins that the
        // derivation covers all of it.
        val roles = materialColorScheme(WeboraColors.LIGHT).colorRoles()

        assertTrue("expected Material's full role set; found ${roles.size}", roles.size >= MATERIAL_ROLES)
    }

    @Test
    fun `every text style is sized from the compiled scale`() {
        val strays = WEBORA_TYPOGRAPHY.textStyles()
            .filterValues { it.fontSize !in WeboraTypeScale.ALL }
            .map { (role, style) -> "$role is ${style.fontSize}" }

        assertTrue(
            "a sixteenth type size means the scale ADR-013 decided is no longer the scale in " +
                "use:\n" + strays.joinToString("\n"),
            strays.isEmpty(),
        )
    }

    @Test
    fun `the type scale covers every Material role`() {
        val styles = WEBORA_TYPOGRAPHY.textStyles()

        assertTrue("expected Material's full type scale; found ${styles.size}", styles.size >= TYPE_ROLES)
    }

    @Test
    fun `every shape corner comes from the compiled radii`() {
        val radii = WeboraRadius.ALL.map { it.value }.toSet()
        val strays = WEBORA_SHAPES.cornerBasedShapes().flatMap { (role, shape) ->
            shape.corners().filterNot { it in radii }.map { "$role has a ${it}dp corner" }
        }

        assertTrue("shape corners outside the compiled radii:\n" + strays.joinToString("\n"), strays.isEmpty())
    }

    @Test
    fun `a container role never rounds a dialog into a stadium`() {
        // `every shape corner comes from the compiled radii` constrains a corner's *provenance* and
        // is satisfied by any declared token, including one meant for a control. This constrains its
        // *fitness*, which is a different question and the one UX-009 was decided by: a Material
        // shape role is a container role — `AlertDialog` resolves its container to `extraLarge` —
        // and `CornerBasedShape` does not reject an over-large corner, it scales adjacent pairs down
        // to fit the shorter side. So a 999 dp pill on a dialog is not ignored; it becomes half the
        // width, the sides round away entirely, and `Surface` clips the heading out of the frame.
        //
        // The measurement therefore runs through the real `createOutline` rather than reading the
        // token, because the clamp is where a good number turns into a bad shape.
        val strays = WEBORA_SHAPES.cornerBasedShapes().flatMap { (role, shape) ->
            shape.resolvedCorners(DIALOG_SIZE)
                .filter { it > DIALOG_CORNER_CEILING }
                .map { "$role resolves to a ${it}px corner on a ${DIALOG_SIZE.width}px-wide dialog" }
        }

        assertTrue(
            "a corner above a quarter of the dialog's width is a container rounding its own " +
                "content away; the ceiling sits inside the degenerate half-width case on purpose:\n" +
                strays.joinToString("\n"),
            strays.isEmpty(),
        )
    }

    @Test
    fun `the pill is a control value and no shape role carries it`() {
        // The assertion above catches the consequence. This one names the cause, so a future pill in
        // a Material role fails as itself rather than as a number that happens to be too big. The
        // pill is not retired — `BrowserChrome` names it directly for the address field, the
        // identity chip and the dock — it is barred from the roles Material resolves containers to.
        val carriers = WEBORA_SHAPES.cornerBasedShapes()
            .filterValues { shape -> shape.corners().any { it == WeboraRadius.PILL.value } }
            .keys

        assertTrue(
            "a Material shape role holding ${WeboraRadius.PILL} rounds every dialog that defaults " +
                "to it into a stadium: $carriers",
            carriers.isEmpty(),
        )
    }

    @Test
    fun `the shape scale covers every role the public API can set`() {
        // Material 3 declares two further roles — `extraLargeIncreased` at 32 dp and
        // `extraExtraLarge` at 48 dp — which are `internal` to the library and absent from the
        // `Shapes` constructor. No public API can supply them, so they keep Material's values in any
        // component that reads them; that is a limitation to state rather than one to hide by adding
        // 32 and 48 to Webora's radii, which would be inventing values to satisfy a test.
        //
        // They are excluded structurally rather than by name: Kotlin mangles an `internal` member's
        // JVM name with a `$module` suffix, which is exactly the property that distinguishes them.
        val settable = WEBORA_SHAPES.cornerBasedShapes()

        assertEquals(SHAPE_ROLES, settable.size)
    }

    @Test
    fun `the projections differ, so the selector is doing something`() {
        // Every assertion above holds for a WeboraTheme that returns the light palette whatever the
        // system setting says. This is the one that would not.
        assertTrue(
            materialColorScheme(WeboraColors.LIGHT).colorRoles() !=
                materialColorScheme(WeboraColors.DARK).colorRoles(),
        )
    }

    @Test
    fun `the dark projection is the dark one`() {
        // Direction A re-derives fully for dark rather than fixing a surface, so the ground moves.
        // Asserted by luminance rather than by hex, which would just restate the palette file.
        val light = contrastRatio(WeboraColors.LIGHT.ground, BLACK)
        val dark = contrastRatio(WeboraColors.DARK.ground, BLACK)

        assertTrue("the dark ground must be darker than the light one", dark < light)
    }

    /**
     * Foreground/background role names, from Material's own naming convention.
     *
     * `onX` sits on `x`. Roles whose stripped name has no counterpart — `onPrimaryFixedVariant`,
     * whose background is `primaryFixed` — are skipped rather than guessed at; each is already
     * covered by the plain `onX` pair for the same container. `inverseOnSurface` does not follow the
     * prefix at all and is named explicitly, because dropping it would leave the one pair whose
     * foreground and background are both inverted unchecked.
     */
    private fun Map<String, *>.namedPairs(): List<Pair<String, String>> {
        val derived = keys
            .filter { it.startsWith(ON_PREFIX) }
            .map { it to it.removePrefix(ON_PREFIX).replaceFirstChar(Char::lowercaseChar) }
            .filter { (_, background) -> background in keys }
        return derived + listOf(INVERSE_PAIR).filter { (foreground, background) ->
            foreground in keys && background in keys
        }
    }

    private fun Typography.textStyles(): Map<String, TextStyle> = readByType(TextStyle::class.java)

    private fun Shapes.cornerBasedShapes(): Map<String, CornerBasedShape> =
        readByType(CornerBasedShape::class.java)

    /**
     * Public roles of [type], by name.
     *
     * `internal` members are excluded because Kotlin mangles their JVM names with a `$module`
     * suffix — the structural marker for "no public API can set this", which is the reason they are
     * out of scope rather than a list of the ones that happen to be inconvenient.
     */
    private fun <T> Any.readByType(type: Class<T>): Map<String, T> = javaClass.methods
        .filter { it.parameterCount == 0 && type.isAssignableFrom(it.returnType) }
        .filter { it.name.startsWith("get") && !it.name.contains(INTERNAL_MARKER) }
        .associate {
            val role = it.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar)
            role to requireNotNull(type.cast(it.invoke(this)))
        }

    /** Corner sizes in dp. At density 1 a pixel is a dp, which is what makes the comparison direct. */
    private fun CornerBasedShape.corners(): List<Float> = listOf(topStart, topEnd, bottomStart, bottomEnd)
        .map { it.toPx(SHAPE_SIZE, DENSITY) }

    /**
     * The corner radii Compose actually draws at [size], after its own clamp.
     *
     * [corners] reads the requested value; this reads the resolved one, and the two differ by
     * exactly the behaviour UX-009 turned on. A shape whose corners are all zero is an
     * [Outline.Rectangle] with no radii to read, which is a rectangle and therefore passes.
     */
    private fun CornerBasedShape.resolvedCorners(size: Size): List<Float> =
        when (val outline = createOutline(size, LayoutDirection.Ltr, DENSITY)) {
            is Outline.Rectangle -> emptyList()
            is Outline.Rounded -> with(outline.roundRect) {
                listOf(topLeftCornerRadius, topRightCornerRadius, bottomLeftCornerRadius, bottomRightCornerRadius)
            }.flatMap { listOf(it.x, it.y) }
            // `RoundedCornerShape` never produces one, and reading a `Path` would need a framework
            // type this source set does not have. Failing loudly beats a silently empty list.
            is Outline.Generic -> error("a rounded-corner shape produced a generic outline")
        }

    private companion object {
        val PROJECTIONS = listOf("light" to WeboraColors.LIGHT, "dark" to WeboraColors.DARK)
        val BLACK = androidx.compose.ui.graphics.Color.Black
        val DENSITY = Density(density = 1f)
        val SHAPE_SIZE = Size(width = 1000f, height = 1000f)

        /**
         * The narrowest box Material lays an `AlertDialog` out in, by a taller-than-wide height.
         *
         * Only the **shorter** side drives `CornerBasedShape`'s clamp, so the width is the number
         * that matters and the height merely has to exceed it; the consent dialog is by far the
         * tallest in the app and is nowhere near square.
         *
         * `DialogMinWidth = 280.dp` is `internal` to material3, so it is transcribed rather than
         * imported — which this repository distrusts on principle. It is safe in this one direction:
         * the corners here are absolute and the clamp only ever shrinks them, so a shape that clears
         * the ceiling at the narrowest width clears it at every width a dialog can reach. A Compose
         * release that *narrows* the minimum would make this assertion lenient, never wrong.
         */
        val DIALOG_SIZE = Size(width = 280f, height = 560f)

        /**
         * A quarter of the dialog's width.
         *
         * Half is the degenerate value — at half the width the side is fully round — so the ceiling
         * has to sit strictly inside it. A quarter leaves the 28 dp container radius (a tenth)
         * clearing comfortably while the 999 dp pill, clamped to 140, fails by a factor of two.
         */
        const val DIALOG_CORNER_CEILING = 70f

        /** Material 3's role counts, restated so a shrinking derivation fails. */
        const val MATERIAL_ROLES = 48
        const val TYPE_ROLES = 15
        const val SHAPE_ROLES = 5
        const val ON_PAIRS = 14

        /** Restated rather than imported, for the reason the palette test restates its own. */
        const val BODY = 4.5

        const val ON_PREFIX = "on"
        val INVERSE_PAIR = "inverseOnSurface" to "inverseSurface"

        /** Kotlin's JVM-name suffix for an `internal` member: `name$module`. */
        const val INTERNAL_MARKER = '$'
    }
}
