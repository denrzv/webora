package app.webora.browser.inspector

import androidx.compose.runtime.Composable

/**
 * The debug-variant inspector.
 *
 * Availability is declared here, beside the panel, rather than read from `BuildConfig.DEBUG`. AGP
 * derives that flag from the build type's `isDebuggable`, and `debugRelease` sets it — so gating on
 * it would collect trace data in a variant compiled against the release stub, with no panel to show
 * it. A constant that travels with the panel cannot disagree with the panel.
 *
 * The host renders the panel and nothing else. It used to also draw a permanent floating affordance
 * over every screen, which put a developer tool in Webora's own canonical evidence and — because the
 * overlay's pixels land inside the region `CI-003` measures for drawn page content — let browser
 * chrome stand in for a page that never painted. `DEVX-003` moved the affordance into the two
 * browser menus that already exist, one per mode. The affordance is absent from a frame because it
 * is not composed, never because anything suppressed it for the camera.
 */
internal const val SITESKIN_INSPECTOR_AVAILABLE: Boolean = true

@Composable
internal fun SiteSkinInspectorHost(snapshot: InspectorSnapshot?, open: Boolean, onClose: () -> Unit) {
    if (snapshot == null || !open) return
    SiteSkinInspectorPanel(snapshot, onClose)
}
