package app.webora.browser.siteskin

import android.graphics.Bitmap
import dev.siteskin.core.model.SiteSkinConfiguration
import kotlinx.coroutines.CancellationException

internal sealed interface BrandAsset {
    data class BitmapAsset(val bitmap: Bitmap) : BrandAsset
    data class Monogram(val text: String) : BrandAsset
}

/**
 * Which stage produced the published [BrandAsset].
 *
 * `NET-003` makes a monogram the correct output of every non-cancellation failure, and that is right
 * for the user: an unreachable logo must never break browsing. It is also why one glyph came to stand
 * for "no logo declared", "the CDN answered 404", "the bytes are not the type the header claimed",
 * "the image is larger than the cap" and "the decoder gave up" — five different things to fix, with
 * one indistinguishable symptom.
 *
 * This is the vocabulary that tells them apart. It is browser-owned and closed: a website can cause a
 * stage to be *selected* and can neither supply nor phrase one.
 */
internal enum class BrandAssetStage {
    /** A bitmap was decoded and published. */
    DECODED,

    /** The manifest declared no logo, so no request was made. */
    NOT_DECLARED,

    /** The server answered and the transport refused the answer. See [BrandAssetTrace.rejection]. */
    TRANSPORT_REJECTED,

    /** No answer arrived at all — an `IOException` or a timeout. */
    TRANSPORT_UNAVAILABLE,

    /** The bytes do not carry the signature of the media type the response declared. */
    SIGNATURE_MISMATCH,

    /** Bounds-only decoding could not read a size and a format from the bytes. */
    BOUNDS_UNREADABLE,

    /** The probed format disagreed with the declared one, or the dimensions exceed the caps. */
    BOUNDS_REFUSED,

    /** Bounds were accepted and full decoding still returned nothing. */
    DECODE_FAILED,

    /** Anything the stages above do not describe, so a new failure is never silently a known one. */
    UNEXPECTED_ERROR,
}

/**
 * What one brand-asset load did, for the developer inspector and the screenshot job's diagnostics.
 *
 * Observational throughout: nothing here re-enters a decision, which is what lets the browser record
 * freely without widening what it trusts. Every field is a number or a closed enum value, so no
 * response header, server message or URL can reach a display surface through it.
 *
 * [elapsedMillis] is here because it is the one field that separates a refusal from a load that
 * simply had not finished when something looked — the question `NET-004` could not answer from
 * anything the browser recorded.
 */
internal data class BrandAssetTrace(
    val stage: BrandAssetStage,
    val rejection: BrandAssetRejection? = null,
    val httpStatus: Int? = null,
    val redirects: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val elapsedMillis: Long = 0,
)

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
