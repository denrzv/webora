package app.webora.browser.siteskin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BrandImageBounds(
    val width: Int,
    val height: Int,
    val format: BrandImageFormat?,
)

internal interface BrandAssetDecoder {
    suspend fun probe(bytes: ByteArray): BrandImageBounds?
    suspend fun decode(bytes: ByteArray): Bitmap?
}

internal class BitmapBrandAssetDecoder : BrandAssetDecoder {
    override suspend fun probe(bytes: ByteArray): BrandImageBounds? = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val format = BrandImageFormat.entries.singleOrNull { it.mediaType == options.outMimeType }
        BrandImageBounds(options.outWidth, options.outHeight, format)
            .takeIf { it.width > 0 && it.height > 0 && it.format != null }
    }

    override suspend fun decode(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
