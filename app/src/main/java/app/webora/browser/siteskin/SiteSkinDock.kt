package app.webora.browser.siteskin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        BrandHubCommand(brandAsset, stringResource(R.string.siteskin_open_hub), onOpenHub, presentation.colors)
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
    onClick: () -> Unit,
    colors: SiteSkinColorScheme,
) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        WeboraIconButton(
            contentDescription = label,
            onClick = onClick,
            modifier = Modifier
                .size(BRAND_HUB_TARGET_SIZE)
                .clip(CircleShape)
                .background(colors.primary)
                .testTag(SITESKIN_DOCK_HUB_TAG),
        ) {
            BrandHubIdentity(asset, colors.onPrimary)
        }
    }
}

@Composable
private fun BrandHubIdentity(asset: BrandAsset, contentColor: Color) {
    Box(Modifier.testTag(BRAND_HUB_IDENTITY_TAG), contentAlignment = Alignment.Center) {
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
