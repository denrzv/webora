package app.webora.browser.browser

import dev.siteskin.core.origin.SiteOrigin

internal enum class BrowserTabKind { HOME, PAGE }

internal data class BrowserTabSnapshot(
    val id: Long,
    val kind: BrowserTabKind,
    val url: String?,
)

/** Versioned browser-owned projection suitable for primitive Android saved state. */
internal data class BrowserSessionSnapshot(
    val version: Int,
    val activeId: Long,
    val nextId: Long,
    val entries: List<BrowserTabSnapshot>,
) {
    companion object {
        const val VERSION = 1
        const val MAX_URL_LENGTH = 2_048

        fun from(session: BrowserSession): BrowserSessionSnapshot = BrowserSessionSnapshot(
            version = VERSION,
            activeId = session.activeId,
            nextId = session.tabs.maxOfOrNull(BrowserTab::id)
                ?.takeUnless { it == Long.MAX_VALUE }
                ?.plus(1)
                ?: 1,
            entries = session.tabs.map { tab -> tab.toSnapshot() },
        )

        fun restore(snapshot: BrowserSessionSnapshot?): BrowserSession {
            if (snapshot?.version != VERSION) return BrowserSession.fresh()
            val ids = mutableSetOf<Long>()
            val tabs = snapshot.entries.asSequence()
                .take(BrowserSession.MAX_TABS)
                .mapNotNull { entry -> entry.toTab()?.takeIf { ids.add(it.id) } }
                .toList()
            return BrowserSession.restore(tabs, snapshot.activeId, snapshot.nextId)
        }
    }
}

private fun BrowserTab.toSnapshot(): BrowserTabSnapshot = when (state.mode) {
    BrowserMode.Home -> BrowserTabSnapshot(id, BrowserTabKind.HOME, null)
    is BrowserMode.Regular, is BrowserMode.Integrated -> BrowserTabSnapshot(
        id,
        BrowserTabKind.PAGE,
        state.displayedUrl.takeIf(::isRestorablePageUrl),
    )
}

private fun BrowserTabSnapshot.toTab(): BrowserTab? {
    if (id <= 0) return null
    val state = when (kind) {
        BrowserTabKind.HOME -> BrowserState()
        BrowserTabKind.PAGE -> url?.takeIf(::isRestorablePageUrl)?.let { restoredUrl ->
            BrowserState().navigateFromHome(restoredUrl).copy(isLoading = false)
        } ?: return null
    }
    return BrowserTab(id, state)
}

private fun isRestorablePageUrl(url: String): Boolean {
    if (url.length > BrowserSessionSnapshot.MAX_URL_LENGTH) return false
    val origin = SiteOrigin.parse(url) ?: return false
    return origin.scheme == "http" || origin.scheme == "https"
}
