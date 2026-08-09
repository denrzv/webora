package app.webora.browser.siteskin

import dev.siteskin.core.model.NavigationItem
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.nav.NavMatcher

internal data class SiteSkinChromeModel(
    val bottomNavigation: List<SiteSkinItemModel>,
    val quickActions: List<SiteSkinItemModel>,
    val siteMenu: List<SiteSkinItemModel>,
    val browserMenu: List<BrowserMenuCommand>,
) {
    companion object {
        fun from(configuration: SiteSkinConfiguration, currentPageUrl: String): SiteSkinChromeModel {
            val navigation = configuration.bottomNavigation.orEmpty().take(MAX_NAVIGATION_ITEMS)
            val activeId = NavMatcher.activeItem(navigation, currentPageUrl)?.id
            return SiteSkinChromeModel(
                bottomNavigation = navigation.map { it.toModel(it.id == activeId) },
                quickActions = configuration.quickActions.orEmpty()
                    .take(MAX_QUICK_ACTIONS)
                    .map { it.toModel() },
                siteMenu = configuration.menu.orEmpty().take(MAX_MENU_ITEMS).map { it.toModel() },
                browserMenu = BROWSER_MENU_COMMANDS,
            )
        }
    }
}

internal data class SiteSkinItemModel(
    val id: String,
    val label: String,
    val icon: String?,
    val isActive: Boolean,
    val item: NavigationItem,
)

internal enum class BrowserMenuCommand {
    PAGE_INFORMATION,
    SETTINGS,
}

private fun NavigationItem.toModel(isActive: Boolean = false) = SiteSkinItemModel(
    id = id,
    label = label,
    icon = icon,
    isActive = isActive,
    item = this,
)

private val BROWSER_MENU_COMMANDS = BrowserMenuCommand.entries.toList()
private const val MAX_NAVIGATION_ITEMS = 5
private const val MAX_QUICK_ACTIONS = 5
private const val MAX_MENU_ITEMS = 20
