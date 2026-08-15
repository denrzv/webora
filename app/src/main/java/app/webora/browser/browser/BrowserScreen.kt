package app.webora.browser.browser

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.webora.browser.R
import app.webora.browser.design.WeboraSpacing
import app.webora.browser.siteskin.ManifestDiscoveryCoordinator
import app.webora.browser.siteskin.ManifestDiscoveryOutcome
import app.webora.browser.siteskin.OkHttpManifestSource
import app.webora.browser.siteskin.SiteConsentDecision
import app.webora.browser.siteskin.SiteConsentStore
import app.webora.browser.siteskin.SiteSkinCandidate
import app.webora.browser.siteskin.CandidateDisposition
import app.webora.browser.siteskin.candidateDisposition
import app.webora.browser.siteskin.isCurrent
import app.webora.browser.siteskin.BrandAsset
import app.webora.browser.siteskin.BrandAssetLoader
import app.webora.browser.siteskin.publishesBrandAsset
import app.webora.browser.siteskin.BitmapBrandAssetDecoder
import app.webora.browser.siteskin.OkHttpBrandAssetSource
import app.webora.browser.siteskin.SiteSkinBottomNavigation
import app.webora.browser.siteskin.SiteSkinChromeModel
import app.webora.browser.siteskin.SiteSkinQuickActions
import app.webora.browser.siteskin.SiteSkinMenu
import app.webora.browser.siteskin.SiteSkinConsentModel
import app.webora.browser.siteskin.SiteSkinTheme
import app.webora.browser.siteskin.scheme
import app.webora.browser.siteskin.SiteSkinTopBar
import app.webora.browser.siteskin.SiteSkinTopBarModel
import app.webora.browser.siteskin.brandMonogram
import app.webora.browser.siteskin.BrowserMenuCommand
import app.webora.browser.siteskin.browserMenuCommands
import app.webora.browser.siteskin.browserMenuLabel
import app.webora.browser.inspector.InspectorBrowserState
import app.webora.browser.inspector.SiteSkinInspectorHost
import app.webora.browser.inspector.SiteSkinTraceRecorder
import app.webora.browser.inspector.BrandAssetTraceSink
import app.webora.browser.inspector.SiteSkinTraceSink
import app.webora.browser.inspector.inspectorRecorder
import app.webora.browser.inspector.rememberInspectorSnapshot
import app.webora.browser.privacy.PrivacySettingsStore
import app.webora.browser.privacy.BrowsingDataCleaner
import app.webora.browser.web.BrowserWebViewController
import app.webora.browser.web.HardenedWebView
import app.webora.browser.web.WebViewEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import dev.siteskin.core.origin.SiteOrigin
import dev.siteskin.core.action.ActionResolver
import dev.siteskin.core.action.ResolvedAction
import dev.siteskin.core.model.NavigationItem
import dev.siteskin.core.model.SiteSkinConfiguration

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
internal fun BrowserScreen(
    modifier: Modifier = Modifier,
    onLaunchExternal: (ExternalNavigation) -> Boolean = { false },
    onDownload: (String) -> Boolean = { false },
    onFileChooser: (String, (String?) -> Unit) -> Unit = { _, complete -> complete(null) },
    onOpenExternalUrl: (String) -> Boolean = { false },
    onShare: (String) -> Boolean = { false },
) {
    val browserModifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    var session by rememberSaveable(stateSaver = browserSessionSaver()) {
        mutableStateOf(BrowserSession.fresh())
    }
    val activeTabId = session.activeId
    val state = session.activeTab.state
    val controllers = remember { mutableMapOf<Long, BrowserWebViewController>() }
    DisposableEffect(controllers) {
        onDispose {
            controllers.values.forEach(BrowserWebViewController::destroy)
            controllers.clear()
        }
    }
    val controller = controllers.getOrPut(activeTabId, ::BrowserWebViewController)
    val generations = remember { mutableMapOf<Long, Long>() }
    val generation = generations[activeTabId] ?: 0L
    var discoveryOwner by remember { mutableStateOf(activeTabId) }
    var pendingConsent by remember { mutableStateOf<Pair<Long, SiteSkinCandidate>?>(null) }
    var siteMenuExpanded by remember { mutableStateOf(false) }
    var tabsVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var clearConfirmation by remember { mutableStateOf(false) }
    var inspectorVisible by remember { mutableStateOf(false) }
    var pendingExternal by remember { mutableStateOf<ExternalNavigation?>(null) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val assetLoader = remember { BrandAssetLoader(OkHttpBrandAssetSource(), BitmapBrandAssetDecoder()) }
    var brandAsset by remember { mutableStateOf<Pair<SiteSkinConfiguration, BrandAsset>?>(null) }
    val consentStore = remember(context) { SiteConsentStore(context) }
    val privacyStore = remember(context) { PrivacySettingsStore(context) }
    val recordStore = remember(context) { BrowsingRecordStore(context) }
    val completedPages = remember { mutableMapOf<Long, String>() }
    var recordVersion by remember { mutableIntStateOf(0) }
    val currentRecordUrl = canonicalBrowsingUrl(state.displayedUrl)
    val currentIsFavourite = currentRecordUrl?.let(recordStore::isFavourite) == true
    val toggleFavourite = {
        currentRecordUrl?.let { url ->
            val observedTitle = recordStore.history().firstOrNull { it.url == url }?.title
            if (currentIsFavourite) recordStore.removeFavourite(url) else recordStore.addFavourite(url, observedTitle)
            recordVersion += 1
        }
        Unit
    }
    var siteSkinEnabled by remember { mutableStateOf(privacyStore.isSiteSkinEnabled()) }
    var storedDecisions by remember { mutableStateOf(consentStore.decisions()) }
    val traceRecorder = remember { inspectorRecorder() }
    // The recorder is a plain class so the JVM gate can drive it, which means Compose cannot
    // subscribe to it. This counter is the observation channel.
    var traceVersion by remember { mutableIntStateOf(0) }
    val traceSink = remember(traceRecorder) {
        traceRecorder?.let { recorder ->
            SiteSkinTraceSink { record ->
                recorder.record(record)
                traceVersion += 1
            }
        } ?: SiteSkinTraceSink.None
    }
    val brandAssetSink = remember(traceRecorder) {
        traceRecorder?.let { recorder ->
            BrandAssetTraceSink { origin, trace ->
                recorder.record(origin, trace)
                traceVersion += 1
            }
        } ?: BrandAssetTraceSink.None
    }
    val manifestDiscovery = rememberManifestDiscovery(scope, traceSink) { outcome ->
        val ownerState = session.tab(discoveryOwner)?.state ?: return@rememberManifestDiscovery
        val origin = when (val mode = ownerState.mode) {
            BrowserMode.Home -> null
            is BrowserMode.Regular -> mode.origin
            is BrowserMode.Integrated -> mode.origin
        }
        val decision = outcome.origin?.let(consentStore::decision)
        val ownerGeneration = generations[discoveryOwner] ?: 0L
        when (val disposition = candidateDisposition(outcome, origin, ownerGeneration, decision, siteSkinEnabled)) {
            is CandidateDisposition.Activate -> {
                session = session.update(discoveryOwner) {
                    it.activateSiteSkin(disposition.candidate.origin, disposition.candidate.configuration)
                }
            }
            is CandidateDisposition.Ask -> pendingConsent = discoveryOwner to disposition.candidate
            CandidateDisposition.Ignore -> Unit
        }
    }
    val downloadMessages = stringResource(R.string.download_started) to stringResource(R.string.download_failed)
    val integrated = state.mode as? BrowserMode.Integrated
    val currentBrandAsset = integrated?.configuration?.let { configuration ->
        brandAsset?.takeIf { it.first === configuration }?.second
            ?: BrandAsset.Monogram(brandMonogram(configuration.site.shortName, configuration.site.name))
    }
    LaunchedEffect(integrated?.configuration) {
        val configuration = integrated?.configuration
        brandAsset = configuration?.let {
            it to BrandAsset.Monogram(brandMonogram(it.site.shortName, it.site.name))
        }
        if (configuration != null) {
            val loaded = assetLoader.load(configuration)
            // Recorded whether or not it publishes: a load dropped for being superseded is one of
            // the things a developer needs to be able to see, and the guard is what would hide it.
            brandAssetSink.record(configuration.origin, loaded.trace)
            if (publishesBrandAsset(state.mode, configuration)) brandAsset = configuration to loaded.asset
        }
    }

    BrowserBackHandler(enabled = state.canGoBack, controller = controller)
    if (state.mode == BrowserMode.Home) {
        Column(browserModifier) {
            HomeScreen(
                onNavigate = { url -> session = session.updateActive { it.navigateFromHome(url) } },
                recents = recordVersion.let { recordStore.recentSites() },
                favourites = recordVersion.let { recordStore.favourites() },
                onRemoveFavourite = { url ->
                    if (recordStore.removeFavourite(url)) recordVersion += 1
                },
                modifier = Modifier.weight(1f),
            )
            BrowserNavigationShell(
                canGoBack = false,
                canGoForward = false,
                canReload = false,
                onBack = {},
                onForward = {},
                onReload = {},
                onHome = {},
                onTabs = { tabsVisible = true },
                onSettings = { settingsVisible = true },
                onInspector = { inspectorVisible = true },
            )
        }
    } else {
        val dispatchSiteItem: (NavigationItem) -> Unit = { item ->
        val mode = state.mode as? BrowserMode.Integrated
        val resolved = mode?.let { ActionResolver.resolve(item.action, it.configuration.site, state.displayedUrl) }
        when (resolved) {
            is ResolvedAction.NavigateInternal -> controller.navigate(resolved.url)
            is ResolvedAction.NavigateExternal -> pendingExternalUrl = resolved.url
            is ResolvedAction.Dial -> externalNavigation(resolved.value)?.let { pendingExternal = it }
            is ResolvedAction.ComposeEmail -> externalNavigation(resolved.value)?.let { pendingExternal = it }
            is ResolvedAction.OpenMap -> externalNavigation(resolved.value)?.let { pendingExternal = it }
            is ResolvedAction.Share -> onShare(resolved.pageUrl)
            ResolvedAction.Refresh -> controller.reload()
            ResolvedAction.OpenMenu -> siteMenuExpanded = true
            null -> Unit
        }
        }
    RegularBrowser(
        state = state,
        controller = controller,
        onObservation = { observation ->
            session = session.update(activeTabId) { it.observe(observation) }
        },
        onHome = { session = session.updateActive { BrowserState() } },
        onExternalNavigation = { pendingExternal = it },
        onDownload = { url ->
            val message = if (onDownload(url)) downloadMessages.first else downloadMessages.second
            scope.launch { snackbar.showSnackbar(message) }
        },
        onFileChooser = onFileChooser,
        brandAsset = currentBrandAsset,
        onSiteSelect = dispatchSiteItem,
        onPageStarted = { url ->
            completedPages.remove(activeTabId)
            val nextGeneration = generation + 1
            generations[activeTabId] = nextGeneration
            discoveryOwner = activeTabId
            pendingConsent = null
            if (siteSkinEnabled) manifestDiscovery.onPageStarted(url, nextGeneration)
        },
        onPageCompleted = { url, title ->
            val canonical = canonicalBrowsingUrl(url)
            if (canonical != null && completedPages[activeTabId] != canonical) {
                recordStore.recordVisit(canonical, title)
                completedPages[activeTabId] = canonical
            }
        },
        onTabs = { tabsVisible = true },
        onSettings = { settingsVisible = true },
        onInspector = { inspectorVisible = true },
        isFavourite = currentIsFavourite,
        onToggleFavourite = toggleFavourite,
        modifier = browserModifier,
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
    pendingExternalUrl?.let { url -> ExternalUrlDialog(
        onConfirm = {
            onOpenExternalUrl(url)
            pendingExternalUrl = null
        },
        onDismiss = { pendingExternalUrl = null },
    ) }
    pendingConsent?.takeIf { it.first == activeTabId }?.second?.let { candidate -> SiteSkinConsentDialog(
        origin = candidate.origin.canonical,
        model = SiteSkinConsentModel.from(candidate.configuration, isSystemInDarkTheme()),
        onAllow = {
            consentStore.save(candidate.origin, SiteConsentDecision.ALLOW)
            storedDecisions = consentStore.decisions()
            val currentOrigin = when (val mode = state.mode) {
                is BrowserMode.Regular -> mode.origin
                is BrowserMode.Integrated -> mode.origin
                BrowserMode.Home -> null
            }
            if (siteSkinEnabled && candidate.isCurrent(currentOrigin, generation)) {
                session = session.updateActive {
                    it.activateSiteSkin(candidate.origin, candidate.configuration)
                }
            }
            pendingConsent = null
        },
        onNotNow = { pendingConsent = null },
        onNever = {
            consentStore.save(candidate.origin, SiteConsentDecision.NEVER)
            storedDecisions = consentStore.decisions()
            pendingConsent = null
        },
    ) }
    val activeMode = state.mode as? BrowserMode.Integrated
    if (siteMenuExpanded && activeMode != null) {
        val chrome = SiteSkinChromeModel.from(activeMode.configuration, state.displayedUrl)
        SiteSkinMenu(
            model = chrome,
            isFavourite = currentIsFavourite,
            onToggleFavourite = toggleFavourite,
            onSiteSelect = { item ->
                siteMenuExpanded = false
                dispatchSiteItem(item)
            },
            onBrowserSelect = { command ->
                siteMenuExpanded = false
                when (command) {
                    BrowserMenuCommand.PAGE_INFORMATION -> Unit
                    BrowserMenuCommand.TABS -> tabsVisible = true
                    BrowserMenuCommand.SETTINGS -> settingsVisible = true
                    BrowserMenuCommand.INSPECTOR -> inspectorVisible = true
                }
            },
        )
    }
    }
    if (tabsVisible) {
        BrowserTabSwitcher(
            session = session,
            controllers = controllers,
            generations = generations,
            onSessionChange = { changed ->
                session = changed
                pendingConsent = null
                pendingExternal = null
                pendingExternalUrl = null
                siteMenuExpanded = false
            },
            onDismiss = { tabsVisible = false },
        )
    }
    if (settingsVisible) {
        PrivacySettingsScreen(
            siteSkinEnabled = siteSkinEnabled,
            decisions = storedDecisions,
            onSiteSkinEnabledChange = { enabled ->
                privacyStore.setSiteSkinEnabled(enabled)
                siteSkinEnabled = enabled
                if (!enabled) {
                    manifestDiscovery.cancel()
                    pendingConsent = null
                    session = session.updateActive { it.deactivateSiteSkin() }
                }
            },
            onRemoveDecision = { stored ->
                consentStore.remove(stored.origin)
                storedDecisions = consentStore.decisions()
            },
            onClearBrowsingData = { clearConfirmation = true },
            onClose = { settingsVisible = false },
            modifier = browserModifier,
        )
    }
    SiteSkinInspectorHost(
        rememberInspectorSnapshot(
            recorder = traceRecorder,
            version = traceVersion,
            state = InspectorBrowserState(
                origin = state.mode.observedOrigin(),
                pageUrl = state.displayedUrl,
                configuration = (state.mode as? BrowserMode.Integrated)?.configuration,
                consent = state.mode.observedOrigin()?.let(consentStore::decision),
                siteSkinEnabled = siteSkinEnabled,
                brandAsset = currentBrandAsset,
                darkTheme = isSystemInDarkTheme(),
            ),
        ),
        open = inspectorVisible,
        onClose = { inspectorVisible = false },
    )
    if (clearConfirmation) {
        val clearedMessage = stringResource(R.string.browsing_data_cleared)
        val incompleteMessage = stringResource(R.string.browsing_data_clear_incomplete)
        ClearBrowsingDataDialog(
            onConfirm = {
                clearConfirmation = false
                scope.launch {
                    val cleaner = BrowsingDataCleaner
                        .android(controllers.values, manifestDiscovery, consentStore, recordStore, traceRecorder)
                    val complete = cleaner.clear()
                    recordVersion += 1
                    storedDecisions = consentStore.decisions()
                    session = session.updateActive { it.deactivateSiteSkin() }
                    snackbar.showSnackbar(if (complete) clearedMessage else incompleteMessage)
                }
            },
            onDismiss = { clearConfirmation = false },
        )
    }
}

@Composable
private fun BrowserTabSwitcher(
    session: BrowserSession,
    controllers: MutableMap<Long, BrowserWebViewController>,
    generations: MutableMap<Long, Long>,
    onSessionChange: (BrowserSession) -> Unit,
    onDismiss: () -> Unit,
) {
    TabSwitcher(
        session = session,
        onSelect = { id -> onSessionChange(session.select(id)); onDismiss() },
        onCloseTab = { id ->
            controllers.remove(id)?.destroy()
            generations.remove(id)
            onSessionChange(session.close(id))
        },
        onNewTab = { onSessionChange(session.createTab()); onDismiss() },
        onDismiss = onDismiss,
    )
}

private fun browserSessionSaver(): Saver<BrowserSession, Bundle> = Saver(
    save = { session ->
        val snapshot = BrowserSessionSnapshot.from(session)
        Bundle().apply {
            putInt("version", snapshot.version)
            putLong("active", snapshot.activeId)
            putLong("next", snapshot.nextId)
            putLongArray("ids", snapshot.entries.map(BrowserTabSnapshot::id).toLongArray())
            putStringArrayList("kinds", ArrayList(snapshot.entries.map { it.kind.name }))
            putStringArrayList("urls", ArrayList(snapshot.entries.map { it.url.orEmpty() }))
        }
    },
    restore = { bundle ->
        val ids = bundle.getLongArray("ids") ?: longArrayOf()
        val kinds = bundle.getStringArrayList("kinds").orEmpty()
        val urls = bundle.getStringArrayList("urls").orEmpty()
        val entries = ids.indices.mapNotNull { index ->
            val kind = kinds.getOrNull(index)?.let { runCatching { BrowserTabKind.valueOf(it) }.getOrNull() }
                ?: return@mapNotNull null
            BrowserTabSnapshot(ids[index], kind, urls.getOrNull(index)?.ifEmpty { null })
        }
        BrowserSessionSnapshot.restore(
            BrowserSessionSnapshot(
                bundle.getInt("version"),
                bundle.getLong("active"),
                bundle.getLong("next"),
                entries,
            ),
        )
    },
)

/** The committed origin, whichever mode the browser is in. Home has none rather than a blank one. */
private fun BrowserMode.observedOrigin(): SiteOrigin? = when (this) {
    BrowserMode.Home -> null
    is BrowserMode.Regular -> origin
    is BrowserMode.Integrated -> origin
}

@Composable
internal fun ExternalUrlDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_url_title)) },
        text = { Text(stringResource(R.string.external_url_message)) },
        confirmButton = { WeboraButton(stringResource(R.string.open_external), onConfirm) },
        dismissButton = { WeboraButton(stringResource(R.string.cancel), onDismiss) },
    )
}

@Composable
private fun rememberManifestDiscovery(
    scope: CoroutineScope,
    trace: SiteSkinTraceSink,
    onOutcome: (ManifestDiscoveryOutcome) -> Unit,
): ManifestDiscoveryCoordinator {
    val currentOutcome = rememberUpdatedState(onOutcome)
    val discovery = remember(scope, trace) {
        ManifestDiscoveryCoordinator(scope, OkHttpManifestSource(), trace = trace) {
            currentOutcome.value(it)
        }
    }
    DisposableEffect(discovery) { onDispose(discovery::cancel) }
    return discovery
}

@Composable
internal fun SiteSkinConsentDialog(
    origin: String,
    model: SiteSkinConsentModel,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    onNever: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.siteskin_consent_title, origin)) },
        text = { SiteSkinConsentDetails(model) },
        confirmButton = { SiteSkinConsentActions(onAllow, onNotNow, onNever) },
    )
}

@Composable
private fun SiteSkinConsentActions(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    onNever: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WeboraButton(
            label = stringResource(R.string.siteskin_allow),
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth(),
        )
        WeboraOutlinedButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.siteskin_not_now))
        }
        WeboraTextButton(onClick = onNever, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.siteskin_never))
        }
    }
}

@Composable
private fun SiteSkinConsentDetails(model: SiteSkinConsentModel) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.siteskin_consent_message))
        Text(
            stringResource(R.string.siteskin_consent_site_heading),
            modifier = Modifier.semantics { heading() },
        )
        Text(model.title)
        model.subtitle?.let { Text(it) }
        model.brandColor?.let { color ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(CONSENT_SWATCH_SIZE)
                        .background(color)
                        .clearAndSetSemantics { },
                )
                Text(stringResource(R.string.siteskin_consent_brand_color))
            }
        }
        ConsentCount(R.plurals.siteskin_consent_navigation_count, model.navigationCount)
        ConsentCount(R.plurals.siteskin_consent_quick_action_count, model.quickActionCount)
        ConsentCount(R.plurals.siteskin_consent_menu_count, model.menuCount)
    }
}

@Composable
private fun ConsentCount(resource: Int, count: Int) {
    Text(pluralStringResource(resource, count, count))
}

private val CONSENT_SWATCH_SIZE = 24.dp

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
        confirmButton = { WeboraButton(stringResource(R.string.open_external), onConfirm) },
        dismissButton = { WeboraButton(stringResource(R.string.cancel), onDismiss) },
    )
}

@Composable
@Suppress("LongMethod")
internal fun RegularBrowser(
    state: BrowserState,
    controller: BrowserWebViewController,
    onObservation: (BrowserObservation) -> Unit,
    onHome: () -> Unit,
    onExternalNavigation: (ExternalNavigation) -> Unit,
    onDownload: (String) -> Unit,
    onFileChooser: (String, (String?) -> Unit) -> Unit,
    brandAsset: BrandAsset?,
    onSiteSelect: (NavigationItem) -> Unit,
    onPageStarted: (String) -> Unit,
    onPageCompleted: (String, String?) -> Unit,
    onTabs: () -> Unit,
    // Neither handler defaults to a no-op. A browser-owned menu command that is offered and does
    // nothing is the same failure the offered list exists to prevent, one layer down.
    onSettings: () -> Unit,
    onInspector: () -> Unit,
    isFavourite: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        val integrated = state.mode as? BrowserMode.Integrated
        val handoff = state.mode.chromeHandoff()
        val security = securityPresentation(state.mode)
        when (handoff.top) {
            TopChrome.NONE -> Unit
            TopChrome.REGULAR -> BrowserChrome(
                    state = state,
                    onAddressChanged = { onObservation(BrowserObservation.AddressEdited(it)) },
                    onSubmit = { resolveAddressInput(state.addressText)?.let(controller::navigate) },
                )
            TopChrome.PROTECTED_INTEGRATED -> {
                val mode = checkNotNull(integrated)
                val identity = checkNotNull(security)
                val asset = brandAsset ?: BrandAsset.Monogram(
                    brandMonogram(mode.configuration.site.shortName, mode.configuration.site.name),
                )
                val colors = SiteSkinTheme.from(mode.configuration).scheme(isSystemInDarkTheme())
                SiteSkinTopBar(
                    model = SiteSkinTopBarModel.from(mode.configuration, asset, identity),
                    colors = colors,
                    canGoBack = state.canGoBack,
                    onBack = controller::goBack,
                )
            }
        }
        BrowserStatusRegion(state)
        Box(Modifier.fillMaxWidth().weight(1f).testTag(BROWSER_CONTENT_TAG)) {
            HardenedWebView(
                initialUrl = state.displayedUrl,
                controller = controller,
                onEvent = { handleWebViewEvent(it, onObservation, onPageStarted, onPageCompleted) },
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
            if (handoff.contentActions == ContentActions.SITESKIN && integrated != null) {
                val chrome = SiteSkinChromeModel.from(integrated.configuration, state.displayedUrl)
                SiteSkinQuickActions(chrome.quickActions, onSiteSelect)
            }
        }
        if (handoff.bottom == BottomChrome.SITESKIN && integrated != null) {
            val chrome = SiteSkinChromeModel.from(integrated.configuration, state.displayedUrl)
            SiteSkinBottomNavigation(chrome.bottomNavigation, onSiteSelect)
        } else {
            BrowserNavigationShell(
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                canReload = state.displayedUrl.isNotBlank(),
                onBack = controller::goBack,
                onForward = controller::goForward,
                onReload = controller::reload,
                onHome = onHome,
                onTabs = onTabs,
                onSettings = onSettings,
                onInspector = onInspector,
                isFavourite = isFavourite,
                onToggleFavourite = onToggleFavourite,
            )
        }
    }
}

private fun handleWebViewEvent(
    event: WebViewEvent,
    onObservation: (BrowserObservation) -> Unit,
    onPageStarted: (String) -> Unit,
    onPageCompleted: (String, String?) -> Unit,
) {
    onObservation(event.toBrowserObservation())
    if (event is WebViewEvent.PageStarted) onPageStarted(event.observation.url)
    if (event is WebViewEvent.MainFrameCompleted) onPageCompleted(event.observation.url, event.title)
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
    is WebViewEvent.MainFrameCompleted -> BrowserObservation.Page(
        url = observation.url,
        isLoading = false,
        canGoBack = observation.canGoBack,
        canGoForward = observation.canGoForward,
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


/**
 * A persistent region that announces load progress and failure.
 *
 * Persistent on purpose: hanging the live region on the progress indicator would destroy the node
 * the moment loading finished, and a destroyed node cannot announce that it finished.
 */
@Composable
private fun BrowserStatusRegion(state: BrowserState) {
    val announcement = browserAnnouncement(state)
    val text = when (announcement) {
        BrowserAnnouncement.LOADING -> stringResource(R.string.browser_loading)
        BrowserAnnouncement.LOADED -> stringResource(R.string.browser_loaded)
        BrowserAnnouncement.FAILED -> stringResource(R.string.browser_load_failed)
        null -> null
    }
    val region = Modifier
        .fillMaxWidth()
        .height(STATUS_REGION_HEIGHT)
        .testTag(BROWSER_STATUS_TAG)
    // No announcement means no semantics at all. An empty description is not silence: it publishes
    // a nameless node into the accessibility tree, the same mistake as a blank security claim.
    Box(
        if (text == null || announcement == null) {
            region
        } else {
            region.semantics {
                liveRegion = announcement.liveRegionMode()
                contentDescription = text
            }
        },
    ) {
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

internal const val BROWSER_SECURITY_TAG = "browser_security"
internal const val BROWSER_STATUS_TAG = "browser_status"

/**
 * The page rectangle — the renderer and any error page, and none of the chrome.
 *
 * `CI-003`'s screenshot check measures this region to decide whether the page has actually been
 * drawn. Excluding the chrome is the point: a frame must not be able to pass that check on the
 * strength of its own navigation bar.
 */
internal const val BROWSER_CONTENT_TAG = "browser_content"
private val STATUS_REGION_HEIGHT = 4.dp
internal const val BROWSER_ERROR_RETRY_TAG = "browser_error_retry"
internal const val BROWSER_ERROR_HOME_TAG = "browser_error_home"

@Composable
private fun BrowserButton(label: String, enabled: Boolean, action: () -> Unit) {
    WeboraButton(label = label, onClick = action, enabled = enabled)
}
