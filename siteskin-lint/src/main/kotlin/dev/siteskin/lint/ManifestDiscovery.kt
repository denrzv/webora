package dev.siteskin.lint

import dev.siteskin.core.SiteSkinSchema
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.origin.SiteOrigin
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull

internal fun interface ManifestLoader {
    fun load(origin: SiteOrigin): ManifestLoadResult
}

internal sealed interface ManifestLoadResult {
    data class Validated(val outcome: SiteSkinValidationOutcome) : ManifestLoadResult
    data class Failed(val message: String) : ManifestLoadResult
}

internal class ManifestDiscovery(
    private val client: OkHttpClient = defaultClient(),
) : ManifestLoader {
    override fun load(origin: SiteOrigin): ManifestLoadResult {
        val initial = origin.toString().toHttpUrl().newBuilder()
            .encodedPath(SiteSkinSchema.WELL_KNOWN_PATH)
            .build()
        return try {
            fetch(origin, initial)
        } catch (_: IOException) {
            ManifestLoadResult.Failed("manifest request failed")
        } catch (_: IllegalArgumentException) {
            ManifestLoadResult.Failed("manifest request failed")
        }
    }

    private fun fetch(origin: SiteOrigin, initial: HttpUrl): ManifestLoadResult {
        var url = initial
        var redirectCount = 0
        while (true) {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                when (val step = processResponse(response, origin, redirectCount)) {
                    is DiscoveryStep.Complete -> return step.result
                    is DiscoveryStep.Redirect -> {
                        url = step.url
                        redirectCount += 1
                    }
                }
            }
        }
    }

    private fun processResponse(
        response: Response,
        origin: SiteOrigin,
        redirectCount: Int,
    ): DiscoveryStep {
        if (response.isRedirect) return redirectStep(response, origin, redirectCount)
        if (!response.isSuccessful) {
            return complete("manifest request returned HTTP ${response.code}")
        }
        if (!isJsonMediaType(response.header("Content-Type"))) {
            return complete("manifest response declared a non-JSON media type")
        }
        val outcome = SiteSkinValidator.validate(response.body.byteStream(), origin.toString())
        return DiscoveryStep.Complete(ManifestLoadResult.Validated(outcome))
    }

    private fun redirectStep(response: Response, origin: SiteOrigin, count: Int): DiscoveryStep {
        if (count == MAX_REDIRECTS) return DiscoveryStep.Complete(tooManyRedirects())
        val target = redirectTarget(response.request.url, response.header("Location"), origin)
            ?: return complete("redirect left the requested origin")
        return DiscoveryStep.Redirect(target)
    }

    private fun redirectTarget(current: HttpUrl, location: String?, origin: SiteOrigin): HttpUrl? {
        val target = location?.let(current::resolve) ?: return null
        val targetOrigin = SiteOrigin.parse(target.toString()) ?: return null
        return target.takeIf { targetOrigin == origin }
    }

    private fun tooManyRedirects(): ManifestLoadResult.Failed =
        ManifestLoadResult.Failed("manifest redirected more than $MAX_REDIRECTS times")

    private fun complete(message: String): DiscoveryStep.Complete =
        DiscoveryStep.Complete(ManifestLoadResult.Failed(message))

    private fun isJsonMediaType(header: String?): Boolean {
        if (header == null) return true
        val mediaType = header.toMediaTypeOrNull() ?: return false
        return mediaType.type == "application" &&
            (mediaType.subtype == "json" || mediaType.subtype.endsWith("+json"))
    }

    private companion object {
        const val MAX_REDIRECTS = 2
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val READ_TIMEOUT_SECONDS = 5L
        const val CALL_TIMEOUT_SECONDS = 10L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

private sealed interface DiscoveryStep {
    data class Redirect(val url: HttpUrl) : DiscoveryStep
    data class Complete(val result: ManifestLoadResult) : DiscoveryStep
}
