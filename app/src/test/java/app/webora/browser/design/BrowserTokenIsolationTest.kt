package app.webora.browser.design

import androidx.compose.ui.graphics.Color
import app.webora.browser.siteskin.SiteSkinTheme
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `C2`: no manifest value can reach a browser token.
 *
 * `ADR-013` hands this test over by name, and says why it belongs in this ticket rather than after
 * it: the invariant was maintained by an *absence*. There was no browser palette, so there was
 * nothing for a website's colours to leak into. Creating one is exactly when the accident stops
 * protecting it.
 *
 * The sharpest version of the leak is not a wrong background somewhere. It is the identity chip:
 * `ADR-006` makes the registrable domain and TLS state browser-owned, and `HARDEN-002` makes brand
 * impersonation the threat that argument exists to stop. A manifest-derived colour on that element
 * is a website influencing Webora's own identity presentation — the same attack as hiding the
 * domain, arriving through colour instead of through text.
 *
 * **Two halves, because neither can fail for the other's reason.** The sweep would pass a design
 * that derives browser tokens from a manifest but happens to be stable for the manifests tried. The
 * source scan would pass a design that reaches the siteskin package through reflection on a string.
 * Between them: a value that moves with a manifest fails the sweep whatever route it took, and a
 * compile-time path fails the scan whether or not it currently changes anything.
 */
class BrowserTokenIsolationTest {

    @Test
    fun `projecting manifests does not move a browser token`() {
        val before = projections()

        BRANDING.forEach { SiteSkinTheme.from(configuration(it)) }

        assertEquals("a browser token changed while website manifests were projected", before, projections())
    }

    @Test
    fun `no browser token holds a colour a manifest asked for`() {
        // The corpus is chosen to be nowhere near Direction A's teal-on-warm-neutral, so an equality
        // here means a value travelled rather than coincided.
        val declared = projections().values.flatMap { it.values }.toSet()
        val requested = BRANDING.flatMap { it.map(::parseHex) }

        val leaked = requested.filter { it in declared }

        assertTrue("a colour a manifest requested is now a browser token: $leaked", leaked.isEmpty())
    }

    @Test
    fun `the website projection did carry those colours`() {
        // The liveness half. Without it, a sweep that silently stopped exercising the projection —
        // a renamed field, a validator rejecting the corpus — would pass by doing nothing, which is
        // the failure mode a stability assertion is most prone to.
        val branding = BRANDING.first()
        val scheme = SiteSkinTheme.from(configuration(branding)).light

        assertEquals(parseHex(branding[0]), scheme.primary)
        assertEquals(parseHex(branding[2]), scheme.background)
    }

    @Test
    fun `no design source imports the website side`() {
        val offenders = designSources().flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                if (FORBIDDEN_IMPORT.containsMatchIn(line)) "${source.name}:${index + 1} ${line.trim()}" else null
            }
        }

        assertTrue(
            "the browser token layer must not depend on the website side; SiteSkinColorScheme is " +
                "the entire website-influenceable colour surface and this package is the other " +
                "one:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no design source mentions the website side in code`() {
        // Beyond imports, because a fully-qualified reference needs none. Comments and string
        // literals are removed first: this file's own subject makes it impossible to document the
        // separation without naming the thing it is separate from, and a rule that forbade the word
        // would forbid the explanation.
        val offenders = designSources().mapNotNull { source ->
            val code = source.readText().withoutCommentsOrStrings()
            FORBIDDEN_SYMBOL.find(code)?.let { "${source.name} references ${it.value}" }
        }

        assertTrue(
            "website-side references in browser token code:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the scan reaches the sources it claims to`() {
        // A scan pointed at nothing passes forever. The floor rises as the layer grows; a root that
        // stops contributing fails here rather than quietly disabling the two rules above.
        val sources = designSources()

        assertTrue("expected the design package to hold sources; found ${sources.size}", sources.size >= MIN_SOURCES)
    }

    @Test
    fun `the comment stripper does not create blind spots`() {
        // The stripper is what the code scan trusts. If it removed too much, a real reference would
        // survive review by hiding behind a URL; if too little, the rule would forbid its own
        // documentation. Both directions, on the two shapes that actually occur.
        val source = """
            // import dev.siteskin.core.Removed
            val url = "https://example.test//not-a-comment"
            val kept = Marker
        """.trimIndent()

        val code = source.withoutCommentsOrStrings()

        assertTrue("a commented-out import must not survive: $code", !code.contains("dev.siteskin"))
        assertTrue("code after a string containing // must survive: $code", code.contains("Marker"))
    }

    private fun projections(): Map<String, Map<String, Color>> = mapOf(
        "light" to WeboraColors.LIGHT.colorRoles(),
        "dark" to WeboraColors.DARK.colorRoles(),
    )

    private fun configuration(branding: List<String>) = SiteSkinValidator.validate(
        manifest(branding).byteInputStream(),
        ORIGIN,
    ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private fun manifest(branding: List<String>): String {
        val fields = BRANDING_FIELDS.zip(branding).joinToString { (name, value) -> "\"$name\":\"$value\"" }
        return """{"schemaVersion":"1.0","site":{"id":"brand","name":"Brand"},"branding":{$fields}}"""
    }

    private fun parseHex(value: String): Color = Color(value.removePrefix("#").toLong(HEX_RADIX) or OPAQUE_ALPHA)

    private companion object {
        const val ORIGIN = "https://brand.example"
        const val HEX_RADIX = 16
        const val OPAQUE_ALPHA = 0xFF000000L
        const val MIN_SOURCES = 5

        val BRANDING_FIELDS = listOf("primaryColor", "secondaryColor", "backgroundColor", "textColor")

        /**
         * Manifests whose colours could not be mistaken for Direction A's.
         *
         * Magenta, orange and violet against Webora's teal on warm neutral — `ADR-013` chose that
         * teal precisely so the browser's own colour does not read as part of the page, and the same
         * distance is what makes an equality assertion meaningful here.
         */
        val BRANDING = listOf(
            listOf("#D94F8A", "#FADADD", "#FFF7FA", "#2B1B24"),
            listOf("#FF6B00", "#FFE0C2", "#FFF4E8", "#241505"),
            listOf("#6A0DAD", "#E4D3F5", "#F6F0FC", "#1A0426"),
        )

        val FORBIDDEN_IMPORT = Regex("""^import\s+(dev\.siteskin|app\.webora\.browser\.siteskin)""")
        val FORBIDDEN_SYMBOL = Regex("""\bSiteSkin\w*""")

        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
        const val DESIGN_PACKAGE = "app/webora/browser/design"

        fun designSources(): List<File> =
            requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY)) {
                "$SOURCE_ROOT_PROPERTY is unset; app/build.gradle.kts must pass the app source roots"
            }
                .split(File.pathSeparator)
                .map { File(it, DESIGN_PACKAGE) }
                .filter(File::isDirectory)
                .flatMap { it.walkTopDown() }
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy(File::getName)
    }
}

/**
 * Kotlin source with comments and string literals removed.
 *
 * A character walk rather than a regex, for the reason `inspectorValue` is one: a regex that strips
 * `//` to end of line also strips everything after the `//` in `"https://…"`, and a scan that
 * quietly removes real code is a scan that stops finding things. `BrowserTokenIsolationTest` asserts
 * both directions of that directly.
 */
internal fun String.withoutCommentsOrStrings(): String {
    val out = StringBuilder()
    var index = 0
    while (index < length) {
        index = when {
            startsWith("/*", index) -> skipTo("*/", index + 2)
            startsWith("//", index) -> skipToLineEnd(index)
            startsWith("\"\"\"", index) -> skipTo("\"\"\"", index + 3)
            this[index] == '"' -> skipStringLiteral(index + 1)
            else -> {
                out.append(this[index])
                index + 1
            }
        }
    }
    return out.toString()
}

private fun String.skipTo(terminator: String, from: Int): Int =
    indexOf(terminator, from).let { if (it < 0) length else it + terminator.length }

private fun String.skipToLineEnd(from: Int): Int = indexOf('\n', from).let { if (it < 0) length else it }

private fun String.skipStringLiteral(from: Int): Int {
    var index = from
    while (index < length) {
        when (this[index]) {
            '\\' -> index++
            '"' -> return index + 1
            else -> Unit
        }
        index++
    }
    return length
}
