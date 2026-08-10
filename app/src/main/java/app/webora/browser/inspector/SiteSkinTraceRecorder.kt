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
) : SiteSkinTraceSink {

    private var records: Map<String, ManifestTraceRecord> = emptyMap()

    /** Incremented on every write so a Compose caller can key recomposition on it. */
    var version: Int = 0
        private set

    override fun record(record: ManifestTraceRecord) {
        val next = LinkedHashMap(records)
        next.remove(record.origin)
        next[record.origin] = record
        while (next.size > maxOrigins) {
            next.remove(next.keys.first())
        }
        records = next
        version += 1
    }

    fun latest(origin: String): ManifestTraceRecord? = records[origin]

    /** Traced origins, least recently recorded first. */
    fun origins(): List<String> = records.keys.toList()

    fun clear() {
        records = emptyMap()
        version += 1
    }

    private companion object {
        const val MAX_TRACED_ORIGINS = 8
    }
}
