package app.webora.browser.siteskin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import app.webora.browser.R
import app.webora.browser.browser.MINIMUM_TOUCH_TARGET
import app.webora.browser.browser.WeboraFloatingActionButton
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
internal fun SiteSkinMenu(
    model: SiteSkinChromeModel,
    onSiteSelect: (NavigationItem) -> Unit,
    onBrowserSelect: (BrowserMenuCommand) -> Unit,
    isFavourite: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier.testTag(SITESKIN_MENU_TAG)) {
        SiteHubSections(model, onSiteSelect)
        if (model.hasSiteItems()) HorizontalDivider()
        MenuHeading(stringResource(R.string.siteskin_browser_menu_heading))
        MenuItem(
            stringResource(if (isFavourite) R.string.remove_favourite else R.string.add_favourite),
            null,
            onClick = onToggleFavourite,
        )
        model.browserMenu.forEach { command ->
            MenuItem(browserMenuLabel(command), null) { onBrowserSelect(command) }
        }
    }
}

@Composable
private fun SiteHubSections(model: SiteSkinChromeModel, onSiteSelect: (NavigationItem) -> Unit) {
    if (model.bottomNavigation.isNotEmpty()) {
        MenuHeading(stringResource(R.string.siteskin_site_menu_heading))
        model.bottomNavigation.forEach { item ->
            MenuItem(item.label, item.icon, item.isActive) { onSiteSelect(item.item) }
        }
    }
    if (model.quickActions.isNotEmpty()) {
        MenuHeading(stringResource(R.string.siteskin_quick_actions))
        model.quickActions.forEach { item -> MenuItem(item.label, item.icon) { onSiteSelect(item.item) } }
    }
    if (model.siteMenu.isNotEmpty()) {
        MenuHeading(stringResource(R.string.siteskin_site_menu_heading))
        model.siteMenu.take(MAX_VISIBLE_MENU_ITEMS).forEach { item ->
            MenuItem(item.label, item.icon) { onSiteSelect(item.item) }
        }
    }
}

private fun SiteSkinChromeModel.hasSiteItems(): Boolean =
    bottomNavigation.isNotEmpty() || quickActions.isNotEmpty() || siteMenu.isNotEmpty()

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
private fun MenuHeading(label: String) {
    Text(label, modifier = Modifier.testTag("$SITESKIN_MENU_SECTION_PREFIX$label"))
}

@Composable
private fun MenuItem(label: String, icon: String?, selected: Boolean? = null, onClick: () -> Unit) {
    val selectedDescription = stringResource(R.string.siteskin_nav_selected)
    val notSelectedDescription = stringResource(R.string.siteskin_nav_not_selected)
    DropdownMenuItem(
        text = { BoundedLabel(label) },
        leadingIcon = icon?.let { { SiteSkinIcon(it) } },
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MINIMUM_TOUCH_TARGET)
            .semantics {
                if (selected != null) {
                    stateDescription = if (selected) selectedDescription else notSelectedDescription
                }
            },
    )
}

@Composable
private fun BoundedLabel(label: String) {
    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SiteSkinIcon(name: String?) {
    Icon(
        painter = painterResource(siteSkinIconResource(name)),
        contentDescription = null,
        modifier = Modifier.clearAndSetSemantics { },
    )
}

private fun siteSkinIconResource(name: String?): Int =
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
internal const val SITESKIN_MENU_TAG = "siteskin_menu"
internal const val SITESKIN_MENU_SECTION_PREFIX = "siteskin_menu_section_"
private const val MAX_VISIBLE_NAVIGATION = 5
private const val MAX_VISIBLE_QUICK_ACTIONS = 5
private const val MAX_VISIBLE_MENU_ITEMS = 20
