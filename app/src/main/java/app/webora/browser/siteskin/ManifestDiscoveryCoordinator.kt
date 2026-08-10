package app.webora.browser.siteskin

import app.webora.browser.inspector.ManifestTraceRecord
import app.webora.browser.inspector.ManifestTransportTrace
import app.webora.browser.inspector.ManifestValidationTrace
import app.webora.browser.inspector.SiteSkinTraceSink
import app.webora.browser.inspector.TraceCacheState
import app.webora.browser.inspector.TraceDiagnostic
import app.webora.browser.inspector.TraceTransportOutcome
import app.webora.browser.inspector.TraceValidationResult
import dev.siteskin.core.ManifestDiagnostic
import dev.siteskin.core.SiteSkinSchema
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal sealed interface ManifestDiscoveryOutcome {
    val origin: SiteOrigin?
    val generation: Long

    data class Available(
        override val origin: SiteOrigin,
        override val generation: Long,
        val validation: SiteSkinValidationOutcome.Accepted,
    ) : ManifestDiscoveryOutcome

    data class Unavailable(
        override val origin: SiteOrigin?,
        override val generation: Long,
    ) : ManifestDiscoveryOutcome
}

/**
 * Runs manifest discovery for one navigation at a time and reports what it found.
 *
 * The [trace] sink is observational and defaults to discarding. Every branch below computes its
 * record from values it already had; nothing reads a record back, so discovery behaves identically
 * whether or not anything is listening. `SiteSkinTraceNeutralityTest` is what keeps that true.
 */
internal class ManifestDiscoveryCoordinator(
    private val scope: CoroutineScope,
    private val source: CacheableManifestSource,
    private val cache: ManifestCache = ManifestCache(),
    private val trace: SiteSkinTraceSink = SiteSkinTraceSink.None,
    private val onOutcome: (ManifestDiscoveryOutcome) -> Unit,
) {
    private var discoveryJob: Job? = null

    fun onPageStarted(pageUrl: String, generation: Long = 0) {
        val parsed = SiteOrigin.parse(pageUrl)
        val origin = parsed?.takeIf { it.scheme == HTTPS }
        discoveryJob?.cancel()
        if (origin == null) {
            // Only when the page parsed to *some* origin. A URL that parses to none leaves the
            // browser in Regular(null), so the panel has no key to look a record up under, and the
            // entry would sit in the bounded store unreachable.
            parsed?.let { trace.record(notEligible(it, generation)) }
            onOutcome(ManifestDiscoveryOutcome.Unavailable(null, generation))
            return
        }
        discoveryJob = scope.launch {
            val cached = cache.active(origin.canonical)
            val discovered = if (cached != null && cache.isFresh(cached)) {
                fromFreshCache(cached, origin, generation)
            } else {
                discover(origin, generation, cached)
            }
            // A superseded navigation publishes nothing — and records nothing. A trace written past
            // this point would describe an origin the browser has already left.
            ensureActive()
            trace.record(
                ManifestTraceRecord(
                    origin = origin.canonical,
                    generation = generation,
                    transport = discovered.transport,
                    validation = discovered.validation,
                ),
            )
            onOutcome(discovered.outcome)
        }
    }

    fun cancel() {
        discoveryJob?.cancel()
    }

    fun clearCache() = cache.clear()

    private fun fromFreshCache(
        cached: CachedManifest,
        origin: SiteOrigin,
        generation: Long,
    ): Discovered {
        val validated = validate(cached.bytes, origin, generation)
        return Discovered(
            validated.outcome,
            transport(origin, TraceTransportOutcome.CACHED, TraceCacheState.FRESH_HIT),
            validated.trace,
        )
    }

    private suspend fun discover(
        origin: SiteOrigin,
        generation: Long,
        cached: CachedManifest?,
    ): Discovered {
        val validators = ManifestRequestValidators(cached?.metadata?.etag, cached?.metadata?.lastModified)
        return when (val result = source.fetch(origin.canonical, validators)) {
            is ManifestFetchResult.Fetched -> fetched(result, origin, generation, cached)
            is ManifestFetchResult.NotModified -> notModified(result, cached, origin, generation)
            ManifestFetchResult.Unavailable -> unavailable(cached, origin, generation)
            is ManifestFetchResult.Rejected -> Discovered(
                ManifestDiscoveryOutcome.Unavailable(origin, generation),
                transport(
                    origin = origin,
                    outcome = TraceTransportOutcome.REJECTED,
                    cacheState = TraceCacheState.MISS,
                    httpStatus = result.httpStatus,
                    redirects = result.redirects,
                    rejection = result.reason,
                ),
                ManifestValidationTrace(TraceValidationResult.NOT_RUN),
            )
        }
    }

    private fun fetched(
        result: ManifestFetchResult.Fetched,
        origin: SiteOrigin,
        generation: Long,
        cached: CachedManifest?,
    ): Discovered {
        val validated = validate(result.bytes, origin, generation)
        val outcome = validated.outcome
        if (outcome is ManifestDiscoveryOutcome.Available) {
            val key = ManifestCacheKey(origin.canonical, outcome.validation.configuration.schemaVersion)
            cache.put(key, result.bytes, result.metadata)
        }
        return Discovered(
            outcome,
            transport(
                origin = origin,
                outcome = TraceTransportOutcome.FETCHED,
                cacheState = if (cached == null) TraceCacheState.MISS else TraceCacheState.REFETCHED,
                httpStatus = result.httpStatus,
                redirects = result.redirects,
            ),
            validated.trace,
        )
    }

    private fun notModified(
        result: ManifestFetchResult.NotModified,
        cached: CachedManifest?,
        origin: SiteOrigin,
        generation: Long,
    ): Discovered {
        if (cached == null) {
            return Discovered(
                ManifestDiscoveryOutcome.Unavailable(origin, generation),
                transport(
                    origin = origin,
                    outcome = TraceTransportOutcome.NOT_MODIFIED,
                    cacheState = TraceCacheState.MISS,
                    httpStatus = result.httpStatus,
                    redirects = result.redirects,
                ),
                ManifestValidationTrace(TraceValidationResult.NOT_RUN),
            )
        }
        cache.refresh(cached, result.metadata.withFallback(cached.metadata))
        val validated = validate(cached.bytes, origin, generation)
        return Discovered(
            validated.outcome,
            transport(
                origin = origin,
                outcome = TraceTransportOutcome.NOT_MODIFIED,
                cacheState = TraceCacheState.REVALIDATED,
                httpStatus = result.httpStatus,
                redirects = result.redirects,
            ),
            validated.trace,
        )
    }

    private fun unavailable(
        cached: CachedManifest?,
        origin: SiteOrigin,
        generation: Long,
    ): Discovered {
        if (cached == null) {
            return Discovered(
                ManifestDiscoveryOutcome.Unavailable(origin, generation),
                transport(origin, TraceTransportOutcome.UNAVAILABLE, TraceCacheState.MISS),
                ManifestValidationTrace(TraceValidationResult.NOT_RUN),
            )
        }
        val validated = validate(cached.bytes, origin, generation)
        return Discovered(
            validated.outcome,
            transport(origin, TraceTransportOutcome.UNAVAILABLE, TraceCacheState.STALE_REPLAYED),
            validated.trace,
        )
    }

    private fun validate(bytes: ByteArray, origin: SiteOrigin, generation: Long): Validated =
        when (val validation = SiteSkinValidator.validate(bytes.inputStream(), origin.canonical)) {
            is SiteSkinValidationOutcome.Accepted -> Validated(
                ManifestDiscoveryOutcome.Available(origin, generation, validation),
                ManifestValidationTrace(
                    result = TraceValidationResult.ACCEPTED,
                    schemaVersion = validation.configuration.schemaVersion,
                    diagnostics = validation.diagnostics.toTrace(),
                ),
            )
            is SiteSkinValidationOutcome.Rejected -> Validated(
                ManifestDiscoveryOutcome.Unavailable(origin, generation),
                // The rejecting diagnostics were computed and then dropped before DEVX-001. They
                // are the only place the browser can say *why* it fell back to regular mode.
                ManifestValidationTrace(
                    result = TraceValidationResult.REJECTED,
                    diagnostics = validation.diagnostics.toTrace(),
                ),
            )
        }

    private data class Discovered(
        val outcome: ManifestDiscoveryOutcome,
        val transport: ManifestTransportTrace,
        val validation: ManifestValidationTrace,
    )

    private data class Validated(
        val outcome: ManifestDiscoveryOutcome,
        val trace: ManifestValidationTrace,
    )

    private companion object {
        const val HTTPS = "https"

        fun transport(
            origin: SiteOrigin,
            outcome: TraceTransportOutcome,
            cacheState: TraceCacheState,
            httpStatus: Int? = null,
            redirects: Int = 0,
            rejection: FetchRejection? = null,
        ) = ManifestTransportTrace(
            manifestUrl = origin.canonical + SiteSkinSchema.WELL_KNOWN_PATH,
            outcome = outcome,
            cacheState = cacheState,
            httpStatus = httpStatus,
            redirects = redirects,
            rejection = rejection,
        )

        /**
         * A page that was never eligible still gets a record, keyed by the origin it parsed to.
         * "SiteSkin requires HTTPS" is an answer a developer needs, and an inspector that shows
         * nothing on an `http://` page looks broken rather than informative.
         */
        fun notEligible(parsed: SiteOrigin, generation: Long) = ManifestTraceRecord(
            origin = parsed.canonical,
            generation = generation,
            transport = ManifestTransportTrace(
                manifestUrl = "",
                outcome = TraceTransportOutcome.NOT_ELIGIBLE,
                cacheState = TraceCacheState.NOT_APPLICABLE,
                rejection = FetchRejection.NOT_HTTPS,
            ),
            validation = ManifestValidationTrace(TraceValidationResult.NOT_RUN),
        )
    }
}

private fun ManifestCacheMetadata.withFallback(fallback: ManifestCacheMetadata) = ManifestCacheMetadata(
    cacheControl = cacheControl ?: fallback.cacheControl,
    etag = etag ?: fallback.etag,
    lastModified = lastModified ?: fallback.lastModified,
)

private fun List<ManifestDiagnostic>.toTrace(): List<TraceDiagnostic> =
    map { TraceDiagnostic(it.code.value, it.pointer) }
