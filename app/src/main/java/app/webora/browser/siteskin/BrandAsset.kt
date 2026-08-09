package app.webora.browser.siteskin

internal object BrandAssetLimits {
    const val MAX_BYTES: Int = 512 * 1024
    const val MAX_AXIS_PIXELS: Int = 1_024
    const val MAX_PIXELS: Long = 1_048_576
}

internal enum class BrandImageFormat(val mediaType: String) {
    PNG("image/png"),
    WEBP("image/webp"),
}

internal fun brandImageFormat(bytes: ByteArray): BrandImageFormat? = when {
    bytes.startsWith(PNG_SIGNATURE) -> BrandImageFormat.PNG
    bytes.startsWith(WEBP_RIFF) && bytes.hasBytesAt(WEBP_MARKER, WEBP_MARKER_OFFSET) -> BrandImageFormat.WEBP
    else -> null
}

internal fun brandImageDimensionsAllowed(width: Int, height: Int): Boolean =
    width in 1..BrandAssetLimits.MAX_AXIS_PIXELS &&
        height in 1..BrandAssetLimits.MAX_AXIS_PIXELS &&
        width.toLong() * height.toLong() <= BrandAssetLimits.MAX_PIXELS

internal fun brandMonogram(shortName: String?, name: String): String =
    (shortName?.takeIf(String::isNotBlank) ?: name)
        .trim()
        .codePoints()
        .findFirst()
        .orElse(DEFAULT_MONOGRAM_CODE_POINT)
        .let(Character::toChars)
        .concatToString()
        .uppercase()

private fun ByteArray.startsWith(prefix: ByteArray): Boolean = hasBytesAt(prefix, 0)

private fun ByteArray.hasBytesAt(expected: ByteArray, offset: Int): Boolean =
    size >= offset + expected.size && expected.indices.all { this[offset + it] == expected[it] }

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)
private val WEBP_RIFF = "RIFF".toByteArray()
private val WEBP_MARKER = "WEBP".toByteArray()
private const val WEBP_MARKER_OFFSET = 8
private const val DEFAULT_MONOGRAM_CODE_POINT = 0x2022
