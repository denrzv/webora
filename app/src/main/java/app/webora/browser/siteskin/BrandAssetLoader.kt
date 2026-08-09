package app.webora.browser.siteskin

import android.graphics.Bitmap
import dev.siteskin.core.model.SiteSkinConfiguration
import kotlinx.coroutines.CancellationException

internal sealed interface BrandAsset {
    data class BitmapAsset(val bitmap: Bitmap) : BrandAsset
    data class Monogram(val text: String) : BrandAsset
}

internal class BrandAssetLoader(
    private val source: BrandAssetSource,
    private val decoder: BrandAssetDecoder,
) {
    suspend fun load(configuration: SiteSkinConfiguration): BrandAsset {
        val fallback = BrandAsset.Monogram(
            brandMonogram(configuration.site.shortName, configuration.site.name),
        )
        val logoUrl = configuration.branding?.logoUrl ?: return fallback
        return try {
            loadRemote(configuration.origin, logoUrl) ?: fallback
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fallback
        }
    }

    private suspend fun loadRemote(origin: String, logoUrl: String): BrandAsset.BitmapAsset? {
        val fetched = source.fetch(origin, logoUrl) as? BrandAssetFetchResult.Fetched ?: return null
        return decodeFetched(fetched)
    }

    private suspend fun decodeFetched(fetched: BrandAssetFetchResult.Fetched): BrandAsset.BitmapAsset? {
        if (brandImageFormat(fetched.bytes) != fetched.format) return null
        val bounds = decoder.probe(fetched.bytes) ?: return null
        if (!isAccepted(fetched.format, bounds)) return null
        return decoder.decode(fetched.bytes)?.let(BrandAsset::BitmapAsset)
    }

    private fun isAccepted(
        format: BrandImageFormat,
        bounds: BrandImageBounds,
    ): Boolean = bounds.format == format &&
        brandImageDimensionsAllowed(bounds.width, bounds.height)
}
