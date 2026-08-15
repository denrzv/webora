package app.webora.browser.siteskin

import app.webora.browser.inspector.SITESKIN_INSPECTOR_AVAILABLE
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
                browserMenu = browserMenuCommands(),
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
    TABS,
    SETTINGS,

    /** Debug-only. Offered by [browserMenuCommands], never by the enum's own membership. */
    INSPECTOR,
}

/**
 * The browser-owned commands this variant offers, in order.
 *
 * `SKIN-003` makes this a **closed** section that manifest entries cannot suppress or replace, and
 * this function is where that closure is expressed: the list is built explicitly rather than derived
 * from `BrowserMenuCommand.entries`, so a value added to the enum does not silently reach a menu.
 *
 * The debug entry is decided by [SITESKIN_INSPECTOR_AVAILABLE] — a `const val` declared in each
 * variant's own source set beside its panel — and never by `BuildConfig.DEBUG`, which AGP derives
 * from `isDebuggable` and `debugRelease` sets true while compiling against the *release* stub. It is
 * appended rather than filtered in, so a release build cannot draw an item whose handler does
 * nothing: an offered command the variant cannot service is a promise it cannot keep.
 *
 * One expression, read by both menus, so regular and integrated mode cannot drift apart.
 *
 * [inspectorAvailable] is a parameter rather than a direct read of the constant so that both answers
 * are reachable from a test. AGP 9.1 creates only `testDebugUnitTest`, where the constant is always
 * `true` — with the constant read inline, no unit test could ever observe the release behaviour, and
 * an implementation returning `BrowserMenuCommand.entries` unconditionally would pass every
 * assertion. That is not hypothetical: it is what the negative control for this function did before
 * the parameter existed.
 */
internal fun browserMenuCommands(
    inspectorAvailable: Boolean = SITESKIN_INSPECTOR_AVAILABLE,
): List<BrowserMenuCommand> = buildList {
    add(BrowserMenuCommand.PAGE_INFORMATION)
    add(BrowserMenuCommand.TABS)
    add(BrowserMenuCommand.SETTINGS)
    if (inspectorAvailable) add(BrowserMenuCommand.INSPECTOR)
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

private const val MAX_NAVIGATION_ITEMS = 5
private const val MAX_QUICK_ACTIONS = 5
private const val MAX_MENU_ITEMS = 20
