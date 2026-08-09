package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal sealed interface ManifestDiscoveryOutcome {
    data class Available(val validation: SiteSkinValidationOutcome.Accepted) : ManifestDiscoveryOutcome
    data object Unavailable : ManifestDiscoveryOutcome
}

internal class ManifestDiscoveryCoordinator(
    private val scope: CoroutineScope,
    private val source: CacheableManifestSource,
    private val cache: ManifestCache = ManifestCache(),
    private val onOutcome: (ManifestDiscoveryOutcome) -> Unit,
) {
    private var discoveryJob: Job? = null

    fun onPageStarted(pageUrl: String) {
        val origin = SiteOrigin.parse(pageUrl)?.takeIf { it.scheme == HTTPS }
        discoveryJob?.cancel()
        if (origin == null) {
            onOutcome(ManifestDiscoveryOutcome.Unavailable)
            return
        }
        discoveryJob = scope.launch {
            val cached = cache.active(origin.canonical)
            val outcome = if (cached != null && cache.isFresh(cached)) {
                validate(cached.bytes, origin.canonical)
            } else {
                discover(origin.canonical, cached)
            }
            ensureActive()
            onOutcome(outcome)
        }
    }

    fun cancel() {
        discoveryJob?.cancel()
    }

    private suspend fun discover(origin: String, cached: CachedManifest?): ManifestDiscoveryOutcome {
        val validators = ManifestRequestValidators(cached?.metadata?.etag, cached?.metadata?.lastModified)
        return when (val result = source.fetch(origin, validators)) {
            is ManifestFetchResult.Fetched -> validateAndCache(result, origin)
            is ManifestFetchResult.NotModified -> reuseNotModified(result, cached, origin)
            ManifestFetchResult.Unavailable -> cached?.let { validate(it.bytes, origin) }
                ?: ManifestDiscoveryOutcome.Unavailable
            ManifestFetchResult.Rejected -> ManifestDiscoveryOutcome.Unavailable
        }
    }

    private fun validateAndCache(
        result: ManifestFetchResult.Fetched,
        origin: String,
    ): ManifestDiscoveryOutcome = when (val outcome = validate(result.bytes, origin)) {
        is ManifestDiscoveryOutcome.Available -> {
            val key = ManifestCacheKey(origin, outcome.validation.configuration.schemaVersion)
            cache.put(key, result.bytes, result.metadata)
            outcome
        }
        ManifestDiscoveryOutcome.Unavailable -> outcome
    }

    private fun reuseNotModified(
        result: ManifestFetchResult.NotModified,
        cached: CachedManifest?,
        origin: String,
    ): ManifestDiscoveryOutcome {
        if (cached == null) return ManifestDiscoveryOutcome.Unavailable
        val metadata = result.metadata.withFallback(cached.metadata)
        cache.refresh(cached, metadata)
        return validate(cached.bytes, origin)
    }

    private fun validate(bytes: ByteArray, origin: String): ManifestDiscoveryOutcome {
        return when (val validation = SiteSkinValidator.validate(bytes.inputStream(), origin)) {
            is SiteSkinValidationOutcome.Accepted -> ManifestDiscoveryOutcome.Available(validation)
            is SiteSkinValidationOutcome.Rejected -> ManifestDiscoveryOutcome.Unavailable
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
