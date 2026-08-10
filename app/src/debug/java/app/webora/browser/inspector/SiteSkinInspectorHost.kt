package app.webora.browser.inspector

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.webora.browser.R
import app.webora.browser.browser.WeboraFloatingActionButton

/**
 * The debug-variant inspector.
 *
 * Availability is declared here, beside the panel, rather than read from `BuildConfig.DEBUG`. AGP
 * derives that flag from the build type's `isDebuggable`, and `debugRelease` sets it — so gating on
 * it would collect trace data in a variant compiled against the release stub, with no panel to show
 * it. A constant that travels with the panel cannot disagree with the panel.
 */
internal const val SITESKIN_INSPECTOR_AVAILABLE: Boolean = true

@Composable
internal fun SiteSkinInspectorHost(snapshot: InspectorSnapshot?) {
    if (snapshot == null) return
    var open by remember { mutableStateOf(false) }
    // enableEdgeToEdge() means an uninset overlay puts the affordance under the gesture bar, where
    // it is partly untappable. Every other browser surface goes through the same safeDrawing inset.
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        WeboraFloatingActionButton(
            onClick = { open = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AFFORDANCE_INSET)
                .testTag(INSPECTOR_AFFORDANCE_TAG),
        ) {
            Text(stringResource(R.string.inspector_open))
        }
    }
    if (open) {
        SiteSkinInspectorPanel(snapshot, onClose = { open = false })
    }
}

internal const val INSPECTOR_AFFORDANCE_TAG = "inspector_affordance"
private val AFFORDANCE_INSET = 16.dp
