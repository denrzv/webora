package app.webora.browser.siteskin

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The placement rule, and the assertion that a website cannot reach it.
 *
 * `UX-023`'s whole gate-visible surface. Research F8 records why that matters here specifically: the
 * rendered half — does a 13-character domain actually escape the ellipsis at 200% — is a
 * text-measurement fact needing a device, so the more of this ticket that lives in the pure
 * decision, the more of it a developer machine can defend.
 */
class HeaderIdentityPlacementTest {
    /** Every row of the plan's measured table, in both directions. */
    @Test
    fun `the measured table is what the rule returns`() {
        assertEquals(
            "a 13-character domain still fits beside the title at default scale",
            HeaderIdentityPlacement.INLINE,
            headerIdentityPlacement(COMPACT, fontScale = 1f, domainLength = SHORT_DOMAIN),
        )
        assertEquals(
            "at 200% it cannot, and the title is what would otherwise measure to zero",
            HeaderIdentityPlacement.OWN_ROW,
            headerIdentityPlacement(COMPACT, fontScale = 2f, domainLength = SHORT_DOMAIN),
        )
        assertEquals(
            "the hosted emulator's width keeps the reference integration inline at default scale",
            HeaderIdentityPlacement.INLINE,
            headerIdentityPlacement(TYPICAL, fontScale = 1f, domainLength = BLOOM_DOMAIN),
        )
        assertEquals(
            HeaderIdentityPlacement.OWN_ROW,
            headerIdentityPlacement(TYPICAL, fontScale = 2f, domainLength = BLOOM_DOMAIN),
        )
    }

    /**
     * A rule returning one constant satisfies half the table and would ship.
     *
     * `INLINE` always passes the two default-scale rows; `OWN_ROW` always passes the two large-scale
     * rows. Neither is caught by a table read one row at a time, so the reachability of both values
     * is asserted directly.
     */
    @Test
    fun `both placements are reachable`() {
        val reached = SCALES.flatMap { scale ->
            WIDTHS.flatMap { width ->
                DOMAIN_LENGTHS.map { length -> headerIdentityPlacement(width, scale, length) }
            }
        }.toSet()

        assertEquals(HeaderIdentityPlacement.entries.toSet(), reached)
    }

    /**
     * Total, and its degenerate inputs fail toward the branch that cannot truncate.
     *
     * A zero width is what `BoxWithConstraints` reports on the first frame of some compositions, and
     * a rule that answered `INLINE` there would put the trust mark in a row it has been measured not
     * to fit. Failing to `OWN_ROW` costs a row of height and guarantees nothing is cut.
     */
    @Test
    fun `degenerate inputs fall to the placement that cannot truncate`() {
        listOf(0.dp, (-10).dp).forEach { width ->
            assertEquals(
                "width $width",
                HeaderIdentityPlacement.OWN_ROW,
                headerIdentityPlacement(width, fontScale = 1f, domainLength = SHORT_DOMAIN),
            )
        }
        assertEquals(
            "a non-positive font scale is not a reason to claim the chip fits",
            HeaderIdentityPlacement.OWN_ROW,
            headerIdentityPlacement(COMPACT, fontScale = 0f, domainLength = SHORT_DOMAIN),
        )
        assertEquals(
            "an empty domain is not a crash and not an inline claim it cannot support",
            HeaderIdentityPlacement.INLINE,
            headerIdentityPlacement(TYPICAL, fontScale = 1f, domainLength = 0),
        )
        assertEquals(
            "a negative length is coerced rather than producing a negative requirement",
            headerIdentityPlacement(TYPICAL, fontScale = 1f, domainLength = 0),
            headerIdentityPlacement(TYPICAL, fontScale = 1f, domainLength = -5),
        )
    }

    /** A longer domain needs more room, so it can only ever wrap earlier — never later. */
    @Test
    fun `the rule is monotonic in every input`() {
        assertEquals(
            HeaderIdentityPlacement.OWN_ROW,
            headerIdentityPlacement(COMPACT, fontScale = 1f, domainLength = 40),
        )
        assertEquals(
            "more width can only help",
            HeaderIdentityPlacement.INLINE,
            headerIdentityPlacement(600.dp, fontScale = 2f, domainLength = BLOOM_DOMAIN),
        )
    }

    /**
     * The decision reads no manifest value, which is the security property and not a style rule.
     *
     * Research F3: `model.title` and `model.subtitle` are the two site-controlled values in this
     * row, and a rule that consulted either would let a website choose which row the browser's trust
     * mark lands on. The scan reads executable lines — `BROWSE-009`'s rule — because this file's own
     * KDoc names `title` and `subtitle` in order to explain why they are absent.
     */
    @Test
    fun `the decision reads no site-controlled value`() {
        val source = executableLines()

        assertTrue("the scan must see real code", "fun headerIdentityPlacement(" in source)
        listOf(
            "model.", "title", "subtitle", "SiteSkinConfiguration", "SiteSkinTopBarModel",
            "presentation", "SiteSkinColorScheme", "NavigationItem",
        ).forEach { assertFalse("$it must not reach the placement rule", it in source) }
    }

    private companion object {
        /** 320 dp host minus the expressive header's two 20 dp gutters. */
        val COMPACT = 280.dp

        /** 360 dp host — the hosted screenshot emulator's width. */
        val TYPICAL = 320.dp

        const val SHORT_DOMAIN = 13 // example.co.uk
        const val BLOOM_DOMAIN = 16 // denrzv.github.io

        val SCALES = listOf(1f, 1.3f, 2f)
        val WIDTHS = listOf(COMPACT, TYPICAL, 600.dp)
        val DOMAIN_LENGTHS = listOf(0, SHORT_DOMAIN, BLOOM_DOMAIN, 40)

        fun executableLines(): String {
            val file = File("src/main/java/app/webora/browser/siteskin/HeaderIdentityPlacement.kt")
            check(file.exists()) { "decision source not found at ${file.absolutePath}" }
            return file.readLines()
                .filterNot { line ->
                    val trimmed = line.trimStart()
                    trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
                }
                .joinToString("\n")
        }
    }
}
