package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinLimits

internal data class ManifestCacheKey(
    val origin: String,
    val schemaVersion: String,
)

internal data class ManifestCacheMetadata(
    val cacheControl: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
)

internal data class CachedManifest(
    val key: ManifestCacheKey,
    val bytes: ByteArray,
    val metadata: ManifestCacheMetadata,
    val storedAtMillis: Long,
    val ttlMillis: Long,
) {
    fun isFresh(nowMillis: Long): Boolean =
        ttlMillis > 0 && nowMillis >= storedAtMillis && nowMillis - storedAtMillis < ttlMillis

    fun copyForCaller(): CachedManifest = copy(bytes = bytes.copyOf())
}

internal class ManifestCache(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val entries = mutableMapOf<ManifestCacheKey, CachedManifest>()
    private val activeByOrigin = mutableMapOf<String, ManifestCacheKey>()

    fun active(origin: String): CachedManifest? =
        activeByOrigin[origin]?.let(entries::get)?.copyForCaller()

    fun get(key: ManifestCacheKey): CachedManifest? = entries[key]?.copyForCaller()

    fun put(
        key: ManifestCacheKey,
        bytes: ByteArray,
        metadata: ManifestCacheMetadata,
    ): CachedManifest {
        activeByOrigin.put(key.origin, key)?.takeIf { it != key }?.let(entries::remove)
        val entry = CachedManifest(
            key = key,
            bytes = bytes.copyOf(),
            metadata = metadata,
            storedAtMillis = nowMillis(),
            ttlMillis = cacheTtlMillis(metadata.cacheControl),
        )
        entries[key] = entry
        return entry.copyForCaller()
    }

    fun refresh(entry: CachedManifest, metadata: ManifestCacheMetadata): CachedManifest =
        put(entry.key, entry.bytes, metadata)

    fun isFresh(entry: CachedManifest): Boolean = entry.isFresh(nowMillis())

    fun clear() {
        entries.clear()
        activeByOrigin.clear()
    }
}

internal fun cacheTtlMillis(cacheControl: String?): Long {
    val directives = cacheControl?.split(',')?.map(String::trim).orEmpty()
    val maxAges = directives.mapNotNull { directive ->
        val parts = directive.split('=', limit = 2)
        if (parts.size == 2 && parts[0].equals("max-age", ignoreCase = true)) parts[1] else null
    }
    if (maxAges.size != 1) return 0
    val seconds = maxAges.single().trim().removeSurrounding("\"").toLongOrNull() ?: return 0
    if (seconds <= 0) return 0
    return minOf(seconds, SiteSkinLimits.MAX_CACHE_TTL_SECONDS) * MILLIS_PER_SECOND
}

private const val MILLIS_PER_SECOND = 1_000L
