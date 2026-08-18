package app.webora.browser.siteskin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.webora.browser.R
import app.webora.browser.browser.WeboraIconButton

/**
 * The browser's navigation commands, as one control in a header a manifest paints.
 *
 * **Nothing in this file reads a site value, and that is the assertion rather than the intention.**
 * `BrowserNavigationHubContractTest` scans the whole file for `presentation`, `colors.`, `model.`,
 * `SiteSkinItemModel`, `NavigationItem` and `ActionResolver`; a rule scoped to one declaration would
 * have to be re-scoped every time a helper is added here. The colours come from `WeboraTheme`
 * through `MaterialTheme`, the icons from `R.drawable`, the names from `R.string`, the order from
 * [browserNavigationActions] and the callbacks from [BrowserNavigationHubState].
 *
 * **The collapsed control is never disabled.** A hub that greys out at the history root *is* a Back
 * button — the misleading affordance `UX-024` exists to remove — and a user who could not open it
 * would also lose Forward and Refresh. It always opens; its three children carry the state, per
 * `A11Y-001`'s rule that enabled state lives in the semantics tree and not only in the pixels.
 *
 * **The bouquet is a `Popup`, which is the mechanism and not the decoration.** Its own window means
 * three things at once: Android and predictive Back are consumed here before `BrowserBackHandler`
 * sees them, so `BROWSE-002`'s single Back contract needs no second handler racing it (`UX-022`);
 * an outside tap dismisses without reaching the page; and the cluster contributes nothing to the
 * header's layout, so the 40 dp `BROWSE-011`'s control row cost is genuinely returned rather than
 * spent again below it.
 *
 * It expands **downward** — `SiteActionBouquet`'s upward anchor inverted, because this anchor is at
 * the top of the screen. `Alignment.TopStart` resolves against layout direction, so RTL is correct
 * with no physical constant in the file.
 *
 * **No animation, deliberately.** `UX-013`'s closed motion policy still has no reader. The issue
 * permits an instant transition, the browser has no animation anywhere today, and a
 * security-relevant overlay whose behaviour no local gate can observe is the wrong place to spend
 * the first one. The obligation passes to the next ticket adding a real decorative transition.
 */
@Composable
internal fun BrowserNavigationHub(state: BrowserNavigationHubState, modifier: Modifier = Modifier) {
    Box(modifier) {
        BrowserControlTile(SITESKIN_NAV_HUB_TAG) {
            WeboraIconButton(
                icon = R.drawable.ic_history,
                // Not `R.string.back`. The control opens three commands, and naming it after one of
                // them is the same lie the icon would tell.
                contentDescription = stringResource(R.string.siteskin_open_navigation),
                onClick = { state.onExpandedChange(!state.expanded) },
            )
        }
        NavigationBouquet(state)
    }
}

@Composable
private fun NavigationBouquet(state: BrowserNavigationHubState) {
    if (!state.expanded) return
    val offset = with(LocalDensity.current) { NAV_BOUQUET_OFFSET.roundToPx() }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, offset),
        onDismissRequest = { state.onExpandedChange(false) },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NAV_BUBBLE_GAP),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag(SITESKIN_NAV_BOUQUET_TAG),
        ) {
            state.actions.forEach { action -> NavigationBubble(action, state) }
        }
    }
}

@Composable
private fun NavigationBubble(action: BrowserNavigationAction, state: BrowserNavigationHubState) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(NAV_BUBBLE_SIZE)
            .shadow(NAV_BUBBLE_ELEVATION, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        WeboraIconButton(
            icon = navigationIcon(action.command),
            contentDescription = stringResource(navigationLabel(action.command)),
            // Close, then dispatch — the order `ActionPetal` already uses, and what makes
            // "selecting an action closes the bouquet" true of the callback rather than of a
            // recomposition that happens to follow it.
            onClick = {
                state.onExpandedChange(false)
                state.onCommand(action.command)
            },
            modifier = Modifier.testTag(navigationTag(action.command)),
            enabled = action.enabled,
        )
    }
}

/**
 * One browser-owned sub-surface for a browser control drawn inside a header the site paints.
 *
 * `UX-014` records why it exists: *"the visual boundary is the ownership boundary."* It reads
 * `MaterialTheme.colorScheme.surfaceContainer` and nothing from [SiteSkinColorScheme]. Moved here
 * from `SiteSkinTopBar.kt` by `UX-024` because the header's browser controls all moved here with
 * it; the top bar now holds only the site's identity half and the browser's trust chip.
 */
@Composable
private fun BrowserControlTile(tag: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .testTag(tag),
        content = { content() },
    )
}

/**
 * The bundled drawable for a browser command. Closed `when`, so a fourth command is a compile error
 * rather than a missing picture, and there is no name, path, identifier or remote asset involved —
 * the rule `UX-005` states for the *site's* icons, applied to the browser's own.
 */
private fun navigationIcon(command: BrowserNavigationCommand): Int = when (command) {
    BrowserNavigationCommand.BACK -> R.drawable.ic_back
    BrowserNavigationCommand.FORWARD -> R.drawable.ic_forward
    BrowserNavigationCommand.REFRESH -> R.drawable.ic_reload
}

/**
 * The browser-authored name for a browser command — the same resource regular chrome uses.
 * `DEVX-003`: one command does not acquire two names because it is drawn on a second surface.
 */
private fun navigationLabel(command: BrowserNavigationCommand): Int = when (command) {
    BrowserNavigationCommand.BACK -> R.string.back
    BrowserNavigationCommand.FORWARD -> R.string.forward
    BrowserNavigationCommand.REFRESH -> R.string.reload
}

private fun navigationTag(command: BrowserNavigationCommand): String = when (command) {
    BrowserNavigationCommand.BACK -> SITESKIN_BACK_TAG
    BrowserNavigationCommand.FORWARD -> SITESKIN_FORWARD_TAG
    BrowserNavigationCommand.REFRESH -> SITESKIN_REFRESH_TAG
}

internal const val SITESKIN_NAV_HUB_TAG = "siteskin_nav_hub"
internal const val SITESKIN_NAV_BOUQUET_TAG = "siteskin_nav_bouquet"

/**
 * `SITESKIN_BACK_TAG` and `SITESKIN_REFRESH_TAG` keep the exact values they carried on the header's
 * standalone controls. The bubbles genuinely are Back and Refresh, so the names stay honest, and the
 * hosted journey's edit is "open the hub first" rather than a rename across two repositories' worth
 * of pinned assertions.
 */
internal const val SITESKIN_BACK_TAG = "siteskin_back"
internal const val SITESKIN_FORWARD_TAG = "siteskin_forward"
internal const val SITESKIN_REFRESH_TAG = "siteskin_refresh"

/** Below the 48 dp tile plus an 8 dp gap, so the cluster clears the brand row it hangs from. */
private val NAV_BOUQUET_OFFSET = 56.dp

/**
 * 52 dp, matching `ACTION_PETAL_SIZE`, so the two bouquets read as one visual language. The 48 dp
 * interaction contract inside is `WeboraIconButton`'s, which has one owner; this is the painted
 * circle around it and deliberately not a second target constant (`UX-002`).
 */
private val NAV_BUBBLE_SIZE = 52.dp
private val NAV_BUBBLE_GAP = 8.dp
private val NAV_BUBBLE_ELEVATION = 8.dp
