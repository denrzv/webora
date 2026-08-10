package app.webora.browser.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    Column(modifier) {
        Text(stringResource(R.string.privacy_settings_title))
        Text(stringResource(R.string.siteskin_global_toggle))
        Switch(checked = siteSkinEnabled, onCheckedChange = onSiteSkinEnabledChange)
        Text(stringResource(R.string.site_permissions))
        if (decisions.isEmpty()) Text(stringResource(R.string.no_site_permissions))
        decisions.forEach { stored ->
            Text(stored.origin.canonical)
            Button(onClick = { onRemoveDecision(stored) }) {
                Text(stringResource(R.string.reset_site_permission))
            }
        }
        Button(onClick = onClearBrowsingData) { Text(stringResource(R.string.clear_browsing_data)) }
        Button(onClick = onClose) { Text(stringResource(R.string.close)) }
    }
}

@Composable
internal fun ClearBrowsingDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_browsing_data)) },
        text = { Text(stringResource(R.string.clear_browsing_data_message)) },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.clear_data)) } },
        dismissButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
