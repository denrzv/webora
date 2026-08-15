package app.webora.browser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

@Composable
internal fun TabSwitcher(
    session: BrowserSession,
    onSelect: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tabs_title, session.tabs.size, BrowserSession.MAX_TABS)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag(TAB_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
            ) {
                tabSummaries(session).forEach { summary ->
                    TabRow(summary, onSelect, onCloseTab)
                }
                if (!session.canCreateTab) Text(stringResource(R.string.tab_limit_reached))
            }
        },
        confirmButton = {
            WeboraButton(
                label = stringResource(R.string.new_tab),
                onClick = onNewTab,
                enabled = session.canCreateTab,
                modifier = Modifier.testTag(NEW_TAB_TAG),
            )
        },
        dismissButton = { WeboraButton(stringResource(R.string.close), onDismiss) },
    )
}

@Composable
private fun TabRow(summary: BrowserTabSummary, onSelect: (Long) -> Unit, onCloseTab: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics { selected = summary.selected },
        horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeboraButton(
            label = stringResource(R.string.tab_description, summary.position, summary.count, summary.label),
            onClick = { onSelect(summary.id) },
            modifier = Modifier.weight(1f).testTag("$TAB_SELECT_TAG${summary.id}"),
        )
        WeboraButton(
            label = stringResource(R.string.close_tab, summary.label),
            onClick = { onCloseTab(summary.id) },
            modifier = Modifier.testTag("$TAB_CLOSE_TAG${summary.id}"),
        )
    }
}

internal const val TAB_LIST_TAG = "tab_list"
internal const val NEW_TAB_TAG = "new_tab"
internal const val TAB_SELECT_TAG = "select_tab_"
internal const val TAB_CLOSE_TAG = "close_tab_"
