package app.webora.browser.browser

/** One browser-owned browsing context. Its id, unlike page data, is never website-controlled. */
@ConsistentCopyVisibility
internal data class BrowserTab internal constructor(
    val id: Long,
    val state: BrowserState,
)

/** A non-empty, bounded ordered set of independent browser tabs. */
internal class BrowserSession private constructor(
    val tabs: List<BrowserTab>,
    val activeId: Long,
    private val nextId: Long,
) {
    val activeTab: BrowserTab
        get() = checkNotNull(tab(activeId))

    val canCreateTab: Boolean
        get() = tabs.size < MAX_TABS

    fun tab(id: Long): BrowserTab? = tabs.firstOrNull { it.id == id }

    fun createTab(): BrowserSession {
        if (!canCreateTab) return this
        val tab = BrowserTab(nextId, BrowserState())
        return BrowserSession(tabs + tab, tab.id, nextAvailableId(nextId, tabs))
    }

    fun select(id: Long): BrowserSession =
        if (id == activeId || tab(id) == null) this else BrowserSession(tabs, id, nextId)

    fun close(id: Long): BrowserSession {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return this
        if (tabs.size == 1) {
            val replacement = BrowserTab(nextId, BrowserState())
            return BrowserSession(listOf(replacement), replacement.id, nextAvailableId(nextId, tabs))
        }
        val remaining = tabs.filterNot { it.id == id }
        val selected = if (id != activeId) activeId else remaining[index.coerceAtMost(remaining.lastIndex)].id
        return BrowserSession(remaining, selected, nextId)
    }

    fun update(id: Long, transform: (BrowserState) -> BrowserState): BrowserSession {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return this
        val updated = tabs.toMutableList()
        updated[index] = updated[index].copy(state = transform(updated[index].state))
        return BrowserSession(updated, activeId, nextId)
    }

    fun updateActive(transform: (BrowserState) -> BrowserState): BrowserSession = update(activeId, transform)

    override fun equals(other: Any?): Boolean =
        other is BrowserSession && tabs == other.tabs && activeId == other.activeId && nextId == other.nextId

    override fun hashCode(): Int = 31 * (31 * tabs.hashCode() + activeId.hashCode()) + nextId.hashCode()

    companion object {
        const val MAX_TABS = 8

        fun fresh(): BrowserSession = BrowserSession(listOf(BrowserTab(1, BrowserState())), 1, 2)

        internal fun restore(tabs: List<BrowserTab>, activeId: Long, nextId: Long): BrowserSession {
            if (tabs.isEmpty()) return fresh()
            val bounded = tabs.take(MAX_TABS)
            val selected = activeId.takeIf { wanted -> bounded.any { it.id == wanted } } ?: bounded.first().id
            val maximum = bounded.maxOfOrNull(BrowserTab::id) ?: 0
            val afterMaximum = if (maximum == Long.MAX_VALUE) 1 else maximum + 1
            val safeNext = maxOf(nextId.takeIf { it > 0 } ?: 1, afterMaximum)
            return BrowserSession(bounded, selected, safeNext)
        }

        private fun nextAvailableId(candidate: Long, existing: List<BrowserTab>): Long {
            val incremented = if (candidate == Long.MAX_VALUE) 1 else candidate + 1
            return generateSequence(incremented) { if (it == Long.MAX_VALUE) 1 else it + 1 }
                .first { id -> id > 0 && existing.none { it.id == id } }
        }
    }
}
