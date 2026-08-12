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
import java.io.File
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
        val dir = frames("03-siteskin-integrated.png", "01-home.png", "02-siteskin-consent.png")

        assertEquals(3, composeContactSheet(dir))

        val sheet = ImageIO.read(dir.resolve(PREVIEW_FILE_NAME).toFile())
        assertEquals(expectedSheetWidth(3), sheet.width)
        // Discovery is sorted, not argument- or filesystem-ordered: the tile drawn from the frame
        // whose marker colour is unique to 01- must be the leftmost one.
        assertEquals(markers.getValue("01-home.png"), tileMarkerColor(sheet, 0))
        assertEquals(markers.getValue("02-siteskin-consent.png"), tileMarkerColor(sheet, 1))
        assertEquals(markers.getValue("03-siteskin-integrated.png"), tileMarkerColor(sheet, 2))
    }

    /**
     * A host with no usable font still writes a structurally perfect PNG — with invisible labels.
     * Counting ink in the label band is the only assertion that separates "wrote a file" from
     * "drew the text", and an unlabelled contact sheet cannot tell a reviewer what they are seeing.
     */
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

    /** The caption comes from the tile's own file, so the two cannot drift apart. */
    @Test fun labelComesFromTheFileItDraws() {
        val first = frames("01-home.png", "02-siteskin-consent.png")
        composeContactSheet(first)
        val before = ImageIO.read(first.resolve(PREVIEW_FILE_NAME).toFile())

        // Same pixels, same position, different name. Only the second label may change.
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

    @Test fun refusesAMissingDirectory() {
        val missing = temp.root.toPath().resolve("never-created")

        val failure = runCatching { composeContactSheet(missing) }.exceptionOrNull()

        assertTrue("expected a failure, got none", failure is ContactSheetFailure)
    }

    /**
     * The frame is there and is named like evidence, but it is not an image. Skipping it would
     * publish a sheet one tile short of the journey it claims to show, so it is fatal.
     */
    @Test fun refusesAnUndecodablePng() {
        val dir = frames("01-home.png", "02-siteskin-consent.png")
        dir.resolve("03-siteskin-integrated.png").toFile().writeText("not a PNG at all")

        val failure = runCatching { composeContactSheet(dir) }.exceptionOrNull()

        assertTrue("expected a failure, got none", failure is ContactSheetFailure)
        assertFalse(dir.resolve(PREVIEW_FILE_NAME).toFile().exists())
    }

    /** The sheet is written beside its inputs, so it must never become one of them. */
    @Test fun excludesAnExistingPreviewFromItsOwnInput() {
        val dir = frames("01-home.png", "02-siteskin-consent.png")

        assertEquals(2, composeContactSheet(dir))
        assertTrue(dir.resolve(PREVIEW_FILE_NAME).toFile().exists())
        assertEquals(2, composeContactSheet(dir))

        val sheet = ImageIO.read(dir.resolve(PREVIEW_FILE_NAME).toFile())
        assertEquals(expectedSheetWidth(2), sheet.width)
    }

    /** Evidence is never stretched to fit a slot. */
    @Test fun preservesAspectRatio() {
        val dir = temp.newFolder("aspect").toPath()
        writeFrame(dir.resolve("01-home.png"), width = 1080, height = 2400, marker = Color.RED)

        composeContactSheet(dir)

        val sheet = ImageIO.read(dir.resolve(PREVIEW_FILE_NAME).toFile())
        val expectedTileHeight = TILE_WIDTH * 2400 / 1080
        assertEquals(PADDING + expectedTileHeight + LABEL_BAND + PADDING, sheet.height)
    }

    // -- helpers ---------------------------------------------------------------------------------

    /** Distinct fill colours so a tile can be traced back to the frame it was drawn from. */
    private val markers = mapOf(
        "01-home.png" to Color.RED,
        "02-siteskin-consent.png" to Color.GREEN,
        "02-siteskin-consent-renamed.png" to Color.GREEN,
        "03-siteskin-integrated.png" to Color.BLUE,
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
        ImageIO.write(image, "png", path.toFile() as File)
    }

    private fun expectedSheetWidth(tiles: Int) = PADDING + tiles * (TILE_WIDTH + PADDING)

    private fun tileColumnLeft(index: Int) = PADDING + index * (TILE_WIDTH + PADDING)

    /** A pixel from the middle of a tile's image area, which is a flat marker colour. */
    private fun tileMarkerColor(sheet: BufferedImage, index: Int): Color =
        Color(sheet.getRGB(tileColumnLeft(index) + TILE_WIDTH / 2, PADDING + TILE_WIDTH / 2))

    /**
     * Pixels in a tile's label band that differ from the sheet background. The band sits directly
     * under that tile's image, so its top is the tallest tile's bottom edge.
     */
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
