package app.webora.browser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.webora.browser.R
import app.webora.browser.design.WeboraSpacing
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
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(WeboraSpacing.GUTTER),
        verticalArrangement = Arrangement.spacedBy(WeboraSpacing.LARGE),
    ) {
        Text(stringResource(R.string.privacy_settings_title), style = MaterialTheme.typography.headlineLarge)
        GlobalSiteSkinRow(siteSkinEnabled, onSiteSkinEnabledChange)
        PermissionSection(decisions, onRemoveDecision)
        WeboraButton(
            label = stringResource(R.string.clear_browsing_data),
            onClick = onClearBrowsingData,
            modifier = Modifier.fillMaxWidth(),
        )
        WeboraTextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.close))
        }
    }
}

@Composable
private fun GlobalSiteSkinRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val label = stringResource(R.string.siteskin_global_toggle)
    val state = stringResource(if (enabled) R.string.state_on else R.string.state_off)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onEnabledChange,
                )
                .semantics {
                    contentDescription = label
                    stateDescription = state
                }
                .padding(WeboraSpacing.LARGE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.MEDIUM),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WeboraSpacing.BASE)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.siteskin_global_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun PermissionSection(
    decisions: List<StoredSiteConsent>,
    onRemoveDecision: (StoredSiteConsent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL)) {
        Text(stringResource(R.string.site_permissions), style = MaterialTheme.typography.titleLarge)
        if (decisions.isEmpty()) {
            Text(
                stringResource(R.string.no_site_permissions),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        decisions.forEach { stored -> PermissionRow(stored, onRemoveDecision) }
    }
}

@Composable
private fun PermissionRow(stored: StoredSiteConsent, onRemoveDecision: (StoredSiteConsent) -> Unit) {
    val resetDescription = stringResource(R.string.reset_site_permission, stored.origin.canonical)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WeboraSpacing.MEDIUM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
        ) {
            Text(
                stored.origin.canonical,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            WeboraTextButton(
                onClick = { onRemoveDecision(stored) },
                modifier = Modifier.semantics { contentDescription = resetDescription },
            ) { Text(stringResource(R.string.reset)) }
        }
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
