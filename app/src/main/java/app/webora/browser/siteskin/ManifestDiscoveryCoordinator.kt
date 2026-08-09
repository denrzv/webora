package app.webora.browser.siteskin

import dev.siteskin.core.ManifestSource
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal sealed interface ManifestDiscoveryOutcome {
    data class Available(val validation: SiteSkinValidationOutcome.Accepted) : ManifestDiscoveryOutcome
    data object Unavailable : ManifestDiscoveryOutcome
}

internal class ManifestDiscoveryCoordinator(
    private val scope: CoroutineScope,
    private val source: ManifestSource,
    private val onOutcome: (ManifestDiscoveryOutcome) -> Unit,
) {
    private var discoveryJob: Job? = null

    fun onPageStarted(pageUrl: String) {
        val origin = SiteOrigin.parse(pageUrl)?.takeIf { it.scheme == HTTPS }
        discoveryJob?.cancel()
        if (origin == null) {
            onOutcome(ManifestDiscoveryOutcome.Unavailable)
            return
        }
        discoveryJob = scope.launch {
            val bytes = source.fetch(origin.canonical)
            ensureActive()
            onOutcome(validate(bytes, origin.canonical))
        }
    }

    fun cancel() {
        discoveryJob?.cancel()
    }

    private fun validate(bytes: ByteArray?, origin: String): ManifestDiscoveryOutcome {
        if (bytes == null) return ManifestDiscoveryOutcome.Unavailable
        return when (val validation = SiteSkinValidator.validate(bytes.inputStream(), origin)) {
            is SiteSkinValidationOutcome.Accepted -> ManifestDiscoveryOutcome.Available(validation)
            is SiteSkinValidationOutcome.Rejected -> ManifestDiscoveryOutcome.Unavailable
        }
    }

    private companion object {
        const val HTTPS = "https"
    }
}
