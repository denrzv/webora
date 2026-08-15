package app.webora.browser.siteskin

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.webora.browser.R
import app.webora.browser.browser.WeboraIconButton

/** Fixed browser-owned commands hosted by the expressive SiteSkin surface. */
@Composable
@Suppress("LongParameterList")
internal fun SiteSkinDock(
    presentation: ExpressiveSiteSkinPresentation,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onOpenHub: () -> Unit,
    onTabs: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpressiveSiteSkinDock(presentation, modifier.testTag(SITESKIN_DOCK_TAG)) {
        DockCommand(R.drawable.ic_back, stringResource(R.string.back), canGoBack, onBack, SITESKIN_DOCK_BACK_TAG)
        DockCommand(
            R.drawable.ic_forward,
            stringResource(R.string.forward),
            canGoForward,
            onForward,
            SITESKIN_DOCK_FORWARD_TAG,
        )
        DockCommand(
            R.drawable.ic_siteskin_flower,
            stringResource(R.string.siteskin_open_hub),
            true,
            onOpenHub,
            SITESKIN_DOCK_HUB_TAG,
        )
        DockCommand(R.drawable.ic_tabs, stringResource(R.string.tabs), true, onTabs, SITESKIN_DOCK_TABS_TAG)
        DockCommand(R.drawable.ic_more, stringResource(R.string.more), true, onMore, SITESKIN_DOCK_MORE_TAG)
    }
}

@Composable
private fun RowScope.DockCommand(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
    ) {
        WeboraIconButton(icon, label, onClick, Modifier.testTag(tag), enabled)
    }
}

internal const val SITESKIN_DOCK_TAG = "siteskin_browser_dock"
internal const val SITESKIN_DOCK_BACK_TAG = "siteskin_dock_back"
internal const val SITESKIN_DOCK_FORWARD_TAG = "siteskin_dock_forward"
internal const val SITESKIN_DOCK_HUB_TAG = "siteskin_dock_hub"
internal const val SITESKIN_DOCK_TABS_TAG = "siteskin_dock_tabs"
internal const val SITESKIN_DOCK_MORE_TAG = "siteskin_dock_more"
