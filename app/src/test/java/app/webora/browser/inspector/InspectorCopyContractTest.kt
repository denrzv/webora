package app.webora.browser.inspector

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules about the copy path that no runtime assertion can reach, because the wiring lives inside a
 * `@Composable` and the JVM gate does not run one.
 *
 * Read from **executable lines**: a line opening a doc comment, a block comment or a line
 * comment is stripped before matching. `BROWSE-009` hit this three times in one ticket — a KDoc
 * satisfying or violating the very rule it was describing — and the corollary it left is followed
 * here too: an anchor must be prose in every world, never a string a real regression would
 * legitimately add.
 *
 * Kotlin block comments **nest**, which this file found the hard way: a KDoc quoting a comment
 * opener opened a second comment and swallowed the rest of the file.
 */
class InspectorCopyContractTest {

    @Test fun `a string enters the document at exactly one place`() {
        // The whole disclosure argument rests on this. Every value in the document goes through
        // `bounded()`, which applies the panel's own `inspectorValue`; a second construction site is
        // how an unbounded website string would reach a clipboard the export claims only mirrors the
        // panel. Counting sites makes that a thing an edit has to *do*, not a thing it can forget.
        val sites = executableLines(SERIALIZER).count { it.contains("JsonString(") }

        assertEquals(
            "InspectorJson.kt must construct JsonString in one place only, so every value passes " +
                "the same bound; found $sites",
            1,
            sites,
        )
    }

    @Test fun `the one site is the bounding helper`() {
        // Counting alone would pass if the single site stopped bounding. Assert the application, not
        // the count — `UX-020`'s rule, which a declaration-satisfied `contains` had already broken
        // once in this repository.
        val site = executableLines(SERIALIZER).single { it.contains("JsonString(") }

        assertTrue(
            "the one JsonString site must apply inspectorValue, was: $site",
            site.contains("JsonString(inspectorValue("),
        )
    }

    @Test fun `the copy control serializes the snapshot it was handed`() {
        val slice = copyControl()

        assertTrue(
            "the copy control must serialize its own snapshot parameter",
            slice.any { it.contains("inspectorJson(snapshot)") },
        )
    }

    @Test fun `every remember in the copy control is keyed on that snapshot`() {
        // An unkeyed `remember` is the whole defect the issue's tab-change requirement describes: the
        // panel would go on copying the first origin's document while displaying a second origin's
        // rows. `rememberCoroutineScope()` is deliberately not matched — the pattern requires the
        // parenthesis to follow `remember` directly.
        val unkeyed = copyControl()
            .filter { REMEMBER.containsMatchIn(it) }
            .filterNot { it.contains("remember(snapshot)") }

        assertTrue("these remembers are not keyed on the snapshot: $unkeyed", unkeyed.isEmpty())
    }

    @Test fun `the copy control cannot dismiss the panel`() {
        // The issue requires the modal to stay open. Structurally rather than by discipline: the
        // control is a separate composable and the dismissal callback is not among its parameters,
        // so there is nothing to call. Adding one is what this fails on.
        val slice = copyControl()

        assertTrue(
            "the copy control must not reach a dismissal callback: $slice",
            slice.none { it.contains("onClose") },
        )
    }

    @Test fun `confirmation follows the clipboard write`() {
        // Confirmation that appears whether or not the write happened is worse than none. A platform
        // failure must surface as a thrown exception in a debug build, not as a false `Copied`.
        val slice = copyControl()
        val write = slice.indexOfFirst { it.contains("setClipEntry(") }
        val confirm = slice.indexOfFirst { it.contains("onCopied()") }

        assertTrue(
            "expected both the clipboard write and the confirmation in the copy control",
            write >= 0 && confirm >= 0,
        )
        assertTrue("the confirmation must follow the write, not precede it", confirm > write)
    }

    @Test fun `the panel reads no browser state of its own`() {
        // The snapshot parameter is the panel's only input, and that is what stops one origin's
        // transport joining another's applied chrome in a document someone pastes into an issue.
        // Forbid the mechanisms, not one spelling of them.
        val offenders = executableLines(PANEL).filter { line -> FORBIDDEN.any(line::contains) }

        assertTrue("the inspector panel must read nothing but its snapshot:\n$offenders", offenders.isEmpty())
    }

    @Test fun `the slice actually found the copy control`() {
        // A source scan that silently matches nothing passes forever. Every rule above is scoped to
        // this slice, so a renamed composable must fail here rather than quietly disabling them.
        assertTrue(
            "the copy control's body was not located, so the rules above are vacuous",
            copyControl().size >= MIN_SLICE_LINES,
        )
    }

    private fun copyControl(): List<String> {
        val lines = executableLines(PANEL)
        val start = lines.indexOfFirst { it.startsWith("private fun $COPY_CONTROL(") }
        require(start >= 0) { "$COPY_CONTROL not found in ${PANEL.name}" }
        val end = lines.drop(start + 1).indexOfFirst { it == "}" }
        require(end >= 0) { "$COPY_CONTROL has no terminating brace at column 0" }
        return lines.subList(start, start + 1 + end)
    }

    private fun executableLines(file: File): List<String> = file.readLines()
        .map { it.trimEnd() }
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
        }

    private companion object {
        const val COPY_CONTROL = "InspectorCopyControl"
        const val MIN_SLICE_LINES = 10

        val SERIALIZER = source("InspectorJson.kt")
        val PANEL = source("SiteSkinInspectorPanel.kt")

        /**
         * Any `remember`, keyed or not.
         *
         * First written as `remember\(`, which matched a *wrongly* keyed remember and walked
         * straight past an **unkeyed** one — and unkeyed is the likelier mistake and the one the
         * issue's tab-change requirement is about. The control caught it: swapping
         * `remember(snapshot)` for `remember` left this test green.
         *
         * `rememberCoroutineScope()` is still not matched, because the brace or parenthesis has to
         * follow the word itself.
         */
        val REMEMBER = Regex("""\bremember\s*[({]""")

        val FORBIDDEN = listOf(
            "SiteSkinTraceRecorder",
            "inspectorRecorder",
            "BrowserState",
            "BrowserMode",
            ".latest(",
        )

        fun source(name: String): File =
            File("src/debug/java/app/webora/browser/inspector/$name")
                .also { require(it.isFile) { "$name not found at ${it.absolutePath}" } }
    }
}
