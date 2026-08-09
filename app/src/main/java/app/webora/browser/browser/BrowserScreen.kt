package app.webora.browser.browser

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import app.webora.browser.R
import app.webora.browser.siteskin.ManifestDiscoveryCoordinator
import app.webora.browser.siteskin.OkHttpManifestSource
import app.webora.browser.web.BrowserWebViewController
import app.webora.browser.web.HardenedWebView
import app.webora.browser.web.WebViewEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun BrowserScreen(
    modifier: Modifier = Modifier,
    onLaunchExternal: (ExternalNavigation) -> Boolean = { false },
    onDownload: (String) -> Boolean = { false },
    onFileChooser: (String, (String?) -> Unit) -> Unit = { _, complete -> complete(null) },
) {
    val controller = remember { BrowserWebViewController() }
    var state by remember { mutableStateOf(BrowserState()) }
    var pendingExternal by remember { mutableStateOf<ExternalNavigation?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val manifestDiscovery = rememberManifestDiscovery(scope)
    val downloadMessages = stringResource(R.string.download_started) to stringResource(R.string.download_failed)

    BrowserBackHandler(enabled = state.canGoBack, controller = controller)
    if (state.mode == BrowserMode.Home) {
        HomeScreen(
            onNavigate = { state = state.navigateFromHome(it) },
            modifier = modifier,
        )
        return
    }
    RegularBrowser(
        state = state,
        controller = controller,
        onObservation = { state = state.observe(it) },
        onHome = { state = BrowserState() },
        onExternalNavigation = { pendingExternal = it },
        onDownload = { url ->
            val message = if (onDownload(url)) downloadMessages.first else downloadMessages.second
            scope.launch { snackbar.showSnackbar(message) }
        },
        onFileChooser = onFileChooser,
        onPageStarted = manifestDiscovery::onPageStarted,
        modifier = modifier,
    )
    SnackbarHost(snackbar)
    pendingExternal?.let { navigation -> ExternalNavigationDialog(
        navigation = navigation,
        onConfirm = {
            onLaunchExternal(navigation)
            pendingExternal = null
        },
        onDismiss = { pendingExternal = null },
    ) }
}

@Composable
private fun rememberManifestDiscovery(scope: CoroutineScope): ManifestDiscoveryCoordinator {
    val discovery = remember(scope) { ManifestDiscoveryCoordinator(scope, OkHttpManifestSource()) {} }
    DisposableEffect(discovery) { onDispose(discovery::cancel) }
    return discovery
}

@Composable
private fun ExternalNavigationDialog(
    navigation: ExternalNavigation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_navigation_title)) },
        text = { Text(stringResource(R.string.external_navigation_message, navigation.kind.name.lowercase())) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.open_external)) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RegularBrowser(
    state: BrowserState,
    controller: BrowserWebViewController,
    onObservation: (BrowserObservation) -> Unit,
    onHome: () -> Unit,
    onExternalNavigation: (ExternalNavigation) -> Unit,
    onDownload: (String) -> Unit,
    onFileChooser: (String, (String?) -> Unit) -> Unit,
    onPageStarted: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        AddressBar(
            state = state,
            onAddressChanged = { onObservation(BrowserObservation.AddressEdited(it)) },
            onSubmit = { resolveAddressInput(state.addressText)?.let(controller::navigate) },
            onBack = controller::goBack,
            onForward = controller::goForward,
            onReload = controller::reload,
            onHome = onHome,
        )
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.fillMaxSize()) {
            HardenedWebView(
                initialUrl = state.displayedUrl,
                controller = controller,
                onEvent = {
                    if (it is WebViewEvent.PageStarted) onPageStarted(it.observation.url)
                    onObservation(it.toBrowserObservation())
                },
                onExternalNavigation = onExternalNavigation,
                onDownload = onDownload,
                onFileChooser = onFileChooser,
                modifier = Modifier.fillMaxSize(),
            )
            state.loadFailure?.let { failure ->
                BrowserErrorPage(
                    failure = failure,
                    onRetry = { failure.retryUrl?.let(controller::navigate) },
                    onHome = onHome,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun WebViewEvent.toBrowserObservation(): BrowserObservation = when (this) {
    is WebViewEvent.MainFrameFailed -> BrowserObservation.PageFailed(url, kind)
    is WebViewEvent.PageStarted -> BrowserObservation.PageStarted(
        observation.url,
        observation.canGoBack,
        observation.canGoForward,
    )
    is WebViewEvent.PageChanged -> BrowserObservation.Page(
        observation.url,
        observation.isLoading,
        observation.canGoBack,
        observation.canGoForward,
    )
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
    onHome: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val security = securityPresentation(state.mode)
    Column {
        OutlinedTextField(
            value = state.addressText,
            onValueChange = onAddressChanged,
            label = { Text(stringResource(R.string.address_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        security?.let {
            val transport = if (it.transportSecurity == TransportSecurity.SECURE) {
                stringResource(R.string.security_secure)
            } else {
                stringResource(R.string.security_not_secure)
            }
            Text(stringResource(R.string.security_identity, transport, it.registrableDomain))
        }
        Row {
            BrowserButton(stringResource(R.string.back), state.canGoBack, onBack)
            BrowserButton(stringResource(R.string.forward), state.canGoForward, onForward)
            BrowserButton(stringResource(R.string.reload), true, onReload)
            BrowserButton(stringResource(R.string.home), true, onHome)
            Button(onClick = { menuExpanded = true }) { Text(stringResource(R.string.more)) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.page_information)) },
                    onClick = { menuExpanded = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = { menuExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun BrowserErrorPage(
    failure: BrowserLoadFailure,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Column {
            Text(stringResource(R.string.error_title))
            failure.registrableDomain?.let { Text(it) }
            Text(
                stringResource(
                    when (failure.kind) {
                        LoadErrorKind.CONNECTION -> R.string.error_connection
                        LoadErrorKind.NETWORK -> R.string.error_network
                        LoadErrorKind.TLS -> R.string.error_tls
                        LoadErrorKind.UNKNOWN -> R.string.error_unknown
                    },
                ),
            )
            Button(
                onClick = onRetry,
                enabled = failure.retryUrl != null,
                modifier = Modifier.testTag(BROWSER_ERROR_RETRY_TAG),
            ) {
                Text(stringResource(R.string.retry))
            }
            Button(
                onClick = onHome,
                modifier = Modifier.testTag(BROWSER_ERROR_HOME_TAG),
            ) {
                Text(stringResource(R.string.home))
            }
        }
    }
}

internal const val BROWSER_ERROR_RETRY_TAG = "browser_error_retry"
internal const val BROWSER_ERROR_HOME_TAG = "browser_error_home"

@Composable
private fun BrowserButton(label: String, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled) { Text(label) }
}
