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
    fun `browser controls use the touch target wrapper`() {
        // Material 3 Button applies defaultMinSize(minHeight = 40.dp) and, unlike Switch and
        // IconButton, never calls minimumInteractiveComponentSize(). Reaching it directly is
        // therefore a 40 dp target every time, which is why the raw component is out of bounds
        // rather than merely discouraged.
        val offenders = composableSources()
            .filterNot { it.readText().contains(TOUCH_TARGET_WRAPPER_DECLARATION) }
            .flatMap { source -> source.violations(RAW_BUTTON_IMPORT) { "imports a Material button directly" } }

        assertTrue(
            "Browser-owned controls must go through the touch-target wrapper so the 48 dp minimum " +
                "cannot be forgotten at a call site:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the scan actually covers the compose surface`() {
        // A scan that silently matches nothing passes forever. Pin the floor so a broken source
        // path or a changed layout fails here rather than quietly disabling the rules above. The
        // floor rises with each added source root; a root that stops contributing must fail.
        val sources = composableSources()

        assertTrue("expected the app to declare composables; found ${sources.size}", sources.size >= MIN_SOURCES)
    }

    @Test
    fun `every scanned source root actually contributes`() {
        // The global floor above cannot notice one root going quiet while the others grow. A root
        // that contributes nothing is a source set outside the gate, which is the whole reason the
        // scan was widened past src/main/java.
        val empty = sourceRoots.filter { root ->
            root.walkTopDown().none { file ->
                file.isFile && file.extension == "kt" && file.readText().contains(COMPOSABLE_ANNOTATION)
            }
        }

        assertTrue(
            "these scanned source roots declare no composable, so they are covered only in name: $empty",
            empty.isEmpty(),
        )
    }

    private fun File.violations(pattern: Regex, describe: () -> String): List<String> =
        readLines().mapIndexedNotNull { index, line ->
            if (pattern.containsMatchIn(line)) "$name:${index + 1} ${describe()}: ${line.trim()}" else null
        }

    private fun composableSources(): List<File> = sourceRoots
        .flatMap { root -> root.walkTopDown() }
        .filter { it.isFile && it.extension == "kt" }
        .filter { it.readText().contains(COMPOSABLE_ANNOTATION) }
        .sortedBy(File::getName)

    private companion object {
        /**
         * Every variant source root that can declare a composable, not just `src/main/java`.
         *
         * A debug-only screen is browser-owned UI too. Scanning one root would have made a variant
         * source set an escape hatch from the rule this scan exists to enforce — and `DEVX-001`
         * adds exactly such a screen. A missing root fails here rather than silently shrinking the
         * scan, which is the same reason the coverage floor below exists.
         */
        val sourceRoots: List<File> =
            requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY)) {
                "$SOURCE_ROOT_PROPERTY is unset; app/build.gradle.kts must pass the app source roots"
            }
                .split(File.pathSeparator)
                .map(::File)
                .onEach { require(it.isDirectory) { "app source root is not a directory: $it" } }

        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
        const val COMPOSABLE_ANNOTATION = "@Composable"
        const val MIN_SOURCES = 10

        /** `Text("…")` and `Text(text = "…")`, the direct route from a literal to the screen. */
        val TEXT_LITERAL = Regex("""\bText\(\s*(text\s*=\s*)?"""")

        /** A literal bound to an argument whose value is read aloud or displayed as a name. */
        val NAMED_LITERAL = Regex("""\b(label|text|title|description|contentDescription)\s*=\s*"""")

        /** The wrapper's own file is the one place the raw component may be reached. */
        const val TOUCH_TARGET_WRAPPER_DECLARATION = "fun WeboraButton("

        // Any Material button-like type, and any alias for one. The narrower spelling this
        // replaced named only Button and TextButton, so OutlinedButton, IconButton, an aliased
        // import, and the FloatingActionButton already in the tree all walked straight through it.
        val RAW_BUTTON_IMPORT = Regex("""^import androidx\.compose\.material3\.\w*Button( as \w+)?$""")
    }
}
