package app.webora.browser.inspector

import androidx.compose.runtime.Composable

/**
 * The release-variant inspector: there isn't one.
 *
 * Availability is declared here, beside the host, rather than read from `BuildConfig.DEBUG`. AGP
 * derives that flag from the build type's `isDebuggable`, and `debugRelease` sets it — so gating on
 * it would collect trace data in a variant whose source set is this file, with no panel to display
 * it. A constant that travels with the panel cannot disagree with the panel.
 *
 * `debugRelease` compiles this same file: `app/build.gradle.kts` adds `src/release/java` to its
 * source set, because `initWith(release)` copies build-type configuration and not sources. One
 * shared stub rather than two that can drift.
 *
 * `assertInspectorAbsentFromReleaseVariants` checks the compiled output of both variants for the
 * panel's absence and for this file's presence.
 */
internal const val SITESKIN_INSPECTOR_AVAILABLE: Boolean = false

@Composable
internal fun SiteSkinInspectorHost(snapshot: InspectorSnapshot?, open: Boolean, onClose: () -> Unit) {
    // Nothing. The caller has already skipped assembling `snapshot`, which is always null here, and
    // `browserMenuCommands()` never offers the entry that would set `open`.
}
