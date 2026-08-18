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
     * The rule is about *deciding*, not about touching. `DEVX-001`'s inspector legitimately holds a
     * `HubPresentation?` and prints its name — showing a site owner what was requested beside what
     * was composed is the whole reason the nullable holder exists, and displaying a value decides
     * nothing. Naming an enum **constant** is what a decision looks like, so that is what this
     * forbids outside the policy.
     *
     * The first version of this test banned the type instead and failed on the inspector, which was
     * correct code. A rule stated one level too coarse fails honest call sites and teaches people to
     * widen it; stated at the mechanism, it has nothing to widen.
     */
    @Test
    fun `only the policy branches on the hint`() {
        val constant = Regex("""HubPresentation\.[A-Z_]+""")
        val deciders = sourceRoots()
            .flatMap { it.walkTopDown().filter { file -> file.extension == "kt" } }
            .filter { constant.containsMatchIn(executableLines(it)) }
            .map { it.name }
            .toSet()

        assertEquals(
            "Only HubSurface.kt may branch on a hint value; every other surface takes a HubSurface",
            setOf("HubSurface.kt"),
            deciders,
        )
    }

    /**
     * And the policy is the only thing that turns a hint into a surface.
     *
     * A second function with the same signature would satisfy the constant rule above by delegating
     * once and then drifting. `resolveHubPresentation` and its one `hubSurface()` extension are the
     * whole seam.
     */
    @Test
    fun `the hint to surface mapping is declared once`() {
        val declarations = sourceRoots()
            .flatMap { it.walkTopDown().filter { file -> file.extension == "kt" } }
            .sumOf { file ->
                Regex("""fun\s+\w*[Hh]ub\w*\([^)]*HubPresentation""")
                    .findAll(executableLines(file)).count()
            }

        assertEquals("exactly one function maps a hint to a surface", 1, declarations)
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
