package app.webora.browser.siteskin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.webora.browser.R
import app.webora.browser.browser.MINIMUM_TOUCH_TARGET
import app.webora.browser.browser.WeboraFloatingActionButton
import app.webora.browser.browser.WeboraIconButton
import app.webora.browser.design.WeboraSpacing
import dev.siteskin.core.model.NavigationItem

@Composable
internal fun SiteSkinBottomNavigation(
    items: List<SiteSkinItemModel>,
    onSelect: (NavigationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selected = stringResource(R.string.siteskin_nav_selected)
    val notSelected = stringResource(R.string.siteskin_nav_not_selected)
    NavigationBar(modifier.testTag(SITESKIN_BOTTOM_NAV_TAG)) {
        items.take(MAX_VISIBLE_NAVIGATION).forEach { item ->
            NavigationBarItem(
                selected = item.isActive,
                onClick = { onSelect(item.item) },
                icon = { SiteSkinIcon(item.icon) },
                label = { BoundedLabel(item.label) },
                modifier = Modifier.semantics {
                    contentDescription = item.label
                    stateDescription = if (item.isActive) selected else notSelected
                },
            )
        }
    }
}

@Composable
internal fun SiteSkinQuickActions(
    items: List<SiteSkinItemModel>,
    onSelect: (NavigationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.siteskin_quick_actions)
    Column(modifier.testTag(SITESKIN_QUICK_ACTIONS_TAG)) {
        WeboraFloatingActionButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
        ) { SiteSkinIcon(items.first().icon) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.take(MAX_VISIBLE_QUICK_ACTIONS).forEach { item ->
                DropdownMenuItem(
                    text = { BoundedLabel(item.label) },
                    leadingIcon = { SiteSkinIcon(item.icon) },
                    onClick = {
                        expanded = false
                        onSelect(item.item)
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun IntegratedBrowserMenuSheet(
    commands: List<BrowserMenuCommand>,
    isFavourite: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    onSelect: (BrowserMenuCommand) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.testTag(INTEGRATED_BROWSER_MENU_TAG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WeboraSpacing.GUTTER)
                .padding(bottom = WeboraSpacing.GUTTER),
            verticalArrangement = Arrangement.spacedBy(WeboraSpacing.MEDIUM),
        ) {
            BrowserMenuHeader(onDismiss)
            FavouriteTile(isFavourite) {
                onDismiss()
                onToggleFavourite()
            }
            commands.chunked(BROWSER_MENU_COLUMNS).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.MEDIUM),
                ) {
                    row.forEach { command ->
                        BrowserMenuTile(command) {
                            onDismiss()
                            onSelect(command)
                        }
                    }
                    if (row.size < BROWSER_MENU_COLUMNS) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BrowserMenuHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(R.string.siteskin_browser_menu_heading),
            style = MaterialTheme.typography.titleLarge,
        )
        WeboraIconButton(
            icon = R.drawable.ic_close,
            contentDescription = stringResource(R.string.close),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun FavouriteTile(isFavourite: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = BROWSER_MENU_TILE_HEIGHT),
    ) {
        Row(
            modifier = Modifier.padding(WeboraSpacing.LARGE),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.MEDIUM),
        ) {
            Icon(painterResource(R.drawable.ic_siteskin_flower), contentDescription = null)
            Text(
                stringResource(if (isFavourite) R.string.remove_favourite else R.string.add_favourite),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun RowScope.BrowserMenuTile(command: BrowserMenuCommand, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f).heightIn(min = BROWSER_MENU_TILE_HEIGHT),
    ) {
        Column(
            modifier = Modifier.padding(WeboraSpacing.LARGE),
            verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
        ) {
            Icon(painterResource(browserMenuIcon(command)), contentDescription = null)
            Text(browserMenuLabel(command), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun browserMenuIcon(command: BrowserMenuCommand): Int = when (command) {
    BrowserMenuCommand.PAGE_INFORMATION -> R.drawable.ic_lock
    BrowserMenuCommand.TABS -> R.drawable.ic_tabs
    BrowserMenuCommand.SETTINGS -> R.drawable.ic_menu
    BrowserMenuCommand.INSPECTOR -> R.drawable.ic_search
}

/**
 * The browser-authored label for a browser-owned menu command.
 *
 * Shared by the integrated menu and regular mode's own dropdown so the two cannot name the same
 * command differently — the same reason `browserMenuCommands()` is one expression read twice rather
 * than one condition written twice. Every label is a string resource: these are browser copy, and no
 * manifest field, page value or trusted configuration reaches them.
 */
@Composable
internal fun browserMenuLabel(command: BrowserMenuCommand): String = when (command) {
    BrowserMenuCommand.PAGE_INFORMATION -> stringResource(R.string.page_information)
    BrowserMenuCommand.TABS -> stringResource(R.string.tabs)
    BrowserMenuCommand.SETTINGS -> stringResource(R.string.settings)
    BrowserMenuCommand.INSPECTOR -> stringResource(R.string.inspector_menu_entry)
}

@Composable
private fun BoundedLabel(label: String) {
    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun SiteSkinIcon(name: String?) {
    Icon(
        painter = painterResource(siteSkinIconResource(name)),
        contentDescription = null,
        modifier = Modifier.clearAndSetSemantics { },
    )
}

internal fun siteSkinIconResource(name: String?): Int =
    SITE_SKIN_ICON_RESOURCES[name] ?: R.drawable.ic_siteskin_generic

private val SITE_SKIN_ICON_RESOURCES = mapOf(
    "home" to R.drawable.ic_home,
    "catalog" to R.drawable.ic_siteskin_catalog,
    "grid_view" to R.drawable.ic_siteskin_catalog,
    "flower" to R.drawable.ic_siteskin_flower,
    "shopping_cart" to R.drawable.ic_siteskin_shopping_cart,
    "person" to R.drawable.ic_siteskin_person,
    "call" to R.drawable.ic_siteskin_call,
    "share" to R.drawable.ic_siteskin_share,
    "menu" to R.drawable.ic_menu,
    "search" to R.drawable.ic_search,
)

internal const val SITESKIN_BOTTOM_NAV_TAG = "siteskin_bottom_navigation"
internal const val SITESKIN_QUICK_ACTIONS_TAG = "siteskin_quick_actions"
internal const val INTEGRATED_BROWSER_MENU_TAG = "integrated_browser_menu"
private const val MAX_VISIBLE_NAVIGATION = 5
private const val MAX_VISIBLE_QUICK_ACTIONS = 5
private const val BROWSER_MENU_COLUMNS = 2
private val BROWSER_MENU_TILE_HEIGHT = 76.dp
