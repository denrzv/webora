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
