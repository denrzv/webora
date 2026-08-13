package app.webora.browser.inspector

/**
 * Where a [ManifestTraceRecord] goes.
 *
 * A sink rather than a nullable recorder, and [None] rather than a null check, because the
 * discovery pipeline must behave identically whether or not anything is listening. A null check is
 * a branch, and a branch is somewhere for the traced and untraced paths to diverge.
 */
internal fun interface SiteSkinTraceSink {
    fun record(record: ManifestTraceRecord)

    companion object {
        /** Discards every record. The default in every production code path. */
        val None: SiteSkinTraceSink = SiteSkinTraceSink { }
    }
}

/**
 * Where a [BrandAssetTrace] goes, keyed by the origin whose manifest declared the logo.
 *
 * A separate sink rather than a second method on [SiteSkinTraceSink], for the same reason the two
 * pipelines have separate traces: manifest discovery decides whether a skin activates, and the asset
 * load runs after it has. Nothing observes both.
 *
 * [None] over a nullable recorder, as above — a null check is a branch, and a branch is somewhere for
 * the traced and untraced paths to diverge.
 */
internal fun interface BrandAssetTraceSink {
    fun record(origin: String, trace: BrandAssetTrace)

    companion object {
        /** Discards every trace. The default in every production code path. */
        val None: BrandAssetTraceSink = BrandAssetTraceSink { _, _ -> }
    }
}
