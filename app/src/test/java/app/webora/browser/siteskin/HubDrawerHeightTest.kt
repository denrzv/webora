package app.webora.browser.siteskin

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * The drawer's height policy, and the assertion that a website cannot reach it.
 *
 * `UX-026`'s whole gate-visible surface. The rendered half — does a two-row menu actually stop short
 * of the viewport on a real device — needs an emulator; the *decision* does not, which is why it
 * left the composable at all. `headerIdentityPlacement` is the same shape for the same reason.
 */
class HubDrawerHeightTest {
    /** The plan's table, at the hosted device's safe height and at the 320 dp floor. */
    @Test
    fun `the bounds are a fraction of the available height`() {
        val hosted = hubDrawerHeight(HOSTED_SAFE_HEIGHT)

        assertEquals(HOSTED_SAFE_HEIGHT * MAX_FRACTION, hosted.max)
        assertEquals("the minimum does not bind on a device this tall", MIN_HEIGHT, hosted.min)
        assertTrue(
            "a full panel must still leave scrim to tap — this is dismissal, not decoration",
            hosted.max < HOSTED_SAFE_HEIGHT,
        )
    }

    /**
     * The reference integration wraps: neither bound binds and the content decides.
     *
     * Research F4 measures Bloom's drawer at ~272 dp against ~800 dp of safe height. The bounds exist
     * for the degenerate ends; the middle is what `heightIn` plus the content produces, and this
     * asserts the rule leaves that middle alone rather than pinning it.
     */
    @Test
    fun `a short menu is bounded by neither end`() {
        val bounds = hubDrawerHeight(HOSTED_SAFE_HEIGHT)

        assertTrue("Bloom's estimated content clears the minimum", BLOOM_CONTENT > bounds.min)
        assertTrue("and is far under the maximum", BLOOM_CONTENT < bounds.max)
    }

    /**
     * Total, and never `min > max`.
     *
     * `heightIn` throws on inverted bounds, so a rule that let the minimum escape the maximum would
     * crash the browser on a small enough window — a failure caused by the device rather than by
     * anything the site did. The coercion is what stops that, and this is the case that fails when
     * somebody removes it for reading oddly.
     */
    @Test
    fun `a viewport under the minimum still yields usable bounds`() {
        listOf(0.dp, (-10).dp, 1.dp, 40.dp, MIN_HEIGHT, 400.dp, 2000.dp).forEach { available ->
            val bounds = hubDrawerHeight(available)

            assertTrue("min > max at $available", bounds.min <= bounds.max)
            assertTrue("negative minimum at $available", bounds.min >= 0.dp)
            assertTrue("negative maximum at $available", bounds.max >= 0.dp)
        }
    }

    /** A taller window can only offer a taller panel; the rule is monotonic. */
    @Test
    fun `more available height is never less room`() {
        val heights = listOf(0.dp, 100.dp, 400.dp, HOSTED_SAFE_HEIGHT, 2000.dp).map { hubDrawerHeight(it).max }

        assertEquals(heights.sortedBy { it.value }, heights)
    }

    /**
     * The rule takes a dimension and returns dimensions — nothing a manifest can supply.
     *
     * **This is the ticket's security assertion, and it is deliberately two-sided.** The tempting
     * implementation reads `model.siteMenu.size`: a site-controlled number that looks like a layout
     * input and would produce a result that looks right for the reference integration.
     *
     * Reflection alone cannot say it, because `Dp` is a value class and erases to `float` — an
     * `itemCount: Int` would be caught, but a `fontScale: Float` would slip through erasure looking
     * identical to a dimension. So arity and erasure are asserted against the compiled method, and
     * the declared types against the declaration line. Together they admit exactly one signature.
     *
     * `kotlin-reflect` is not on the test classpath and is deliberately not added for this —
     * `ColorRoles.kt` records that rule. The method is located by prefix because an `internal`
     * top-level function's JVM name carries a module suffix.
     *
     * Its negative control is adding a second parameter of any type, which fails here and nowhere
     * else in the suite.
     */
    @Test
    fun `the rule reads a dimension and nothing else`() {
        val method = Class.forName("$PACKAGE.HubDrawerHeightKt").declaredMethods
            .filterNot { it.isSynthetic }
            .single { it.name.startsWith("hubDrawerHeight") }

        assertEquals("one parameter, erased from Dp", listOf(FLOAT), method.parameterTypes.toList())
        assertEquals(HubDrawerHeight::class.java, method.returnType)
        assertTrue(
            "the declared signature is the half erasure cannot see",
            "internal fun hubDrawerHeight(available: Dp): HubDrawerHeight {" in source(),
        )

        val fields = HubDrawerHeight::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }

        assertEquals("two dimensions and nothing else", listOf(FLOAT, FLOAT), fields.map { it.type })
        assertEquals(listOf("max", "min"), fields.map { it.name }.sorted())
    }

    private companion object {
        const val PACKAGE = "app.webora.browser.siteskin"

        /** `Dp` is a value class, so it erases to `float` at every JVM boundary. */
        val FLOAT: Class<*> = Float::class.javaPrimitiveType!!

        fun source(): String = File("src/main/java/app/webora/browser/siteskin/HubDrawerHeight.kt").readText()

        /** Pixel 6 profile, 1080 × 2400 at 2.75, less the status and navigation bars. */
        val HOSTED_SAFE_HEIGHT = 800.dp

        /** Research F4's estimate for the reference integration's two rows plus its header. */
        val BLOOM_CONTENT = 272.dp

        val MIN_HEIGHT = 180.dp
        const val MAX_FRACTION = 0.85f
    }
}
