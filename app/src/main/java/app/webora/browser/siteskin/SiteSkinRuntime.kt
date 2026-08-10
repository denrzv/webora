package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.origin.SiteOrigin

internal enum class SiteConsentDecision {
    ALLOW,
    NEVER,
}

internal data class SiteSkinCandidate(
    val origin: SiteOrigin,
    val generation: Long,
    val configuration: SiteSkinConfiguration,
)

internal sealed interface CandidateDisposition {
    data class Activate(val candidate: SiteSkinCandidate) : CandidateDisposition
    data class Ask(val candidate: SiteSkinCandidate) : CandidateDisposition
    data object Ignore : CandidateDisposition
}

internal fun candidateDisposition(
    outcome: ManifestDiscoveryOutcome,
    observedOrigin: SiteOrigin?,
    currentGeneration: Long,
    consent: SiteConsentDecision?,
    siteSkinEnabled: Boolean = true,
): CandidateDisposition {
    val available = outcome as? ManifestDiscoveryOutcome.Available
    if (!siteSkinEnabled || available == null) return CandidateDisposition.Ignore
    if (available.generation != currentGeneration || available.origin != observedOrigin) {
        return CandidateDisposition.Ignore
    }
    val configurationOrigin = SiteOrigin.parse(available.validation.configuration.origin)
    if (configurationOrigin != available.origin) return CandidateDisposition.Ignore
    val candidate = SiteSkinCandidate(
        available.origin,
        available.generation,
        available.validation.configuration,
    )
    return when (consent) {
        SiteConsentDecision.ALLOW -> CandidateDisposition.Activate(candidate)
        SiteConsentDecision.NEVER -> CandidateDisposition.Ignore
        null -> CandidateDisposition.Ask(candidate)
    }
}

internal fun SiteSkinCandidate.isCurrent(origin: SiteOrigin?, generation: Long): Boolean =
    this.origin == origin && this.generation == generation

internal fun acceptedConfiguration(outcome: SiteSkinValidationOutcome): SiteSkinConfiguration? =
    (outcome as? SiteSkinValidationOutcome.Accepted)?.configuration
