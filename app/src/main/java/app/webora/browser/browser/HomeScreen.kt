package app.webora.browser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeScreen(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    var address by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Webora", style = MaterialTheme.typography.headlineLarge) }
        item {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Search or enter address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { resolveAddressInput(address)?.let(onNavigate) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { EmptyHomeSection("Recent sites", "Sites you visit will appear here.") }
        item { EmptyHomeSection("Favourites", "Your saved sites will appear here.") }
        item { Text("Suggested integrations", style = MaterialTheme.typography.titleLarge) }
        items(defaultSuggestedSites, key = SuggestedSite::url) { site ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(site.name, style = MaterialTheme.typography.titleMedium)
                    Text(site.description)
                    Button(onClick = { resolveAddressInput(site.url)?.let(onNavigate) }) {
                        Text("Open ${site.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeSection(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
