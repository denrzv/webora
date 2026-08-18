package app.webora.browser.siteskin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.webora.browser.R
import app.webora.browser.browser.WeboraIconButton
import dev.siteskin.core.model.NavigationItem

/** Fixed browser-owned commands hosted by the expressive SiteSkin surface. */
@Composable
@Suppress("LongParameterList")
internal fun SiteSkinDock(
    presentation: ExpressiveSiteSkinPresentation,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    siteActions: List<SiteSkinItemModel>,
    hubSurface: HubSurface,
    siteActionsExpanded: Boolean,
    onSiteActionsToggle: () -> Unit,
    onSiteActionsDismiss: () -> Unit,
    onSiteSelect: (NavigationItem) -> Unit,
    onTabs: () -> Unit,
    onMore: () -> Unit,
    brandAsset: BrandAsset,
    modifier: Modifier = Modifier,
) {
    ExpressiveSiteSkinDock(presentation, modifier.testTag(SITESKIN_DOCK_TAG)) {
        DockCommand(
            R.drawable.ic_back, stringResource(R.string.back), canGoBack, onBack,
            SITESKIN_DOCK_BACK_TAG, presentation.colors.onSecondary,
        )
        DockCommand(
            R.drawable.ic_forward,
            stringResource(R.string.forward),
            canGoForward,
            onForward,
            SITESKIN_DOCK_FORWARD_TAG,
            presentation.colors.onSecondary,
        )
        BrandHubCommand(
            asset = brandAsset,
            label = stringResource(R.string.siteskin_open_hub),
            actions = siteActions,
            // The bouquet is a `Popup` anchored to this button, so it can only be composed here.
            // The drawer is a `Dialog` in its own window and is composed beside the dock by
            // `SiteSkinHubHost`. Both read the same single hub state with its three existing
            // resets; only one is ever composed, because `hubSurface` is a total decision.
            expanded = siteActionsExpanded && hubSurface == HubSurface.BOUQUET,
            onToggle = onSiteActionsToggle,
            onDismiss = onSiteActionsDismiss,
            onSelect = onSiteSelect,
            colors = presentation.colors,
        )
        DockCommand(
            R.drawable.ic_tabs, stringResource(R.string.tabs), true, onTabs,
            SITESKIN_DOCK_TABS_TAG, presentation.colors.onSecondary,
        )
        DockCommand(
            R.drawable.ic_more, stringResource(R.string.more), true, onMore,
            SITESKIN_DOCK_MORE_TAG, presentation.colors.onSecondary,
        )
    }
}

@Composable
private fun RowScope.DockCommand(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    contentColor: Color,
) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            WeboraIconButton(icon, label, onClick, Modifier.testTag(tag), enabled)
        }
    }
}

@Composable
private fun RowScope.BrandHubCommand(
    asset: BrandAsset,
    label: String,
    actions: List<SiteSkinItemModel>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (NavigationItem) -> Unit,
    colors: SiteSkinColorScheme,
) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        WeboraIconButton(
            contentDescription = label,
            onClick = onToggle,
            modifier = Modifier
                .size(BRAND_HUB_TARGET_SIZE)
                .clip(CircleShape)
                .background(colors.primary)
                .testTag(SITESKIN_DOCK_HUB_TAG),
        ) {
            BrandHubIdentity(asset, colors.onPrimary)
        }
        SiteActionBouquet(actions, expanded, colors, onDismiss, onSelect)
    }
}

@Composable
private fun SiteActionBouquet(
    actions: List<SiteSkinItemModel>,
    expanded: Boolean,
    colors: SiteSkinColorScheme,
    onDismiss: () -> Unit,
    onSelect: (NavigationItem) -> Unit,
) {
    if (!expanded || actions.isEmpty()) return
    val offset = with(LocalDensity.current) { -ACTION_BOUQUET_OFFSET.roundToPx() }
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offset),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Row(
            modifier = Modifier
                .height(ACTION_BOUQUET_HEIGHT)
                .testTag(SITESKIN_ACTION_BOUQUET_TAG),
            verticalAlignment = Alignment.Bottom,
        ) {
            actions.forEachIndexed { index, action ->
                ActionPetal(action, index, actions.size, colors, onDismiss, onSelect)
            }
        }
    }
}

@Composable
private fun ActionPetal(
    action: SiteSkinItemModel,
    index: Int,
    count: Int,
    colors: SiteSkinColorScheme,
    onDismiss: () -> Unit,
    onSelect: (NavigationItem) -> Unit,
) {
    val primary = index % 2 == 0 || action.isActive
    val selected = stringResource(R.string.siteskin_nav_selected)
    val notSelected = stringResource(R.string.siteskin_nav_not_selected)
    Box(
        modifier = Modifier
            .size(ACTION_PETAL_SLOT_SIZE)
            .offset(y = -actionLift(index, count)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = {
                onDismiss()
                onSelect(action.item)
            },
            shape = CircleShape,
            color = if (primary) colors.primary else colors.secondary,
            contentColor = if (primary) colors.onPrimary else colors.onSecondary,
            shadowElevation = ACTION_PETAL_ELEVATION,
            modifier = Modifier
                .size(ACTION_PETAL_SIZE)
                .testTag("$SITESKIN_ACTION_TAG_PREFIX${action.id}")
                .semantics {
                    contentDescription = action.label
                    if (action.isNavigation) {
                        stateDescription = if (action.isActive) selected else notSelected
                    }
                },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SiteSkinIcon(action.icon)
            }
        }
    }
}

private fun actionLift(index: Int, count: Int) = when (count) {
    1 -> 20.dp
    2 -> 12.dp
    THREE_ACTIONS -> listOf(4.dp, 20.dp, 4.dp)[index]
    FOUR_ACTIONS -> listOf(4.dp, 16.dp, 16.dp, 4.dp)[index]
    else -> listOf(0.dp, 12.dp, 20.dp, 12.dp, 0.dp)[index]
}

@Composable
private fun BrandHubIdentity(asset: BrandAsset, contentColor: Color) {
    Box(
        modifier = Modifier
            .size(BRAND_HUB_ASSET_SIZE)
            .testTag(BRAND_HUB_IDENTITY_TAG),
        contentAlignment = Alignment.Center,
    ) {
        when (asset) {
            is BrandAsset.BitmapAsset -> Image(
                bitmap = asset.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(BRAND_HUB_ASSET_SIZE).clip(CircleShape).clearAndSetSemantics { },
            )
            is BrandAsset.Monogram -> if (asset.text.isNotBlank()) {
                Text(
                    text = asset.text.take(2),
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_siteskin_flower),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(BRAND_HUB_ASSET_SIZE).clearAndSetSemantics { },
                )
            }
        }
    }
}

internal const val SITESKIN_DOCK_TAG = "siteskin_browser_dock"
internal const val SITESKIN_DOCK_BACK_TAG = "siteskin_dock_back"
internal const val SITESKIN_DOCK_FORWARD_TAG = "siteskin_dock_forward"
internal const val SITESKIN_DOCK_HUB_TAG = "siteskin_dock_hub"
internal const val SITESKIN_DOCK_TABS_TAG = "siteskin_dock_tabs"
internal const val SITESKIN_DOCK_MORE_TAG = "siteskin_dock_more"
internal val BRAND_HUB_TARGET_SIZE = 52.dp
internal val BRAND_HUB_ASSET_SIZE = 40.dp
internal const val BRAND_HUB_IDENTITY_TAG = "siteskin_dock_hub_identity"
internal const val SITESKIN_ACTION_BOUQUET_TAG = "siteskin_action_bouquet"
internal const val SITESKIN_ACTION_TAG_PREFIX = "siteskin_action_"
private val ACTION_BOUQUET_HEIGHT = 84.dp
private val ACTION_BOUQUET_OFFSET = 96.dp
private val ACTION_PETAL_SLOT_SIZE = 60.dp
private val ACTION_PETAL_SIZE = 52.dp
private val ACTION_PETAL_ELEVATION = 8.dp
private const val THREE_ACTIONS = 3
private const val FOUR_ACTIONS = 4
