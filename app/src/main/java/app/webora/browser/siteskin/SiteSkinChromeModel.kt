package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinLimits
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

/**
 * Re-bounds manifest label text before it can reach the screen or the accessibility tree.
 *
 * `:siteskin-core` already truncates to this limit during normalization, and this is deliberately
 * the same bound read from the same published constant rather than a second number that could
 * drift. It is applied again because the visual bounds on manifest text — one line, ellipsized —
 * are not bounds at all in the accessible channel: assistive technology reads the string, not the
 * layout, so an unbounded label would be spoken in full however the pixels were clipped.
 */
internal fun accessibleLabel(raw: String): String = raw.take(SiteSkinLimits.MAX_LABEL_LENGTH)

private fun NavigationItem.toModel(isActive: Boolean = false) = SiteSkinItemModel(
    id = id,
    label = accessibleLabel(label),
    icon = icon,
    isActive = isActive,
    item = this,
)

private val BROWSER_MENU_COMMANDS = BrowserMenuCommand.entries.toList()
private const val MAX_NAVIGATION_ITEMS = 5
private const val MAX_QUICK_ACTIONS = 5
private const val MAX_MENU_ITEMS = 20
