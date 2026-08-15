package app.webora.browser.browser

/** Whether Back has a browser-owned destination before Android handles app exit. */
internal fun BrowserMode.canNavigateBack(): Boolean = this != BrowserMode.Home

/**
 * Consumes browser Back using live renderer history first and native Home second.
 *
 * The caller owns both effects. In particular, [navigateHistory] must consult the currently
 * attached WebView rather than relying only on a possibly delayed state observation.
 */
internal fun navigateBrowserBack(
    mode: BrowserMode,
    navigateHistory: () -> Boolean,
    navigateHome: () -> Unit,
): Boolean {
    if (!mode.canNavigateBack()) return false
    if (!navigateHistory()) navigateHome()
    return true
}
