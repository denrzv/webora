package app.webora.browser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import app.webora.browser.R
import app.webora.browser.design.WeboraRadius
import app.webora.browser.design.WeboraSpacing

@Composable
internal fun HomeScreen(
    onNavigate: (String) -> Unit,
    recents: List<BrowsingRecord> = emptyList(),
    favourites: List<BrowsingRecord> = emptyList(),
    onRemoveFavourite: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var address by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(HOME_SCREEN_TAG),
        contentPadding = PaddingValues(WeboraSpacing.GUTTER),
        verticalArrangement = Arrangement.spacedBy(WeboraSpacing.LARGE),
    ) {
        item { HomeHeader() }
        item { HomeAddress(address, { address = it }, onNavigate) }
        item { BrowsingRecordSection(R.string.home_recent_title, R.string.home_recent_empty, recents, onNavigate) }
        item {
            BrowsingRecordSection(
                R.string.home_favourites_title,
                R.string.home_favourites_empty,
                favourites,
                onNavigate,
                onRemoveFavourite,
            )
        }
        item { Text(stringResource(R.string.home_suggested_title), style = MaterialTheme.typography.titleLarge) }
        items(defaultSuggestedSites, key = SuggestedSite::url) { site ->
            SuggestedSiteCard(site = site, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun HomeAddress(value: String, onValueChange: (String) -> Unit, onNavigate: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.address_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { resolveAddressInput(value)?.let(onNavigate) }),
        // This pill is explicit because the shared AlertDialog shape must remain independent.
        shape = RoundedCornerShape(WeboraRadius.PILL),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BrowsingRecordSection(
    titleRes: Int,
    emptyRes: Int,
    records: List<BrowsingRecord>,
    onNavigate: (String) -> Unit,
    onRemove: ((String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
        if (records.isEmpty()) {
            EmptyHomeSection(emptyRes)
        } else {
            records.forEach { record -> BrowsingRecordCard(record, onNavigate, onRemove) }
        }
    }
}

@Composable
private fun BrowsingRecordCard(
    record: BrowsingRecord,
    onNavigate: (String) -> Unit,
    onRemove: ((String) -> Unit)?,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(
            Modifier.padding(WeboraSpacing.LARGE),
            verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
        ) {
            Text(record.title, style = MaterialTheme.typography.titleMedium)
            Text(record.origin, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WeboraTextButton(onClick = { onNavigate(record.url) }) {
                Text(stringResource(R.string.home_open_record, record.title))
            }
            onRemove?.let { remove ->
                WeboraTextButton(onClick = { remove(record.url) }) {
                    Text(stringResource(R.string.remove_favourite_named, record.title))
                }
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(WeboraSpacing.BASE)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.home_welcome_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SuggestedSiteCard(site: SuggestedSite, onNavigate: (String) -> Unit) {
    val name = stringResource(site.nameRes)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(WeboraSpacing.LARGE),
            verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(site.descriptionRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            WeboraTextButton(onClick = { resolveAddressInput(site.url)?.let(onNavigate) }) {
                Text(stringResource(R.string.home_open_site, name))
            }
        }
    }
}

@Composable
private fun EmptyHomeSection(messageRes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WeboraRadius.LARGE),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(WeboraSpacing.LARGE),
            verticalArrangement = Arrangement.spacedBy(WeboraSpacing.BASE),
        ) {
            Text(
                stringResource(messageRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal const val HOME_SCREEN_TAG = "browser_home"
