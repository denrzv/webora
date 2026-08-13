package app.webora.browser.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.webora.browser.R
import app.webora.browser.browser.WeboraButton

/**
 * What the browser decided about this origin, and why.
 *
 * Read-only by construction. There is no re-validate, no manifest override, no consent control and
 * no retry: a developer tool that can make a rejected manifest activate is a bypass of the
 * validator rather than a view of it, and `PRIV-001`'s settings screen stays the only place a
 * decision changes.
 *
 * Every website-controlled value goes through [inspectorValue] and is rendered in its own `Text`
 * node beside a browser-authored label from resources. Label and value are never concatenated,
 * because a value that can contain the label's separator can imitate the label.
 */
@Composable
internal fun SiteSkinInspectorPanel(snapshot: InspectorSnapshot, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag(INSPECTOR_PANEL_TAG),
        title = { Text(stringResource(R.string.inspector_title)) },
        text = { InspectorBody(snapshot) },
        confirmButton = { WeboraButton(stringResource(R.string.inspector_close), onClose) },
    )
}

@Composable
private fun InspectorBody(snapshot: InspectorSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        Text(stringResource(R.string.inspector_read_only))
        InspectorOriginSection(snapshot)
        InspectorBrandAssetSection(snapshot)
        snapshot.record?.let { record ->
            InspectorTransportSection(record.transport)
            InspectorValidationSection(record.validation)
        } ?: Text(stringResource(R.string.inspector_no_record))
        snapshot.applied?.let { InspectorAppliedSection(it) }
    }
}

@Composable
private fun InspectorOriginSection(snapshot: InspectorSnapshot) {
    InspectorHeading(stringResource(R.string.inspector_section_origin))
    InspectorRow(stringResource(R.string.inspector_origin), inspectorValue(snapshot.origin))
    InspectorRow(stringResource(R.string.inspector_activation), snapshot.activation.name)
    InspectorRow(stringResource(R.string.inspector_global_preference), snapshot.siteSkinEnabled.toString())
    InspectorRow(stringResource(R.string.inspector_consent), snapshot.consent?.name.orAbsent())
}

/**
 * Why the 40 dp slot is showing what it is showing.
 *
 * Its own section rather than rows appended to the origin's, because `HTTP status` and
 * `Redirects followed` already appear under `Transport` and mean the *manifest's* there. Two
 * identically labelled numbers about two different requests, in one scrolling panel, is a way to
 * misdiagnose rather than a way to diagnose.
 *
 * `NET-003` makes a monogram the correct output of every failure, so the kind alone cannot tell a
 * site owner whether their logo was refused, never arrived, or has not finished arriving. The stage
 * is the answer, and every value here is a closed browser-owned enum or a number — a header value, a
 * server message or a URL has no path into this section.
 */
@Composable
private fun InspectorBrandAssetSection(snapshot: InspectorSnapshot) {
    InspectorHeading(stringResource(R.string.inspector_section_brand_asset))
    InspectorRow(stringResource(R.string.inspector_brand_asset), snapshot.brandAsset.name)
    val trace = snapshot.brandAssetTrace
    InspectorRow(
        stringResource(R.string.inspector_brand_asset_stage),
        trace?.stage?.name ?: stringResource(R.string.inspector_brand_asset_pending),
    )
    trace ?: return
    InspectorRow(stringResource(R.string.inspector_brand_asset_rejection), trace.rejection?.name.orAbsent())
    InspectorRow(stringResource(R.string.inspector_brand_asset_status), trace.httpStatus?.toString().orAbsent())
    InspectorRow(stringResource(R.string.inspector_brand_asset_redirects), trace.redirects.toString())
    InspectorRow(
        stringResource(R.string.inspector_brand_asset_pixels),
        if (trace.width == null || trace.height == null) {
            stringResource(R.string.inspector_absent)
        } else {
            stringResource(R.string.inspector_brand_asset_pixels_value, trace.width, trace.height)
        },
    )
    InspectorRow(
        stringResource(R.string.inspector_brand_asset_elapsed),
        stringResource(R.string.inspector_brand_asset_elapsed_value, trace.elapsedMillis),
    )
}

@Composable
private fun InspectorTransportSection(transport: ManifestTransportTrace) {
    InspectorHeading(stringResource(R.string.inspector_section_transport))
    InspectorRow(stringResource(R.string.inspector_manifest_url), inspectorValue(transport.manifestUrl))
    InspectorRow(stringResource(R.string.inspector_transport_outcome), transport.outcome.name)
    InspectorRow(stringResource(R.string.inspector_http_status), transport.httpStatus?.toString().orAbsent())
    InspectorRow(stringResource(R.string.inspector_redirects), transport.redirects.toString())
    InspectorRow(stringResource(R.string.inspector_cache_state), transport.cacheState.name)
    InspectorRow(stringResource(R.string.inspector_rejection), transport.rejection?.name.orAbsent())
}

@Composable
private fun InspectorValidationSection(validation: ManifestValidationTrace) {
    InspectorHeading(stringResource(R.string.inspector_section_validation))
    InspectorRow(stringResource(R.string.inspector_validation_result), validation.result.name)
    InspectorRow(
        stringResource(R.string.inspector_schema_version),
        inspectorValue(validation.schemaVersion).ifEmpty { stringResource(R.string.inspector_absent) },
    )
    InspectorHeading(stringResource(R.string.inspector_diagnostics))
    if (validation.diagnostics.isEmpty()) {
        Text(stringResource(R.string.inspector_none))
    } else {
        // The code is a closed browser-owned vocabulary; the pointer is arbitrary website text,
        // because SS-W-FIELD-UNKNOWN reports the key it did not recognise.
        validation.diagnostics.forEach { InspectorRow(it.code, inspectorValue(it.pointer)) }
    }
}

@Composable
private fun InspectorAppliedSection(applied: InspectorAppliedChrome) {
    InspectorHeading(stringResource(R.string.inspector_section_applied))
    InspectorRow(stringResource(R.string.inspector_site_name), inspectorValue(applied.siteName))
    InspectorRow(stringResource(R.string.inspector_site_id), inspectorValue(applied.siteId))
    InspectorRow(stringResource(R.string.inspector_home_url), inspectorValue(applied.homeUrl))
    InspectorRow(
        stringResource(R.string.inspector_active_item),
        inspectorValue(applied.activeNavigationId).ifEmpty { stringResource(R.string.inspector_no_match) },
    )
    applied.counts.forEach { count ->
        InspectorRow(
            count.collection.name,
            stringResource(R.string.inspector_count_value, count.rendered, count.trusted),
        )
    }
    // The label slot is browser copy. `id` is schema-constrained to [a-z0-9_-], so nothing hostile
    // fits through it — which is exactly why it belongs in the value: the rule is enforceable only
    // while it has no exceptions, and this is the tool that exists to make the boundary legible.
    applied.navigation.forEach { item ->
        InspectorRow(
            stringResource(R.string.inspector_navigation_item),
            inspectorValue("${item.id} ${item.label} ${item.actionType}"),
        )
    }
    InspectorRow(
        stringResource(R.string.inspector_theme_mode),
        if (applied.theme.darkTheme) {
            stringResource(R.string.inspector_theme_dark)
        } else {
            stringResource(R.string.inspector_theme_light)
        },
    )
    applied.theme.roles.forEach { role ->
        InspectorRow(
            role.role.name,
            stringResource(R.string.inspector_color_value, role.applied, role.trusted.orAbsent()),
        )
    }
}

@Composable
private fun InspectorHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

/**
 * One browser-authored label and one value, as two nodes.
 *
 * `FlowRow` rather than `Row` so a long value wraps below its label at a large font scale instead of
 * being clipped — the same reason `A11Y-001` reflowed the browser's own control rows.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun InspectorRow(label: String, value: String) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ROW_SPACING)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun String?.orAbsent(): String = this ?: stringResource(R.string.inspector_absent)

internal const val INSPECTOR_PANEL_TAG = "inspector_panel"
private val ROW_SPACING = 8.dp
