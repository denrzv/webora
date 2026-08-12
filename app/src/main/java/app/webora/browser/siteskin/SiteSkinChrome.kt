package app.webora.browser.siteskin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
    val glyph = stringResource(R.string.siteskin_quick_actions_glyph)
    Column(modifier.testTag(SITESKIN_QUICK_ACTIONS_TAG)) {
        WeboraFloatingActionButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
        ) { Text(glyph) }
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
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier.testTag(SITESKIN_MENU_TAG)) {
        if (model.siteMenu.isNotEmpty()) {
            MenuHeading(stringResource(R.string.siteskin_site_menu_heading))
            model.siteMenu.take(MAX_VISIBLE_MENU_ITEMS).forEach { item ->
                MenuItem(item.label, item.icon) { onSiteSelect(item.item) }
            }
            HorizontalDivider()
        }
        MenuHeading(stringResource(R.string.siteskin_browser_menu_heading))
        model.browserMenu.forEach { command ->
            val label = when (command) {
                BrowserMenuCommand.PAGE_INFORMATION -> stringResource(R.string.page_information)
                BrowserMenuCommand.SETTINGS -> stringResource(R.string.settings)
                BrowserMenuCommand.INSPECTOR -> stringResource(R.string.inspector_menu_entry)
            }
            MenuItem(label, null) { onBrowserSelect(command) }
        }
    }
}

@Composable
private fun MenuHeading(label: String) {
    Text(label, modifier = Modifier.testTag("$SITESKIN_MENU_SECTION_PREFIX$label"))
}

@Composable
private fun MenuItem(label: String, icon: String?, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { BoundedLabel(label) },
        leadingIcon = icon?.let { { SiteSkinIcon(it) } },
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MINIMUM_TOUCH_TARGET),
    )
}

@Composable
private fun BoundedLabel(label: String) {
    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SiteSkinIcon(name: String?) {
    val glyph = when (name) {
        "home" -> "⌂"
        "grid_view" -> "▦"
        "shopping_cart" -> "▣"
        "person" -> "●"
        "call" -> "☎"
        else -> "•"
    }
    Text(glyph, modifier = Modifier.clearAndSetSemantics { })
}

internal const val SITESKIN_BOTTOM_NAV_TAG = "siteskin_bottom_navigation"
internal const val SITESKIN_QUICK_ACTIONS_TAG = "siteskin_quick_actions"
internal const val SITESKIN_MENU_TAG = "siteskin_menu"
internal const val SITESKIN_MENU_SECTION_PREFIX = "siteskin_menu_section_"
private const val MAX_VISIBLE_NAVIGATION = 5
private const val MAX_VISIBLE_QUICK_ACTIONS = 5
private const val MAX_VISIBLE_MENU_ITEMS = 20
