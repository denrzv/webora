package app.webora.browser.siteskin

import android.graphics.Bitmap
import app.webora.browser.inspector.BrandAssetStage
import app.webora.browser.inspector.BrandAssetTrace
import dev.siteskin.core.model.SiteSkinConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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
        val trace = attemptLoad(configuration.origin, logoUrl)
        return outcome(trace.asset ?: fallback, trace.trace, startedAt)
    }

    /**
     * Retries, and only the one outcome that is transient by definition.
     *
     * `NET-004`'s hosted run 16 recorded `TRANSPORT_UNAVAILABLE` after 891 ms because the device's
     * Wi-Fi dropped in the same second the user allowed SiteSkin. The refusal was correct; what was
     * not correct is that it was **permanent**. `BrowserScreen` keys this load on the trusted
     * configuration instance, and `BrowserState.forObservedOrigin` deliberately keeps that instance
     * across every same-origin page start — so nothing ever asked again. The network returned 6.4
     * seconds later and that origin showed a monogram for the rest of the visit, with the logo one
     * request away the whole time.
     *
     * Nothing else is retried, and the exclusions are the point:
     * - **A rejection is not retried.** The server answered and the browser declined the answer.
     *   Asking again would hammer a site whose logo legitimately 404s, and would change nothing.
     * - **A decode failure is not retried.** The same bytes decode the same way.
     * - **An undeclared logo is not retried.** There is nothing to request.
     *
     * The whole loop is inside the caller's coroutine, so a navigation away cancels it mid-backoff:
     * [kotlinx.coroutines.delay] is cancellable and the guard below rethrows rather than reporting a
     * stage. `NET-003`'s caps, allow-list and same-origin recheck are untouched — this changes how
     * many times the browser asks, never what it will accept.
     */
    private suspend fun attemptLoad(origin: String, logoUrl: String): Traced {
        var attempt = 1
        while (true) {
            val traced = try {
                loadRemote(origin, logoUrl)
            } catch (cancelled: CancellationException) {
                // Cancellation is lifecycle control, not a remote failure: a superseded load has no
                // outcome to publish and no stage to report.
                throw cancelled
            } catch (_: Exception) {
                Traced(null, BrandAssetTrace(BrandAssetStage.UNEXPECTED_ERROR))
            }
            val retryable = traced.trace.stage == BrandAssetStage.TRANSPORT_UNAVAILABLE
            if (!retryable || attempt == MAX_ATTEMPTS) return traced.withAttempts(attempt)
            delay(RETRY_DELAY_MILLIS * attempt)
            attempt += 1
        }
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

    private fun Traced.withAttempts(attempts: Int) = copy(trace = trace.copy(attempts = attempts))

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L

        /**
         * Three tries, 1 s then 2 s apart.
         *
         * Sized against what it exists to survive — run 16's network came back 6.4 s after it went
         * away — and against what it must not become: this runs once per activation, off the main
         * thread, behind an already-rendered monogram, so the user waits for nothing and a site that
         * is genuinely unreachable receives three requests rather than a stream of them.
         */
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
