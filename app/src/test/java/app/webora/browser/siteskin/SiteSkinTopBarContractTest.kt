package app.webora.browser.siteskin

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The integrated trust mark is browser-owned, structurally.
 *
 * `UX-021` puts a compiled browser colour and a browser-derived domain inside a header a manifest
 * paints. Runtime tests can show the chip renders; only a source scan can show that no path exists
 * from the SiteSkin palette to its ground, its tint or its text — the difference `UX-003` records
 * between "runtime behaviour and source structure fail under different regressions".
 *
 * **The scan reads executable lines only.** `BROWSE-009` found this trap three times in one ticket:
 * a KDoc naming the thing it forbids satisfies or violates the rule on the code's behalf. This
 * file's own doc comment mentions `presentation.colors`, and would fail its own assertion under a
 * `readText()`.
 */
class SiteSkinTopBarContractTest {

    @Test
    fun `the security chip grounds on the container role its colours are measured against`() {
        // Not a style preference. `WeboraColorSchemeTest` measures `secure` and `notSecure` only
        // against `container`, and `materialColorScheme` maps `primaryContainer` to `container`
        // while mapping `surfaceContainer` to `chrome`. The deleted full-width row used
        // `surfaceContainer`, which was harmless only because it drew its text in `onSurface`.
        val chip = securityChipSource()

        assertTrue(
            "the chip must ground on primaryContainer, the role secure/notSecure are measured on",
            "MaterialTheme.colorScheme.primaryContainer" in chip,
        )
        assertFalse(
            "surfaceContainer maps to `chrome`, which no measured pair covers for these colours",
            "surfaceContainer" in chip,
        )
    }

    @Test
    fun `the security chip tints from the compiled browser palette`() {
        val chip = securityChipSource()

        assertTrue("the shield must tint from the compiled secure role", "browserColors.secure" in chip)
        assertTrue("the shield must tint from the compiled notSecure role", "browserColors.notSecure" in chip)
        assertTrue(
            "the palette must be read from the compiled projection, chosen by the system setting",
            "WeboraColors.scheme(isSystemInDarkTheme())" in chip,
        )
    }

    @Test
    fun `no manifest colour reaches the security chip`() {
        // The sharpest version of this leak is not a wrong background — it is a manifest-derived
        // colour on the trust mark, which is `HARDEN-002`'s impersonation surface arriving through
        // colour instead of text. The negative control is a one-word edit: ground the chip on
        // `presentation.colors.background`.
        val chip = securityChipSource()

        FORBIDDEN_IN_CHIP.forEach { forbidden ->
            assertFalse(
                "a manifest-influenced value reached the browser's trust mark: '$forbidden'",
                forbidden in chip,
            )
        }
    }

    @Test
    fun `the security tag is applied to a node rather than merely declared`() {
        // `UX-020`'s lesson: the constant is declared in this same file, so `source.contains(TAG)`
        // is satisfied by the declaration and would pass over a top bar that had dropped the tag.
        // `CI-009` is pending hosted acceptance on this exact tag, so its application is the
        // contract, not its existence.
        val chip = securityChipSource()

        assertTrue(
            "SITESKIN_SECURITY_TAG must be applied to the chip's node",
            "testTag(SITESKIN_SECURITY_TAG)" in chip,
        )
    }

    @Test
    fun `the security identity is laid out after the flexible title`() {
        // Issue requirement 10, as a structural fact rather than a rendered one. A manifest controls
        // the title's length; if the chip were declared before the `weight(1f)` column, or given a
        // weight of its own, a long enough title could push the browser's trust mark out of the
        // header. The bounds assertion in `SiteSkinTopBarTest` covers the rendered half.
        val source = executableLines(topBarFile())
        val title = source.indexOf("Modifier.weight(1f)")
        val chip = source.indexOf("SiteSkinSecurityChip(")

        assertTrue("the title column must still be the flexible sibling", title > 0)
        assertTrue("the chip must be declared after the flexible title column", chip > title)
    }

    @Test
    fun `the full-width security row and its separator are gone`() {
        val source = executableLines(topBarFile())

        assertFalse("the deleted row must not return", "fun SecurityIdentity(" in source)
        assertFalse(
            "the separator string has no remaining reader and must not read like a live contract",
            "siteskin_security_separator" in source,
        )
    }

    @Test
    fun `the scan reads code and not prose`() {
        // Guards the guard. Every assertion above is a `contains` over this projection, so a
        // regression in `executableLines` would quietly turn the whole file into decoration. The
        // top bar's own KDoc names the forbidden ground while explaining why the chip must not use
        // it — under a `readText()` that prose alone fails the isolation assertion.
        //
        // Anchored on a phrase that cannot also be code. An earlier version used
        // `presentation.colors.background`, which the strip must hide *and* a regression would
        // legitimately add — so it failed under the very control it was supposed to be neutral to,
        // and could not tell a broken strip from a real violation.
        val raw = topBarFile().readText()
        val stripped = executableLines(topBarFile())

        assertTrue("the KDoc explaining the ground rule is still there", PROSE_ONLY in raw)
        assertFalse("but the scan must not see prose", PROSE_ONLY in stripped)
        assertTrue("executable lines must survive the strip", "SiteSkinSecurityChip(" in stripped)
    }

    private companion object {
        val FORBIDDEN_IN_CHIP = listOf(
            "presentation",
            "SiteSkinColorScheme",
            "colors.background",
            "colors.primary",
            "colors.secondary",
            "colors.onBackground",
            "model.title",
            "model.subtitle",
            "model.brandAsset",
        )

        const val SOURCE_ROOT_PROPERTY = "webora.app.src"

        /** A sentence from the chip's KDoc. Prose, and unable to become code under any regression. */
        const val PROSE_ONLY = "whose entire job is to be trustworthy"

        /**
         * Located through the same property `BrowserSurfaceConventionsTest` uses, never a relative
         * path: the working directory a test runs in is not a contract, and a scan that silently
         * fails to find its subject is a scan that passes for the wrong reason.
         */
        fun topBarFile(): File {
            val roots = requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY)) {
                "$SOURCE_ROOT_PROPERTY is unset; app/build.gradle.kts must pass the app source roots"
            }.split(File.pathSeparator).map(::File)

            return roots
                .map { File(it, "app/webora/browser/siteskin/SiteSkinTopBar.kt") }
                .firstOrNull(File::exists)
                ?: error("SiteSkinTopBar.kt not found under any of $roots")
        }

        /**
         * The chip's own declaration, so a colour used legitimately elsewhere in the file — the brand
         * logo genuinely does read `colors.background` — cannot satisfy or violate the chip's rule.
         */
        fun securityChipSource(): String {
            val source = executableLines(topBarFile())
            val start = source.indexOf("private fun SiteSkinSecurityChip(")
            check(start >= 0) { "SiteSkinSecurityChip declaration not found" }
            val next = source.indexOf("\nprivate fun ", start + 1)
            val end = if (next >= 0) next else source.length
            return source.substring(start, end)
        }

        /**
         * Source with comment lines removed.
         *
         * `BROWSE-009`: a source scan reads executable lines, never `readText()`, or the prose will
         * satisfy or violate the rule on the code's behalf. Strips whole-line comments and block
         * continuations, which is what KDoc is made of; a trailing `// note` after real code keeps
         * its code, which is the conservative direction for a rule that mostly forbids.
         */
        fun executableLines(file: File): String = file.readLines()
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
            }
            .joinToString("\n")
    }
}
