package app.webora.browser.inspector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * A recorder, or nothing at all in a variant that has no panel to show its records.
 *
 * The gate is [SITESKIN_INSPECTOR_AVAILABLE], which is a `const val` declared in the variant source
 * set beside the panel. In the release variants it folds to `false` at compile time, so no recorder
 * is constructed and no record is ever built.
 */
internal fun inspectorRecorder(): SiteSkinTraceRecorder? =
    if (SITESKIN_INSPECTOR_AVAILABLE) SiteSkinTraceRecorder() else null

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
    return remember(recorder, record, state) {
        recorder?.let { inspectorSnapshot(state, record) }
    }
}
