package app.webora.browser.siteskin

import dev.siteskin.core.model.HubPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The hint→surface policy, and the assertion that there is only one of it.
 *
 * The value cases are cheap and would pass over a hardcoded `AUTO -> DRAWER` written at a call
 * site, which is why they are not the whole test. `AUTO` resolving to the drawer *today* is a
 * policy that will change; a copy of it living beside a composable is a second answer nobody will
 * remember to update. The source scan is what forbids the copy.
 */
class HubSurfaceTest {
    @Test
    fun `every hint including null resolves to a surface`() {
        assertEquals(HubSurface.BOUQUET, resolveHubPresentation(HubPresentation.BOUQUET))
        assertEquals(HubSurface.DRAWER, resolveHubPresentation(HubPresentation.DRAWER))
        assertEquals(HubSurface.DRAWER, resolveHubPresentation(HubPresentation.AUTO))
        assertEquals(HubSurface.DRAWER, resolveHubPresentation(null))
    }

    /**
     * A total function that returned one constant would satisfy every value assertion above.
     *
     * This is the guard against that: the policy must be able to produce both surfaces, so a
     * regression that made the drawer unconditional — the tempting simplification once `AUTO`
     * already resolves to it — fails here rather than silently removing the site's only influence
     * over this choice.
     */
    @Test
    fun `the policy can produce both surfaces`() {
        assertEquals(
            HubSurface.entries.toSet(),
            HubPresentation.entries.map(::resolveHubPresentation).toSet(),
        )
    }

    /**
     * A site's hint is a preference, so an explicit `bouquet` must survive the policy unchanged.
     *
     * Stated separately from the value table because it is the half a future device or
     * accessibility override would be tempted to break, and the review reading that change should
     * find an assertion naming what is being reversed.
     */
    @Test
    fun `an explicit bouquet is honoured rather than redirected`() {
        assertEquals(HubSurface.BOUQUET, resolveHubPresentation(HubPresentation.BOUQUET))
    }

    /**
     * `AUTO` routes through the policy; no call site compiles its own default.
     *
     * `resolveHubPresentation` and `hubSurface` are the only executable references to
     * `HubPresentation` in the app. Anything else reading the enum is a second reader deciding for
     * itself what an absent hint means, which is exactly the drift this function exists to
     * prevent — and the failure mode is silent, because both copies agree on the day the second
     * one is written.
     */
    @Test
    fun `the hint is read in exactly one file`() {
        val readers = sourceRoots()
            .flatMap { it.walkTopDown().filter { file -> file.extension == "kt" } }
            .filter { "HubPresentation" in executableLines(it) }
            .map { it.name }
            .toSet()

        assertEquals(
            "Only the policy may read core's hint enum; every other surface takes a HubSurface",
            setOf("HubSurface.kt"),
            readers,
        )
    }

    /**
     * The policy is pure: no `Context`, no configuration, no callback, no composable.
     *
     * `UX-013` keeps `ExpressiveSiteSkinPresentation` to colour and motion, and a surface choice is
     * a fourth kind of thing that must not be folded into it. If this decision ever needs a
     * platform fact, it arrives as an explicit parameter the JVM gate can drive — the shape
     * `UX-014` used for animator duration scale — never as a read performed inside the function.
     */
    @Test
    fun `the policy takes no platform input`() {
        val source = executableLines(policyFile())

        assertTrue("the scan must see real code", "fun resolveHubPresentation(" in source)
        listOf("Context", "@Composable", "LocalConfiguration", "Resources", "System.").forEach {
            assertTrue("$it has no place in a pure policy", it !in source)
        }
    }

    private companion object {
        fun policyFile(): File =
            File("src/main/java/app/webora/browser/siteskin/HubSurface.kt").also {
                check(it.exists()) { "policy source not found at ${it.absolutePath}" }
            }

        fun sourceRoots(): List<File> = listOf(
            File("src/main/java"),
            File("src/debug/java"),
            File("src/release/java"),
        ).onEach { check(it.isDirectory) { "missing source root ${it.absolutePath}" } }

        /**
         * `BROWSE-009`: a source scan reads executable lines, never `readText()`, or the prose will
         * satisfy or violate the rule on the code's behalf. This file is the sharpest case of that
         * trap in the repository so far — every KDoc here names `HubPresentation`, so an unstripped
         * scan would report every documented file as a reader.
         */
        fun executableLines(file: File): String = file.readLines()
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
            }
            .joinToString("\n")
    }
}
