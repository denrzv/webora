package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinLimits

/**
 * One position in the integrated dock.
 *
 * **Sealed, and browser slots carry no site data at all.** `UX-024` put the browser's navigation
 * cluster and the site's action cluster in adjacent surfaces and recorded why they must not share an
 * item type: the moment they did, a manifest could publish something that renders identically to a
 * browser command. `UX-025` puts both kinds in *one row* for the first time, which is where that
 * pressure actually arrives.
 *
 * So [Brand], [Tabs] and [More] are objects — there is no field through which a manifest could
 * reach them — and [Site] carries the already-bounded [SiteSkinItemModel] and nothing else. The
 * tempting refactor is one `DockSlot(icon, label, onClick)` for all four; that is the violation this
 * shape exists to prevent, not merely a smell, and `DockArrangementTest` inventories the fields to
 * keep it closed as the model grows.
 */
internal sealed interface DockSlot {
    /** The browser-owned brand hub. Present in every arrangement, in a position the site cannot pick. */
    data object Brand : DockSlot

    /** The browser-owned tab switcher. */
    data object Tabs : DockSlot

    /** The browser-owned overflow. Present in every arrangement. */
    data object More : DockSlot

    /** One validated site item the manifest nominated. */
    data class Site(val item: SiteSkinItemModel) : DockSlot
}

/**
 * The dock's slots, in order, for the active configuration and page.
 *
 * Two shapes and no third: either the site nominated nothing usable and the dock is `UX-024`'s three
 * browser commands, or it nominated something and the dock grows around a centred brand hub.
 */
internal sealed interface DockArrangement {
    val slots: List<DockSlot>

    /** Every id the dock actually rendered, for the drawer to subtract. */
    val projectedIds: Set<String>
        get() = slots.filterIsInstance<DockSlot.Site>().map { it.item.id }.toSet()

    /** `UX-024`'s dock, unchanged: brand hub, Tabs, More. */
    data object BrowserOnly : DockArrangement {
        override val slots: List<DockSlot> = listOf(DockSlot.Brand, DockSlot.Tabs, DockSlot.More)
    }

    /**
     * Site items around a centred brand hub, with More trailing.
     *
     * Tabs leaves the dock here and is reached through the More menu, which has offered it since
     * `DEVX-003`. That is the one browser affordance this ticket moves, and it moves to a surface
     * that already carried it rather than disappearing.
     */
    data class Projected(val items: List<SiteSkinItemModel>) : DockArrangement {
        override val slots: List<DockSlot> = buildList {
            val leading = (items.size + 1) / 2
            items.take(leading).forEach { add(DockSlot.Site(it)) }
            add(DockSlot.Brand)
            items.drop(leading).forEach { add(DockSlot.Site(it)) }
            add(DockSlot.More)
        }
    }
}

/**
 * Resolves the trusted dock ids against the bounded chrome model.
 *
 * Pure and total. It performs no validation of its own — core has already bounded the id list to
 * three, de-duplicated it, and dropped every id that named nothing — so this is a lookup, and a
 * lookup that finds nothing yields [DockArrangement.BrowserOnly] rather than an empty dock.
 *
 * **Order is the site's, membership is the browser's.** The site chose which of its items matter and
 * in what sequence; where those land, how many slots exist, and what sits between them are decided
 * here. There is no input through which a manifest could add a fourth site slot, move the brand hub,
 * or remove More.
 *
 * Items are looked up across all three collections because an id is unique within the manifest and a
 * site may reasonably promote a quick action or a menu entry, not only a navigation tab.
 */
internal fun dockArrangement(model: SiteSkinChromeModel, dockIds: List<String>): DockArrangement {
    if (dockIds.isEmpty()) return DockArrangement.BrowserOnly
    val byId = (model.bottomNavigation + model.quickActions + model.siteMenu).associateBy { it.id }
    // Defence in depth, exactly like `SiteSkinChromeModel`'s own 5/5/20 caps over core's limits.
    // Core bounds this list to `SiteSkinLimits.MAX_DOCK_ITEMS` already, so a longer one reaching
    // here means the two layers disagree — and the browser's dock is not where that should first
    // become visible.
    val items = dockIds.take(SiteSkinLimits.MAX_DOCK_ITEMS).mapNotNull(byId::get)
    return if (items.isEmpty()) DockArrangement.BrowserOnly else DockArrangement.Projected(items)
}
