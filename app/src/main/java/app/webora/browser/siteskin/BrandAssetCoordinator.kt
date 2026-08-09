package app.webora.browser.siteskin

import dev.siteskin.core.model.SiteSkinConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class BrandAssetCoordinator(
    private val scope: CoroutineScope,
    private val loadAsset: suspend (SiteSkinConfiguration) -> BrandAsset,
    private val onAsset: (BrandAsset) -> Unit,
) {
    private var loadJob: Job? = null

    fun load(configuration: SiteSkinConfiguration) {
        loadJob?.cancel()
        loadJob = scope.launch {
            val asset = loadAsset(configuration)
            ensureActive()
            onAsset(asset)
        }
    }

    fun cancel() {
        loadJob?.cancel()
    }
}
