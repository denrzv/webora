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
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * The composer draws a picture that a reviewer will treat as an account of what happened, so the
 * assertions here are mostly about what it must refuse to do. A sheet that silently drops a frame,
 * reorders one, or captions a tile with the wrong name is worse than no sheet: it is a green run
 * showing a journey that did not occur.
 */
class ContactSheetTest {

    @get:Rule val temp = TemporaryFolder()

    @Test fun composesOneTilePerFrameInFilenameOrder() {
        val dir = frames(
            "04-bloom-actions.png",
            "01-home.png",
            "06-google-regular.png",
            "03-bloom-storefront.png",
            "02-siteskin-consent.png",
            "05-bloom-profile.png",
        )

        assertEquals(6, composeContactSheet(dir))

        val sheet = ImageIO.read(dir.resolve(PREVIEW_FILE_NAME).toFile())
        assertEquals(expectedSheetWidth(6), sheet.width)
        canonicalFrames.forEachIndexed { index, name ->
            assertEquals(markers.getValue(name), tileMarkerColor(sheet, index))
            assertTrue("label band $index has no ink", labelInkPixels(sheet, index) > 0)
        }
    }

    @Test fun labelsAreDrawnAndNotBlank() {
        val dir = frames("01-home.png", "02-siteskin-consent.png")

        composeContactSheet(dir)

        val sheet = ImageIO.read(dir.resolve(PREVIEW_FILE_NAME).toFile())
        repeat(2) { index ->
            assertTrue(
                "label band $index has no ink — the font rendered nothing",
                labelInkPixels(sheet, index) > 0,
            )
        }
    }

    @Test fun labelComesFromTheFileItDraws() {
        val first = frames("01-home.png", "02-siteskin-consent.png")
        composeContactSheet(first)
        val before = ImageIO.read(first.resolve(PREVIEW_FILE_NAME).toFile())

        val second = frames("01-home.png", "02-siteskin-consent-renamed.png")
        composeContactSheet(second)
        val after = ImageIO.read(second.resolve(PREVIEW_FILE_NAME).toFile())

        assertEquals(labelInkPixels(before, 0), labelInkPixels(after, 0))
        assertNotEquals(labelInkPixels(before, 1), labelInkPixels(after, 1))
    }

    @Test fun refusesADirectoryWithNoFrames() {
        val dir = temp.newFolder("empty").toPath()

        val failure = runCatching { composeContactSheet(dir) }.exceptionOrNull()

        assertTrue("expected a failure, got none", failure is ContactSheetFailure)
        assertFalse(dir.resolve(PREVIEW_FILE_NAME).toFile().exists())
    }

    @Test fun refusesAMissingDirectoryAndNamesTheResolvedPath() {
        val missing = temp.root.toPath().resolve("never-created")

        val failure = runCatching { composeContactSheet(missing) }.exceptionOrNull()

        assertTrue("expected a failure, got none", failure is ContactSheetFailure)
        val message = failure?.message.orEmpty()
        assertTrue(
            "message must name an absolute path, got: $message",
            message.contains(missing.toAbsolutePath().toString()),
        )
    }

    @Test fun namesTheResolvedPathWhenARelativeDirectoryIsEmpty() {
        val relative = java.nio.file.Path.of("definitely-not-here-" + System.nanoTime())

        val failure = runCatching { composeContactSheet(relative) }.exceptionOrNull()

        assertTrue("expected a failure, got none", failure is ContactSheetFailure)
        assertTrue(
            "a relative argument must be reported as the absolute path it resolved to",
            failure?.message.orEmpty().contains(relative.toAbsolutePath().toString()),
        )
    }

    @Test fun refusesAnUndecodablePng() {
        val dir = frames("01-home.png", "02-siteskin-consent.png")
        dir.resolve("03-bloom-storefront.png").toFile().writeText("not a PNG at all")

        val failure = runCatching { composeContactSheet(dir) }.exceptionOrNull()

        assertTrue("expected a failure, got none", failure is ContactSheetFailure)
        assertFalse(dir.resolve(PREVIEW_FILE_NAME).toFile().exists())
    }

    @Test fun excludesAnExistingPreviewFromItsOwnInput() {
        val dir = frames("01-home.png", "02-siteskin-consent.png")

        assertEquals(2, composeContactSheet(dir))
        assertTrue(dir.resolve(PREVIEW_FILE_NAME).toFile().exists())
        assertEquals(2, composeContactSheet(dir))

        val sheet = ImageIO.read(dir.resolve(PREVIEW_FILE_NAME).toFile())
        assertEquals(expectedSheetWidth(2), sheet.width)
    }

    @Test fun aFailedCompositionLeavesNoStaleSheet() {
        val dir = frames("01-home.png", "02-siteskin-consent.png")
        assertEquals(2, composeContactSheet(dir))
        assertTrue(dir.resolve(PREVIEW_FILE_NAME).toFile().exists())

        dir.resolve("03-bloom-storefront.png").toFile().writeText("not a PNG at all")
        val failure = runCatching { composeContactSheet(dir) }.exceptionOrNull()

        assertTrue("expected a failure, got none", failure is ContactSheetFailure)
        assertFalse(
            "the sheet from the previous composition survived a refusal",
            dir.resolve(PREVIEW_FILE_NAME).toFile().exists(),
        )
    }

    @Test fun labelsAreClippedToTheirOwnTile() {
        val longName = "01-" + "wide".repeat(30) + ".png"
        val dir = temp.newFolder("clip").toPath()
        writeFrame(dir.resolve(longName), width = 540, height = 1200, marker = Color.RED)
        writeFrame(dir.resolve("02-short.png"), width = 540, height = 1200, marker = Color.GREEN)

        composeContactSheet(dir)

        val sheet = ImageIO.read(dir.resolve(PREVIEW_FILE_NAME).toFile())
        val background = sheet.getRGB(1, 1)
        val bandTop = sheet.height - PADDING - LABEL_BAND
        var gutterInk = 0
        for (y in bandTop until bandTop + LABEL_BAND) {
            for (x in tileColumnLeft(0) + TILE_WIDTH until tileColumnLeft(1)) {
                if (sheet.getRGB(x, y) != background) gutterInk++
            }
        }
        assertEquals("a caption overran its tile into the gutter", 0, gutterInk)
    }

    @Test fun preservesAspectRatio() {
        val dir = temp.newFolder("aspect").toPath()
        writeFrame(dir.resolve("01-home.png"), width = 1080, height = 2400, marker = Color.RED)

        composeContactSheet(dir)

        val sheet = ImageIO.read(dir.resolve(PREVIEW_FILE_NAME).toFile())
        val expectedTileHeight = TILE_WIDTH * 2400 / 1080
        assertEquals(PADDING + expectedTileHeight + LABEL_BAND + PADDING, sheet.height)
    }

    private val markers = mapOf(
        "01-home.png" to Color.RED,
        "02-siteskin-consent.png" to Color.GREEN,
        "02-siteskin-consent-renamed.png" to Color.GREEN,
        "03-bloom-storefront.png" to Color.BLUE,
        "04-bloom-actions.png" to Color.MAGENTA,
        "05-bloom-profile.png" to Color.CYAN,
        "06-google-regular.png" to Color.ORANGE,
    )

    private val canonicalFrames = listOf(
        "01-home.png",
        "02-siteskin-consent.png",
        "03-bloom-storefront.png",
        "04-bloom-actions.png",
        "05-bloom-profile.png",
        "06-google-regular.png",
    )

    private var folderSequence = 0

    private fun frames(vararg names: String): Path {
        val dir = temp.newFolder("frames-${folderSequence++}").toPath()
        names.forEach { name ->
            writeFrame(dir.resolve(name), width = 540, height = 1200, marker = markers.getValue(name))
        }
        return dir
    }

    private fun writeFrame(path: Path, width: Int, height: Int, marker: Color) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = marker
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(image, "png", path.toFile())
    }

    private fun expectedSheetWidth(tiles: Int) = PADDING + tiles * (TILE_WIDTH + PADDING)

    private fun tileColumnLeft(index: Int) = PADDING + index * (TILE_WIDTH + PADDING)

    private fun tileMarkerColor(sheet: BufferedImage, index: Int): Color =
        Color(sheet.getRGB(tileColumnLeft(index) + TILE_WIDTH / 2, PADDING + TILE_WIDTH / 2))

    private fun labelInkPixels(sheet: BufferedImage, index: Int): Int {
        val bandTop = sheet.height - PADDING - LABEL_BAND
        val background = sheet.getRGB(1, 1)
        var ink = 0
        for (y in bandTop until bandTop + LABEL_BAND) {
            for (x in tileColumnLeft(index) until tileColumnLeft(index) + TILE_WIDTH) {
                if (sheet.getRGB(x, y) != background) ink++
            }
        }
        return ink
    }
}
