package app.webora.browser.browser

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import app.webora.browser.web.BrowserWebViewController
import app.webora.browser.web.HardenedWebView

@Composable
internal fun BrowserScreen(
    startUrl: String,
    modifier: Modifier = Modifier,
) {
    val controller = remember { BrowserWebViewController() }
    var state by remember { mutableStateOf(BrowserState(addressText = startUrl)) }

    BrowserBackHandler(enabled = state.canGoBack, controller = controller)
    Column(modifier = modifier) {
        AddressBar(
            state = state,
            onAddressChanged = { state = state.observe(BrowserObservation.AddressEdited(it)) },
            onSubmit = { resolveAddressInput(state.addressText)?.let(controller::navigate) },
            onBack = controller::goBack,
            onForward = controller::goForward,
            onReload = controller::reload,
        )
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        HardenedWebView(
            initialUrl = startUrl,
            controller = controller,
            onObservation = { observation ->
                state = state.observe(
                    BrowserObservation.Page(
                        observation.url,
                        observation.isLoading,
                        observation.canGoBack,
                        observation.canGoForward,
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BrowserBackHandler(enabled: Boolean, controller: BrowserWebViewController) {
    val owner = LocalOnBackPressedDispatcherOwner.current
    val callback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                if (!controller.goBack()) {
                    isEnabled = false
                    owner?.onBackPressedDispatcher?.onBackPressed()
                    isEnabled = true
                }
            }
        }
    }
    callback.isEnabled = enabled
    DisposableEffect(owner, callback) {
        owner?.onBackPressedDispatcher?.addCallback(callback)
        onDispose(callback::remove)
    }
}

@Composable
private fun AddressBar(
    state: BrowserState,
    onAddressChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = state.addressText,
            onValueChange = onAddressChanged,
            label = { Text("Search or enter address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            BrowserButton("Back", state.canGoBack, onBack)
            BrowserButton("Forward", state.canGoForward, onForward)
            BrowserButton("Reload", true, onReload)
        }
    }
}

@Composable
private fun BrowserButton(label: String, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled) { Text(label) }
}
