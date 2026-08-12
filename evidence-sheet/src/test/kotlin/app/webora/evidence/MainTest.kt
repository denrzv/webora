package app.webora.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * `tiles=N` is a contract, not a log line. The screenshot workflow compares it against the number of
 * PNGs the run actually collected and fails when the two disagree, so a composer that quietly drew
 * fewer tiles than there were frames cannot publish. That makes its exact shape worth asserting.
 */
class MainTest {

    @get:Rule val temp = TemporaryFolder()

    @Test fun printsOneTilesLineOnSuccess() {
        val dir = frames("01-home.png", "02-siteskin-consent.png", "03-siteskin-integrated.png")

        val result = runMain(dir.toString())

        assertEquals(0, result.status)
        assertEquals(listOf("tiles=3"), result.out.lines().filter { it.isNotBlank() })
    }

    @Test fun reportsNoTileCountWhenCompositionFails() {
        val empty = temp.newFolder("empty").toPath()

        val result = runMain(empty.toString())

        assertNotEquals(0, result.status)
        assertFalse("a failed run must not print a tile count", result.out.contains("tiles="))
        assertTrue(result.err.isNotBlank())
    }

    @Test fun rejectsMissingArgument() {
        val result = runMain()

        assertNotEquals(0, result.status)
        assertFalse(result.out.contains("tiles="))
        assertTrue(result.err.isNotBlank())
    }

    @Test fun rejectsExtraArguments() {
        val dir = frames("01-home.png")

        val result = runMain(dir.toString(), dir.toString())

        assertNotEquals(0, result.status)
        assertFalse(result.out.contains("tiles="))
    }

    // -- helpers ---------------------------------------------------------------------------------

    private class Result(val status: Int, val out: String, val err: String)

    /**
     * Drives the entry point in-process with captured streams. Spawning a JVM would test the Gradle
     * `application` wiring rather than this code, and would make the assertion on stdout depend on
     * whatever else a launcher decides to print.
     */
    private fun runMain(vararg args: String): Result {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val status = PrintStream(out, true).use { outStream ->
            PrintStream(err, true).use { errStream ->
                runContactSheetCommand(args.toList(), outStream, errStream)
            }
        }
        return Result(status, out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
    }

    private var folderSequence = 0

    private fun frames(vararg names: String): Path {
        val dir = temp.newFolder("main-${folderSequence++}").toPath()
        names.forEach { name ->
            val image = BufferedImage(540, 1200, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.color = Color.DARK_GRAY
            g.fillRect(0, 0, 540, 1200)
            g.dispose()
            ImageIO.write(image, "png", dir.resolve(name).toFile())
        }
        return dir
    }
}
