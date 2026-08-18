package app.webora.browser.siteskin

/**
 * The browser's own navigation commands, as a closed vocabulary.
 *
 * Three constants, and a fourth is a compile error at every exhaustive `when` that dispatches them.
 * `UX-024` puts these inside a header a manifest paints, so what stops a website reaching them is
 * not that the composable is careful — it is that there is no type through which a manifest value
 * could become one of these. A site item is a `NavigationItem` resolved by `ActionResolver`; a
 * browser command is this enum dispatched by a `when`. The two paths never meet.
 */
internal enum class BrowserNavigationCommand {
    BACK,
    FORWARD,
    REFRESH,
}

/**
 * One command and whether the browser's own observations currently permit it.
 *
 * Deliberately carries **no icon, no label and no payload**. `UX-015`'s `SiteSkinItemModel` carries
 * all three because a website supplies them; this carries none because nothing outside the browser
 * has anything to say here. The two bouquets share a visual vocabulary by design — the issue asks
 * for exactly that — and the moment they shared an item type a manifest could publish something that
 * renders identically to Back. They share no type, and `BrowserNavigationCommandsTest` reads the
 * declared fields reflectively so a field added later is covered without anyone remembering.
 */
internal data class BrowserNavigationAction(
    val command: BrowserNavigationCommand,
    val enabled: Boolean,
)

/**
 * The three commands the integrated navigation hub offers, in compiled order.
 *
 * Total: every input returns all three, so the hub cannot be emptied, shortened or reordered by any
 * runtime condition. Only the `enabled` flags move, and each one comes from a browser-observed fact
 * about the selected tab — `BrowserMode.canNavigateBack()`, `BrowserState.canGoForward`, and
 * `refreshAction(state) != RefreshAction.None`. None of the three is a manifest value, and none of
 * them is read here: this function takes the answers, so a call site cannot smuggle a different
 * question in.
 *
 * The shape is `browserMenuCommands()`'s (`DEVX-003`): one expression built from parameters rather
 * than a list assembled inside a composable, because the JVM gate cannot enter a composable and a
 * decision it cannot drive is a decision with no negative control.
 */
internal fun browserNavigationActions(
    canGoBack: Boolean,
    canGoForward: Boolean,
    canRefresh: Boolean,
): List<BrowserNavigationAction> = listOf(
    BrowserNavigationAction(BrowserNavigationCommand.BACK, canGoBack),
    BrowserNavigationAction(BrowserNavigationCommand.FORWARD, canGoForward),
    BrowserNavigationAction(BrowserNavigationCommand.REFRESH, canRefresh),
)
