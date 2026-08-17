package app.webora.browser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.webora.browser.R
import app.webora.browser.design.WeboraSpacing

internal data class BrowserTabSummary(
    val id: Long,
    val label: String,
    val position: Int,
    val count: Int,
    val selected: Boolean,
)

internal fun tabSummaries(session: BrowserSession): List<BrowserTabSummary> = session.tabs.mapIndexed { index, tab ->
    BrowserTabSummary(
        id = tab.id,
        label = tab.browserLabel(),
        position = index + 1,
        count = session.tabs.size,
        selected = tab.id == session.activeId,
    )
}

private fun BrowserTab.browserLabel(): String = when (val mode = state.mode) {
    BrowserMode.Home -> "Home"
    is BrowserMode.Regular -> mode.origin?.registrableDomain ?: "Page"
    is BrowserMode.Integrated -> mode.origin.registrableDomain
}

/**
 * The tab switcher, in the modal language `UX-018` gave privacy settings.
 *
 * It used to be an `AlertDialog` whose body was two filled `WeboraButton`s per tab — eighteen filled
 * buttons at the eight-tab limit, inside a 280 dp box, with the accessibility sentence
 * `Tab 1 of 8, shop.example` used as the *visible* label and no visual selected state at all. The
 * container, the row and the emphasis all move here; the model, the callbacks and the four tag
 * constants do not.
 *
 * `AlertDialog` remains correct for the short confirmations — consent, clear-data, external
 * navigation. Those are a sentence and two decisions. A list of up to eight items with a per-item
 * destructive action is the settings shape.
 */
@Composable
internal fun TabSwitcher(
    session: BrowserSession,
    onSelect: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            TabSwitcherContent(
                session = session,
                onSelect = onSelect,
                onCloseTab = onCloseTab,
                onNewTab = onNewTab,
                onDismiss = onDismiss,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}

/**
 * The switcher body, separated from its window so instrumentation can drive it directly.
 *
 * The same split `PrivacySettingsDialog`/`PrivacySettingsScreen` uses, for the same reason: a
 * `Dialog` composes into its own window, and a test that wants the content and a test that wants the
 * dismissal contract are asking different questions.
 */
@Composable
internal fun TabSwitcherContent(
    session: BrowserSession,
    onSelect: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(WeboraSpacing.GUTTER),
        verticalArrangement = Arrangement.spacedBy(WeboraSpacing.LARGE),
    ) {
        Text(
            stringResource(R.string.tabs_title, session.tabs.size, BrowserSession.MAX_TABS),
            style = MaterialTheme.typography.headlineLarge,
        )
        Column(
            modifier = Modifier.fillMaxWidth().testTag(TAB_LIST_TAG),
            verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
        ) {
            tabSummaries(session).forEach { summary ->
                TabRow(summary, onSelect, onCloseTab)
            }
        }
        if (!session.canCreateTab) {
            Text(
                stringResource(R.string.tab_limit_reached),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        WeboraButton(
            onClick = onNewTab,
            enabled = session.canCreateTab,
            modifier = Modifier.fillMaxWidth().testTag(NEW_TAB_TAG),
        ) {
            Text(stringResource(R.string.new_tab))
        }
        WeboraTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.close))
        }
    }
}

/**
 * One tab: a tonal selectable surface, and a sibling close control.
 *
 * **Sibling, not descendant.** `Surface(onClick = …)` marks its node `mergeDescendants`, so a close
 * control placed *inside* the select affordance would stop being separately addressable and
 * `TAB_CLOSE_TAG` would resolve to the same node as `TAB_SELECT_TAG`. Both tags are an instrumented
 * contract, so the layout `Row` — which is not clickable and merges nothing — is what holds them
 * apart.
 *
 * **The selected state is a colour role, and the pair is not the obvious one.** `surfaceVariant`
 * against `primaryContainer` reads as the natural unselected/selected pairing, and it is invisible in
 * dark theme: `materialColorScheme` maps those from `chrome` and `container`, which are the same
 * value (`0xFF1F2C2C`) in `WeboraColors.DARK`. `primary`/`onPrimary` separates in both projections
 * and is already in `WeboraColorSchemeTest`'s measured table, so it introduces no unmeasured colour.
 * `TabSwitcherContractTest` pins the separation in both projections and pins the collapse of the
 * rejected pair, so the reason this choice was made cannot quietly stop being true. Not an alpha
 * multiplier, for the reason `UX-003` records: it reads as disabled and can fall below 3:1 whatever
 * sits underneath.
 *
 * The visible label is the browser-derived label alone; `tab_description`'s full sentence survives as
 * the row's accessible name, so the pixels stop repeating "Tab 1 of 8," on every line while the
 * screen-reader output does not regress.
 */
@Composable
private fun TabRow(summary: BrowserTabSummary, onSelect: (Long) -> Unit, onCloseTab: (Long) -> Unit) {
    val description = stringResource(R.string.tab_description, summary.position, summary.count, summary.label)
    val closeDescription = stringResource(R.string.close_tab, summary.label)
    val scheme = MaterialTheme.colorScheme
    val container: Color = if (summary.selected) scheme.primary else scheme.surfaceVariant
    val content: Color = if (summary.selected) scheme.onPrimary else scheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = { onSelect(summary.id) },
            modifier = Modifier
                .weight(1f)
                .browserTouchTarget()
                .testTag("$TAB_SELECT_TAG${summary.id}")
                .semantics {
                    selected = summary.selected
                    contentDescription = description
                },
            shape = MaterialTheme.shapes.medium,
            color = container,
            contentColor = content,
        ) {
            TabIdentity(summary)
        }
        WeboraIconButton(
            icon = R.drawable.ic_close,
            contentDescription = closeDescription,
            onClick = { onCloseTab(summary.id) },
            modifier = Modifier.testTag("$TAB_CLOSE_TAG${summary.id}"),
        )
    }
}

/**
 * What a row shows: the browser-derived label, and browser-authored ordinal copy beneath it.
 *
 * This is the whole visible surface of a tab, and `BROWSE-006` fixes what may be in it — `Home`, a
 * registrable domain, or `Page`. A document title, a manifest `site.name`, editable address text and
 * a per-tab remote asset all stay out; the first two are pinned by `TabSwitcherModelTest`, and
 * `TabSwitcherContractTest` scans this file for the types the last two would need.
 */
@Composable
private fun TabIdentity(summary: BrowserTabSummary) {
    Column(
        modifier = Modifier.padding(WeboraSpacing.MEDIUM),
        verticalArrangement = Arrangement.spacedBy(WeboraSpacing.BASE),
    ) {
        Text(
            summary.label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.tab_position, summary.position, summary.count),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal const val TAB_LIST_TAG = "tab_list"
internal const val NEW_TAB_TAG = "new_tab"
internal const val TAB_SELECT_TAG = "select_tab_"
internal const val TAB_CLOSE_TAG = "close_tab_"
