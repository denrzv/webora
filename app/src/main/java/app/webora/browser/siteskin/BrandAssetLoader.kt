package app.webora.browser.siteskin

import android.graphics.Bitmap
import app.webora.browser.inspector.BrandAssetStage
import app.webora.browser.inspector.BrandAssetTrace
import dev.siteskin.core.model.SiteSkinConfiguration
import kotlinx.coroutines.CancellationException

internal sealed interface BrandAsset {
    data class BitmapAsset(val bitmap: Bitmap) : BrandAsset
    data class Monogram(val text: String) : BrandAsset
}

/** The asset the browser will render, and the account of how it got there. */
internal data class BrandAssetOutcome(val asset: BrandAsset, val trace: BrandAssetTrace)

internal class BrandAssetLoader(
    private val source: BrandAssetSource,
    private val decoder: BrandAssetDecoder,
) {
    suspend fun load(configuration: SiteSkinConfiguration): BrandAssetOutcome {
        val startedAt = System.nanoTime()
        val fallback = BrandAsset.Monogram(
            brandMonogram(configuration.site.shortName, configuration.site.name),
        )
        val logoUrl = configuration.branding?.logoUrl
            ?: return outcome(fallback, BrandAssetTrace(BrandAssetStage.NOT_DECLARED), startedAt)
        val trace = try {
            loadRemote(configuration.origin, logoUrl)
        } catch (cancelled: CancellationException) {
            // Cancellation is lifecycle control, not a remote failure: a superseded load has no
            // outcome to publish and no stage to report.
            throw cancelled
        } catch (_: Exception) {
            Traced(null, BrandAssetTrace(BrandAssetStage.UNEXPECTED_ERROR))
        }
        return outcome(trace.asset ?: fallback, trace.trace, startedAt)
    }

    private suspend fun loadRemote(origin: String, logoUrl: String): Traced =
        when (val result = source.fetch(origin, logoUrl)) {
            is BrandAssetFetchResult.Fetched -> decodeFetched(result)
            is BrandAssetFetchResult.Rejected -> Traced(
                asset = null,
                trace = BrandAssetTrace(
                    stage = BrandAssetStage.TRANSPORT_REJECTED,
                    rejection = result.reason,
                    httpStatus = result.httpStatus,
                    redirects = result.redirects,
                ),
            )
            BrandAssetFetchResult.Unavailable -> Traced(
                asset = null,
                trace = BrandAssetTrace(BrandAssetStage.TRANSPORT_UNAVAILABLE),
            )
        }

    private suspend fun decodeFetched(fetched: BrandAssetFetchResult.Fetched): Traced {
        val transport = BrandAssetTrace(
            stage = BrandAssetStage.DECODED,
            httpStatus = fetched.httpStatus,
            redirects = fetched.redirects,
        )
        if (brandImageFormat(fetched.bytes) != fetched.format) {
            return Traced(null, transport.copy(stage = BrandAssetStage.SIGNATURE_MISMATCH))
        }
        val bounds = decoder.probe(fetched.bytes)
            ?: return Traced(null, transport.copy(stage = BrandAssetStage.BOUNDS_UNREADABLE))
        return decodeWithinBounds(fetched, transport.copy(width = bounds.width, height = bounds.height), bounds)
    }

    private suspend fun decodeWithinBounds(
        fetched: BrandAssetFetchResult.Fetched,
        measured: BrandAssetTrace,
        bounds: BrandImageBounds,
    ): Traced {
        if (!isAccepted(fetched.format, bounds)) {
            return Traced(null, measured.copy(stage = BrandAssetStage.BOUNDS_REFUSED))
        }
        val bitmap = decoder.decode(fetched.bytes)
            ?: return Traced(null, measured.copy(stage = BrandAssetStage.DECODE_FAILED))
        return Traced(BrandAsset.BitmapAsset(bitmap), measured)
    }

    private fun isAccepted(
        format: BrandImageFormat,
        bounds: BrandImageBounds,
    ): Boolean = bounds.format == format &&
        brandImageDimensionsAllowed(bounds.width, bounds.height)

    private fun outcome(asset: BrandAsset, trace: BrandAssetTrace, startedAt: Long) = BrandAssetOutcome(
        asset = asset,
        trace = trace.copy(elapsedMillis = (System.nanoTime() - startedAt) / NANOS_PER_MILLI),
    )

    /** A decoded asset when there is one, always beside the account of the attempt. */
    private data class Traced(val asset: BrandAsset?, val trace: BrandAssetTrace)

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
