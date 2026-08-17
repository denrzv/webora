package app.webora.browser.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import app.webora.browser.R
import app.webora.browser.design.WeboraChrome
import app.webora.browser.design.WeboraRadius
import app.webora.browser.design.WeboraSpacing
import app.webora.browser.siteskin.BrowserMenuCommand
import app.webora.browser.siteskin.browserMenuCommands
import app.webora.browser.siteskin.browserMenuLabel

/** Direction A's regular-mode browser chrome. */
@Composable
internal fun BrowserChrome(
    state: BrowserState,
    onAddressChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val security = securityPresentation(state.mode, state.transport)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = WeboraSpacing.GUTTER, vertical = WeboraSpacing.SMALL),
        verticalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
    ) {
        AddressPill(state.addressText, onAddressChanged, onSubmit)
        security?.let { BrowserSecurityIdentity(it) }
    }
}

@Composable
private fun AddressPill(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit) {
    val label = stringResource(R.string.address_label)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(WeboraRadius.PILL),
        modifier = Modifier.fillMaxWidth().height(WeboraChrome.ADDRESS_HEIGHT),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = WeboraSpacing.LARGE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
        }
    }
}

/** The browser-authored identity remains separate from the editable address above it. */
@Composable
internal fun BrowserSecurityIdentity(security: SecurityPresentation) {
    val secure = security.transportSecurity == TransportSecurity.SECURE
    val transport = transportLabel(security.transportSecurity)
    val description = stringResource(R.string.security_description, transport, security.registrableDomain)
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(WeboraRadius.PILL),
        modifier = Modifier.semantics { contentDescription = description }.testTag(BROWSER_SECURITY_TAG),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = WeboraSpacing.MEDIUM, vertical = WeboraSpacing.SMALL),
            horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (secure) R.drawable.ic_lock else R.drawable.ic_warning),
                contentDescription = null,
            )
            Text(stringResource(R.string.security_identity, transport, security.registrableDomain))
        }
    }
}

@Composable
@Suppress("LongParameterList")
internal fun BrowserNavigationDock(
    canGoBack: Boolean,
    canGoForward: Boolean,
    canReload: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onSettings: () -> Unit,
    onInspector: () -> Unit,
    isFavourite: Boolean = false,
    onToggleFavourite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(WeboraRadius.PILL),
        modifier = modifier.height(WeboraChrome.DOCK_HEIGHT).testTag(BROWSER_NAVIGATION_DOCK_TAG),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrowserNavigationCommands(
                canGoBack, canGoForward, canReload, onBack, onForward, onReload, onHome, onTabs,
            ) { menuExpanded = true }
            BrowserOverflowMenu(
                menuExpanded,
                { menuExpanded = false },
                onTabs,
                onSettings,
                onInspector,
                isFavourite,
                onToggleFavourite,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun RowScope.BrowserNavigationCommands(
    canGoBack: Boolean,
    canGoForward: Boolean,
    canReload: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onMore: () -> Unit,
) {
    BrowserNavigationSlot("back") {
        WeboraIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack, enabled = canGoBack)
    }
    BrowserNavigationSlot("forward") {
        WeboraIconButton(
            R.drawable.ic_forward,
            stringResource(R.string.forward),
            onForward,
            enabled = canGoForward,
        )
    }
    BrowserNavigationSlot("reload") {
        WeboraIconButton(R.drawable.ic_reload, stringResource(R.string.reload), onReload, enabled = canReload)
    }
    BrowserNavigationSlot("home") {
        WeboraIconButton(R.drawable.ic_home, stringResource(R.string.home), onHome)
    }
    BrowserNavigationSlot("tabs") {
        WeboraIconButton(R.drawable.ic_tabs, stringResource(R.string.tabs), onTabs)
    }
    BrowserNavigationSlot("more") {
        WeboraIconButton(R.drawable.ic_more, stringResource(R.string.more), onMore)
    }
}

@Composable
private fun RowScope.BrowserNavigationSlot(name: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("$BROWSER_NAVIGATION_SLOT_TAG_PREFIX$name"),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Shared Home/regular placement for the browser-owned dock. */
@Composable
internal fun BrowserNavigationShell(
    canGoBack: Boolean,
    canGoForward: Boolean,
    canReload: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onSettings: () -> Unit,
    onInspector: () -> Unit,
    isFavourite: Boolean = false,
    onToggleFavourite: () -> Unit = {},
) {
    BrowserNavigationDock(
        canGoBack, canGoForward, canReload, onBack, onForward, onReload, onHome, onTabs,
        onSettings, onInspector, isFavourite, onToggleFavourite,
        Modifier
            .fillMaxWidth()
            .padding(horizontal = WeboraSpacing.LARGE, vertical = WeboraSpacing.SMALL)
            .testTag(BROWSER_NAVIGATION_SHELL_TAG),
    )
}

internal const val BROWSER_NAVIGATION_SHELL_TAG = "browser_navigation_shell"
internal const val BROWSER_NAVIGATION_DOCK_TAG = "browser_navigation_dock"
internal const val BROWSER_NAVIGATION_SLOT_TAG_PREFIX = "browser_navigation_slot_"

@Composable
private fun BrowserOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTabs: () -> Unit,
    onSettings: () -> Unit,
    onInspector: () -> Unit,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(if (isFavourite) R.string.remove_favourite else R.string.add_favourite)) },
            onClick = { onDismiss(); onToggleFavourite() },
        )
        browserMenuCommands().forEach { command ->
            DropdownMenuItem(
                text = { Text(browserMenuLabel(command)) },
                onClick = {
                    onDismiss()
                    when (command) {
                        BrowserMenuCommand.PAGE_INFORMATION -> Unit
                        BrowserMenuCommand.TABS -> onTabs()
                        BrowserMenuCommand.SETTINGS -> onSettings()
                        BrowserMenuCommand.INSPECTOR -> onInspector()
                    }
                },
            )
        }
    }
}

/** Browser-owned recovery surface, rendered over a failed main frame. */
@Composable
internal fun BrowserErrorPage(
    failure: BrowserLoadFailure,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(WeboraSpacing.GUTTER),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(WeboraChrome.ADDRESS_HEIGHT),
            )
            BrowserErrorCopy(failure)
            Row(horizontalArrangement = Arrangement.spacedBy(WeboraSpacing.SMALL)) {
                WeboraButton(
                    label = stringResource(R.string.retry),
                    onClick = onRetry,
                    enabled = failure.retryUrl != null,
                    modifier = Modifier.testTag(BROWSER_ERROR_RETRY_TAG),
                )
                WeboraButton(
                    label = stringResource(R.string.home),
                    onClick = onHome,
                    modifier = Modifier.testTag(BROWSER_ERROR_HOME_TAG),
                )
            }
        }
    }
}

@Composable
private fun BrowserErrorCopy(failure: BrowserLoadFailure) {
    Text(
        stringResource(R.string.error_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = WeboraSpacing.LARGE).semantics { heading() },
    )
    failure.registrableDomain?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Text(
        stringResource(failure.kind.messageResource()),
        modifier = Modifier.padding(vertical = WeboraSpacing.LARGE),
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun LoadErrorKind.messageResource(): Int = when (this) {
    LoadErrorKind.CONNECTION -> R.string.error_connection
    LoadErrorKind.NETWORK -> R.string.error_network
    LoadErrorKind.TLS -> R.string.error_tls
    LoadErrorKind.UNKNOWN -> R.string.error_unknown
}
