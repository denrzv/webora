package app.webora.browser.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.webora.browser.R
import app.webora.browser.siteskin.StoredSiteConsent

@Composable
internal fun PrivacySettingsScreen(
    siteSkinEnabled: Boolean,
    decisions: List<StoredSiteConsent>,
    onSiteSkinEnabledChange: (Boolean) -> Unit,
    onRemoveDecision: (StoredSiteConsent) -> Unit,
    onClearBrowsingData: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.privacy_settings_title))
        // Material gives Switch its role and its 48 dp target but not its name: the label is a
        // sibling Text, so assistive technology would announce an unnamed toggle. The state
        // description is what keeps the setting from being communicated by switch position alone.
        val toggleLabel = stringResource(R.string.siteskin_global_toggle)
        val toggleState = stringResource(if (siteSkinEnabled) R.string.state_on else R.string.state_off)
        Text(toggleLabel)
        Switch(
            checked = siteSkinEnabled,
            onCheckedChange = onSiteSkinEnabledChange,
            modifier = Modifier.semantics {
                contentDescription = toggleLabel
                stateDescription = toggleState
            },
        )
        Text(stringResource(R.string.site_permissions))
        if (decisions.isEmpty()) Text(stringResource(R.string.no_site_permissions))
        // The origin goes in the button's own label rather than a sibling Text with a
        // contentDescription override. A screen of identically named "Reset decision" buttons is
        // unusable when navigating by control, and Compose merges a parent description with its
        // child text rather than replacing it — so the label is the honest place to put it.
        decisions.forEach { stored ->
            WeboraButton(
                label = stringResource(R.string.reset_site_permission, stored.origin.canonical),
                onClick = { onRemoveDecision(stored) },
            )
        }
        WeboraButton(stringResource(R.string.clear_browsing_data), onClearBrowsingData)
        WeboraButton(stringResource(R.string.close), onClose)
    }
}

@Composable
internal fun ClearBrowsingDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_browsing_data)) },
        text = { Text(stringResource(R.string.clear_browsing_data_message)) },
        confirmButton = { WeboraButton(stringResource(R.string.clear_data), onConfirm) },
        dismissButton = { WeboraButton(stringResource(R.string.cancel), onDismiss) },
    )
}
