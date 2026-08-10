package app.webora.browser.browser

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conventions that browser-owned Compose sources must satisfy, checked by reading the sources.
 *
 * These are accessibility guarantees that no single call site owns. Written as prose in a review
 * checklist they hold exactly once and then decay — `HomeScreen` and `OnboardingScreen` grew their
 * own inline copy while `strings.xml` was already the rule. Written as a scan they hold for code
 * that does not exist yet, which is the only version worth having.
 *
 * The scanned set is **discovered, not listed**: every app source declaring `@Composable` is
 * subject to the rules. A registry of covered files would leave a new screen uncovered by default,
 * which is the failure mode these rules exist to prevent.
 */
class BrowserSurfaceConventionsTest {

    @Test
    fun `browser copy resolves from resources`() {
        val offenders = composableSources().flatMap { source ->
            source.violations(TEXT_LITERAL) { "passes a string literal to Text(" }
        }

        assertTrue(
            "Browser-owned copy must resolve from strings.xml so it is reviewable, localizable, " +
                "and readable by assistive technology in the user's language:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `accessible names resolve from resources`() {
        val offenders = composableSources().flatMap { source ->
            source.violations(NAMED_LITERAL) { "hard-codes a user-visible or accessible name" }
        }

        assertTrue(
            "A name, label, title, or contentDescription reaches assistive technology verbatim; " +
                "hard-coding one puts it outside review and outside translation:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the scan actually covers the compose surface`() {
        // A scan that silently matches nothing passes forever. Pin the floor so a broken source
        // path or a changed layout fails here rather than quietly disabling both rules above.
        val sources = composableSources()

        assertTrue("expected the app to declare composables; found ${sources.size}", sources.size >= MIN_SOURCES)
    }

    private fun File.violations(pattern: Regex, describe: () -> String): List<String> =
        readLines().mapIndexedNotNull { index, line ->
            if (pattern.containsMatchIn(line)) "$name:${index + 1} ${describe()}: ${line.trim()}" else null
        }

    private fun composableSources(): List<File> = sourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filter { it.readText().contains(COMPOSABLE_ANNOTATION) }
        .sortedBy(File::getName)
        .toList()

    private companion object {
        val sourceRoot: File = File(
            requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY)) {
                "$SOURCE_ROOT_PROPERTY is unset; app/build.gradle.kts must pass the app source root"
            },
        ).also { require(it.isDirectory) { "app source root is not a directory: $it" } }

        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
        const val COMPOSABLE_ANNOTATION = "@Composable"
        const val MIN_SOURCES = 6

        /** `Text("…")` and `Text(text = "…")`, the direct route from a literal to the screen. */
        val TEXT_LITERAL = Regex("""\bText\(\s*(text\s*=\s*)?"""")

        /** A literal bound to an argument whose value is read aloud or displayed as a name. */
        val NAMED_LITERAL = Regex("""\b(label|text|title|description|contentDescription)\s*=\s*"""")
    }
}
