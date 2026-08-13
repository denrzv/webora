package app.webora.browser.inspector

/**
 * A bounded, process-lifetime, in-memory store of the most recent trace per origin.
 *
 * Keyed by full canonical origin — the same key `ManifestCache` and `SiteConsentStore` use — so a
 * developer reading the panel is reading the same identity the browser looked the decision up
 * under, ports and scheme included.
 *
 * Nothing here is persisted, logged, shared or transmitted; `PRIV-001`'s zero-telemetry rule has no
 * developer-tooling exemption. [clear] is called by `BrowsingDataCleaner` alongside the manifest
 * cache and the stored consent decisions.
 *
 * Bounded at [MAX_TRACED_ORIGINS] and evicted by least-recently-recorded rather than first-seen: a
 * developer moving between a handful of origins wants the ones they just visited, not the ones they
 * started with.
 */
internal class SiteSkinTraceRecorder(
    private val maxOrigins: Int = MAX_TRACED_ORIGINS,
) : SiteSkinTraceSink, BrandAssetTraceSink {

    private var records: Map<String, ManifestTraceRecord> = emptyMap()

    /**
     * Brand-asset traces, in their own map rather than a field on [ManifestTraceRecord].
     *
     * The asset load runs *after* activation, on an origin whose manifest record already exists. A
     * field would mean merging into a record written by a different pipeline at a different time, and
     * a merge that silently no-ops when the record is absent loses exactly the case worth reading.
     * Two maps, one eviction rule.
     */
    private var brandAssets: Map<String, BrandAssetTrace> = emptyMap()

    /** Incremented on every write so a Compose caller can key recomposition on it. */
    var version: Int = 0
        private set

    override fun record(record: ManifestTraceRecord) {
        records = records.put(record.origin, record)
        version += 1
    }

    override fun record(origin: String, trace: BrandAssetTrace) {
        brandAssets = brandAssets.put(origin, trace)
        version += 1
    }

    fun latest(origin: String): ManifestTraceRecord? = records[origin]

    fun latestBrandAsset(origin: String): BrandAssetTrace? = brandAssets[origin]

    /** Traced origins, least recently recorded first. */
    fun origins(): List<String> = records.keys.toList()

    fun clear() {
        records = emptyMap()
        brandAssets = emptyMap()
        version += 1
    }

    /** Most-recently-recorded last, evicted from the front once [maxOrigins] is exceeded. */
    private fun <T> Map<String, T>.put(origin: String, value: T): Map<String, T> {
        val next = LinkedHashMap(this)
        next.remove(origin)
        next[origin] = value
        while (next.size > maxOrigins) {
            next.remove(next.keys.first())
        }
        return next
    }

    private companion object {
        const val MAX_TRACED_ORIGINS = 8
    }
}
