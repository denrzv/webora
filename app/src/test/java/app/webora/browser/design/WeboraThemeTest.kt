package app.webora.browser.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
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
    fun `the Material scheme is fully populated`() {
        // The closure assertion above would pass vacuously if reflection found nothing. It would also
        // pass on a scheme with three roles. Material's own surface is what it is; this pins that the
        // derivation covers all of it.
        val roles = materialColorScheme(WeboraColors.LIGHT).colorRoles()

        assertTrue("expected Material's full role set; found ${roles.size}", roles.size >= MATERIAL_ROLES)
    }

    @Test
    fun `every text style is sized from the compiled scale`() {
        val strays = weboraTypography().textStyles()
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
        val styles = weboraTypography().textStyles()

        assertTrue("expected Material's full type scale; found ${styles.size}", styles.size >= TYPE_ROLES)
    }

    @Test
    fun `every shape corner comes from the compiled radii`() {
        val radii = WeboraRadius.ALL.map { it.value }.toSet()
        val strays = weboraShapes().cornerBasedShapes().flatMap { (role, shape) ->
            shape.corners().filterNot { it in radii }.map { "$role has a ${it}dp corner" }
        }

        assertTrue("shape corners outside the compiled radii:\n" + strays.joinToString("\n"), strays.isEmpty())
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
        val settable = weboraShapes().cornerBasedShapes()

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

    private companion object {
        val PROJECTIONS = listOf("light" to WeboraColors.LIGHT, "dark" to WeboraColors.DARK)
        val BLACK = androidx.compose.ui.graphics.Color.Black
        val DENSITY = Density(density = 1f)
        val SHAPE_SIZE = Size(width = 1000f, height = 1000f)

        /** Material 3's role counts, restated so a shrinking derivation fails. */
        const val MATERIAL_ROLES = 48
        const val TYPE_ROLES = 15
        const val SHAPE_ROLES = 5

        /** Kotlin's JVM-name suffix for an `internal` member: `name$module`. */
        const val INTERNAL_MARKER = '$'
    }
}
