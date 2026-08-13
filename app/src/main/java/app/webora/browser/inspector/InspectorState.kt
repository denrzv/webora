package app.webora.browser.inspector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * A recorder, or nothing at all in a variant that has no panel to show its records.
 *
 * The gate is [SITESKIN_INSPECTOR_AVAILABLE], which is a `const val` declared in the variant source
 * set beside the panel. In the release variants it folds to `false` at compile time, so no recorder
 * is constructed and no record is ever built.
 *
 * **One instance per process**, which is what [SiteSkinTraceRecorder]'s own documentation always
 * claimed — "a bounded, process-lifetime, in-memory store". It was constructed inside a `remember`,
 * so a configuration change discarded every trace a developer was mid-way through reading, and
 * instrumentation had no way to see what the running browser had decided. `NET-004` needed the
 * second of those: the hosted screenshot journey records the brand-asset outcome from *this*
 * recorder, so the diagnostics artifact reports what the browser did rather than what a second
 * loader would have done.
 */
internal fun inspectorRecorder(): SiteSkinTraceRecorder? = processRecorder

private val processRecorder: SiteSkinTraceRecorder? by lazy {
    if (SITESKIN_INSPECTOR_AVAILABLE) SiteSkinTraceRecorder() else null
}

/**
 * The panel's view of the current origin, recomputed when the browser state or the trace changes.
 *
 * [version] is the observation channel: the recorder is a plain class so the JVM gate can drive it,
 * which means Compose cannot subscribe to it. The caller increments a Compose-observable counter as
 * it forwards each record, and this reads it as a `remember` key.
 */
@Composable
internal fun rememberInspectorSnapshot(
    recorder: SiteSkinTraceRecorder?,
    version: Int,
    state: InspectorBrowserState,
): InspectorSnapshot? {
    val origin = state.origin?.canonical
    val record = remember(recorder, version, origin) { origin?.let { recorder?.latest(it) } }
    val brandAsset = remember(recorder, version, origin) { origin?.let { recorder?.latestBrandAsset(it) } }
    return remember(recorder, record, brandAsset, state) {
        recorder?.let { inspectorSnapshot(state, record, brandAsset) }
    }
}
