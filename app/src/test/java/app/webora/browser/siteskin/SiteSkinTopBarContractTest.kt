package app.webora.browser.siteskin

import java.io.File
import org.junit.Assert.assertEquals
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
    fun `the browser refresh control is browser-owned in every value it reads`() {
        // `BROWSE-011`. The control sits inside a header a manifest paints, so the runtime can only
        // show that it renders; a source scan is what shows no path exists from the SiteSkin palette
        // or the site's model to its ground, its icon, its label or its callback. The negative
        // control is a one-word edit: ground the tile on `presentation.colors.secondary`.
        val control = declaration("private fun BrowserControlRow(")

        assertTrue("the control must draw the compiled reload icon", "R.drawable.ic_reload" in control)
        assertTrue("its name must be a browser-authored resource", "stringResource(R.string.reload)" in control)
        FORBIDDEN_IN_CHIP.forEach { forbidden ->
            assertFalse(
                "a manifest-influenced value reached a browser command: '$forbidden'",
                forbidden in control,
            )
        }
    }

    @Test
    fun `browser controls in the header share one Webora-token sub-surface`() {
        // `UX-014`: the header's colours are the site's, so a browser control drawn straight onto
        // them reads as the site's — "the visual boundary is the ownership boundary". One
        // declaration rather than two copies, for the reason `UX-021` records about a `when` that
        // shipped twice and drifted.
        val tile = declaration("private fun BrowserControlTile(")

        assertTrue(
            "the browser tile must ground on a Webora token",
            "MaterialTheme.colorScheme.surfaceContainer" in tile,
        )
        assertFalse("no site colour may paint a browser control's tile", "presentation" in tile)
        assertFalse("no site colour may paint a browser control's tile", "colors." in tile)

        val source = executableLines(topBarFile())
        assertTrue("Back must use the shared tile", "BrowserControlTile(SITESKIN_BACK_TAG)" in source)
        assertTrue("Refresh must use the shared tile", "BrowserControlTile(SITESKIN_REFRESH_TAG)" in source)
    }

    @Test
    fun `the refresh tag is applied to a node rather than merely declared`() {
        // `UX-020`'s lesson again: the constant is declared in this same file, so a `contains` over
        // the bare name would be satisfied by the declaration alone.
        val source = executableLines(topBarFile())

        assertTrue(
            "SITESKIN_REFRESH_TAG must reach a node",
            "BrowserControlTile(SITESKIN_REFRESH_TAG)" in source,
        )
        assertTrue("the tile is what applies it", "testTag(tag)" in declaration("private fun BrowserControlTile("))
    }

    @Test
    fun `the browser control row does not compete with the brand row for width`() {
        // `BROWSE-011`'s whole placement argument, as a structural fact. The brand row has 164 dp
        // for the title and the trust chip at 320 dp; a browser control in *that* row truncates the
        // domain and measures the site's title to zero. It is also why the control row must declare
        // no weight: the assertion above locates the title column by the file's first
        // `Modifier.weight(1f)`, and a second weighted child would make that depend on declaration
        // order for an unrelated reason.
        val control = declaration("private fun BrowserControlRow(")
        val brand = declaration("private fun BrandRow(")

        assertFalse("the control row must not introduce a second weighted child", "weight(" in control)
        assertTrue("it is trailing-aligned instead", "Arrangement.End" in control)
        assertFalse("no browser command may join the brand row", "BrowserControlRow(" in brand)
        assertTrue("the brand row still carries Back", "BrowserBack(" in brand)
        assertTrue("and still ends with the trust chip", "SiteSkinSecurityChip(" in brand)
    }

    /**
     * Both placements keep the trust chip away from site content, and this is the branch-complete
     * statement of `UX-021`'s guarantee.
     *
     * The assertion above covers the `INLINE` branch by declaration order — the chip follows the
     * weighted title, so a long title yields to it. `UX-023` added a second branch, and order says
     * nothing there. In `OWN_ROW` the chip sits in a row that contains no model text at all, so a
     * manifest cannot compete with it by any mechanism; that is stronger than the ordering rule, and
     * this is where it is written down.
     *
     * Worth keeping because the ordering assertion is genuinely fragile: it locates the chip by the
     * file's *first* `SiteSkinSecurityChip(`, so declaring the identity row earlier in the file
     * breaks it for a reason that has nothing to do with the guarantee. That happened during
     * implementation — the plan predicted the assertion would pass unedited and it did not, until
     * the declaration moved below `BrandRow`.
     */
    @Test
    fun `both identity placements keep the chip away from site content`() {
        val identityRow = declaration("private fun SiteSkinIdentityRow(")
        val brand = declaration("private fun BrandRow(")

        assertTrue("the wrapped row draws the chip", "SiteSkinSecurityChip(" in identityRow)
        listOf("model.title", "model.subtitle", "model.brandAsset", "BrandLogo(", "Text(").forEach {
            assertFalse("$it must not share the wrapped identity row", it in identityRow)
        }

        assertTrue("the inline chip is still conditional on the browser's decision", "if (inlineIdentity)" in brand)
        assertFalse("and the brand row still declares no weight for it", "SiteSkinSecurityChip(\n" in brand)
    }

    /**
     * The header asks the decision; it does not re-derive one.
     *
     * One `BoxWithConstraints` and one `LocalDensity` read, both feeding `headerIdentityPlacement`.
     * A second width or scale read here would be a second answer to the same question, free to
     * disagree with the first — `UX-021`'s "one `when`, one owner" applied to a layout rule.
     */
    @Test
    fun `the header reads width and scale once and asks the decision`() {
        val source = executableLines(topBarFile())

        // The call, not the mention: an `import` line is not a read, and counting bare
        // `BoxWithConstraints` scores the import too. `UX-020`'s rule — assert the application, not
        // the declaration — which that ticket learned from a tag check satisfied by its own constant.
        assertEquals("one width read", 1, source.split("BoxWithConstraints(").size - 1)
        assertEquals("one font-scale read", 1, source.split("LocalDensity.current.fontScale").size - 1)
        assertEquals("one placement decision", 1, source.split("headerIdentityPlacement(").size - 1)
        assertTrue(
            "and it is fed the browser-derived domain, never manifest text",
            "model.security.registrableDomain.length" in source,
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
        /**
         * One top-level declaration's own text, so a value used legitimately elsewhere in the file
         * cannot satisfy or violate its rule. `securityChipSource` is this, specialised; both stop
         * at the next `private fun` rather than at a brace count, which is enough while every
         * declaration in this file is top-level.
         */
        fun declaration(signature: String): String {
            val source = executableLines(topBarFile())
            val start = source.indexOf(signature)
            check(start >= 0) { "declaration not found: $signature" }
            val next = source.indexOf("\nprivate fun ", start + 1)
            val end = if (next >= 0) next else source.length
            return source.substring(start, end)
        }

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
