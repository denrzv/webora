package app.webora.evidence

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.io.path.name

/** The sheet is written beside its inputs, so this name is also excluded from discovery. */
const val PREVIEW_FILE_NAME: String = "preview.png"

const val TILE_WIDTH: Int = 360
const val LABEL_BAND: Int = 48
const val PADDING: Int = 16

private const val LABEL_FONT_SIZE = 22
private val SHEET_BACKGROUND = Color(0x1C, 0x1F, 0x26)
private val LABEL_INK = Color(0xED, 0xEE, 0xF2)

/** Every refusal is this type, so a caller can tell a composition failure from a bug. */
class ContactSheetFailure(message: String) : RuntimeException(message)

/**
 * Draws every canonical frame in [directory] into one labelled `preview.png`, and returns the number
 * of tiles drawn.
 *
 * **This function makes a claim about what happened during a run**, which is what shapes its error
 * handling: it is total or it throws. A frame that cannot be read is never skipped, because a sheet
 * missing a tile still looks like a complete journey to the person holding it. The caller compares
 * the returned count against the number of screenshots the run actually collected, so a discrepancy
 * fails the run rather than being published.
 *
 * There is deliberately **no parameter for a title, a caption or a label**. A tile's label is derived
 * from that tile's own path, inside the same loop iteration that draws it, so the two cannot drift
 * and no text from the workflow, the page under test, or a SiteSkin manifest can reach the image.
 * The frames depict manifest-driven UI; nothing manifest-driven may caption Webora's own evidence.
 * Adding such a parameter is the violation this design exists to prevent.
 *
 * Order is filename order, which is journey order by construction — the capturing test names frames
 * `01-`, `02-`, `03-`. Sorting the discovered files means there is no second list of frame names to
 * fall out of step with the first.
 */
fun composeContactSheet(directory: Path): Int {
    val frames = discoverFrames(directory)
    val images = frames.map { frame ->
        val image = runCatching { ImageIO.read(frame.toFile()) }.getOrNull()
            ?: throw ContactSheetFailure("Not a readable image: $frame")
        frame to image
    }

    val tiles = images.map { (frame, image) -> Tile(frame.name, image) }
    val sheet = drawSheet(tiles)

    val output = directory.resolve(PREVIEW_FILE_NAME)
    if (!ImageIO.write(sheet, "png", output.toFile())) {
        throw ContactSheetFailure("No PNG writer accepted the contact sheet")
    }
    return tiles.size
}

private class Tile(val label: String, val image: BufferedImage) {
    /** Scaled to a fixed width; the height follows, because evidence is never stretched to fit. */
    val height: Int = (TILE_WIDTH.toLong() * image.height / image.width).toInt().coerceAtLeast(1)
}

private fun discoverFrames(directory: Path): List<Path> {
    // Absolute, always. `Not a directory: review` cost a whole hosted run to diagnose,
    // because the interesting part was not the name — it was which `review` the process
    // had actually looked in. A path failure should name the path it resolved to.
    if (!Files.isDirectory(directory)) {
        throw ContactSheetFailure("Not a directory: ${directory.toAbsolutePath()}")
    }
    val frames = Files.list(directory).use { paths ->
        paths.filter { Files.isRegularFile(it) }
            .filter { it.extension.equals("png", ignoreCase = true) }
            .filter { it.name != PREVIEW_FILE_NAME }
            .map { it }
            .sorted(compareBy { it.name })
            .toList()
    }
    if (frames.isEmpty()) {
        throw ContactSheetFailure("No frames to compose in ${directory.toAbsolutePath()}")
    }
    return frames
}

private fun drawSheet(tiles: List<Tile>): BufferedImage {
    val tallest = tiles.maxOf { it.height }
    val width = PADDING + tiles.size * (TILE_WIDTH + PADDING)
    val height = PADDING + tallest + LABEL_BAND + PADDING

    val sheet = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = sheet.createGraphics()
    try {
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
        )
        g.color = SHEET_BACKGROUND
        g.fillRect(0, 0, width, height)

        g.font = Font(Font.SANS_SERIF, Font.PLAIN, LABEL_FONT_SIZE)
        val metrics = g.fontMetrics
        val labelBaseline = height - PADDING - LABEL_BAND + metrics.ascent

        tiles.forEachIndexed { index, tile ->
            val left = PADDING + index * (TILE_WIDTH + PADDING)
            g.drawImage(tile.image, left, PADDING, TILE_WIDTH, tile.height, null)
            g.color = LABEL_INK
            g.drawString(tile.label, left, labelBaseline)
        }
    } finally {
        g.dispose()
    }
    return sheet
}
