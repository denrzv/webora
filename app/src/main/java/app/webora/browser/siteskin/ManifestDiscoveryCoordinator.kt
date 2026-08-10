package app.webora.browser.siteskin

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

internal class ManifestDiscoveryCoordinator(
    private val scope: CoroutineScope,
    private val source: CacheableManifestSource,
    private val cache: ManifestCache = ManifestCache(),
    private val onOutcome: (ManifestDiscoveryOutcome) -> Unit,
) {
    private var discoveryJob: Job? = null

    fun onPageStarted(pageUrl: String, generation: Long = 0) {
        val origin = SiteOrigin.parse(pageUrl)?.takeIf { it.scheme == HTTPS }
        discoveryJob?.cancel()
        if (origin == null) {
            onOutcome(ManifestDiscoveryOutcome.Unavailable(null, generation))
            return
        }
        discoveryJob = scope.launch {
            val cached = cache.active(origin.canonical)
            val outcome = if (cached != null && cache.isFresh(cached)) {
                validate(cached.bytes, origin, generation)
            } else {
                discover(origin, generation, cached)
            }
            ensureActive()
            onOutcome(outcome)
        }
    }

    fun cancel() {
        discoveryJob?.cancel()
    }

    fun clearCache() = cache.clear()

    private suspend fun discover(
        origin: SiteOrigin,
        generation: Long,
        cached: CachedManifest?,
    ): ManifestDiscoveryOutcome {
        val validators = ManifestRequestValidators(cached?.metadata?.etag, cached?.metadata?.lastModified)
        return when (val result = source.fetch(origin.canonical, validators)) {
            is ManifestFetchResult.Fetched -> validateAndCache(result, origin, generation)
            is ManifestFetchResult.NotModified -> reuseNotModified(result, cached, origin, generation)
            ManifestFetchResult.Unavailable -> cached?.let { validate(it.bytes, origin, generation) }
                ?: ManifestDiscoveryOutcome.Unavailable(origin, generation)
            ManifestFetchResult.Rejected -> ManifestDiscoveryOutcome.Unavailable(origin, generation)
        }
    }

    private fun validateAndCache(
        result: ManifestFetchResult.Fetched,
        origin: SiteOrigin,
        generation: Long,
    ): ManifestDiscoveryOutcome = when (val outcome = validate(result.bytes, origin, generation)) {
        is ManifestDiscoveryOutcome.Available -> {
            val key = ManifestCacheKey(origin.canonical, outcome.validation.configuration.schemaVersion)
            cache.put(key, result.bytes, result.metadata)
            outcome
        }
        is ManifestDiscoveryOutcome.Unavailable -> outcome
    }

    private fun reuseNotModified(
        result: ManifestFetchResult.NotModified,
        cached: CachedManifest?,
        origin: SiteOrigin,
        generation: Long,
    ): ManifestDiscoveryOutcome {
        if (cached == null) return ManifestDiscoveryOutcome.Unavailable(origin, generation)
        val metadata = result.metadata.withFallback(cached.metadata)
        cache.refresh(cached, metadata)
        return validate(cached.bytes, origin, generation)
    }

    private fun validate(bytes: ByteArray, origin: SiteOrigin, generation: Long): ManifestDiscoveryOutcome {
        return when (val validation = SiteSkinValidator.validate(bytes.inputStream(), origin.canonical)) {
            is SiteSkinValidationOutcome.Accepted -> ManifestDiscoveryOutcome.Available(origin, generation, validation)
            is SiteSkinValidationOutcome.Rejected -> ManifestDiscoveryOutcome.Unavailable(origin, generation)
        }
    }

    private companion object {
        const val HTTPS = "https"
    }
}

private fun ManifestCacheMetadata.withFallback(fallback: ManifestCacheMetadata) = ManifestCacheMetadata(
    cacheControl = cacheControl ?: fallback.cacheControl,
    etag = etag ?: fallback.etag,
    lastModified = lastModified ?: fallback.lastModified,
)
