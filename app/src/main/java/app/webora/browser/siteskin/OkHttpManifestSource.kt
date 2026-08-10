package app.webora.browser.siteskin

import dev.siteskin.core.ManifestSource
import dev.siteskin.core.SiteSkinLimits
import dev.siteskin.core.SiteSkinSchema
import dev.siteskin.core.origin.SiteOrigin
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

internal data class ManifestRequestValidators(
    val etag: String? = null,
    val lastModified: String? = null,
)

/**
 * Why the browser refused a response the server actually returned.
 *
 * Owned by the transport rather than by the developer inspector that displays it: the transport is
 * what knows these apart, and collapsing them again at the boundary would put the vocabulary in the
 * wrong place.
 */
internal enum class FetchRejection {
    NOT_HTTPS,
    HTTP_ERROR,
    REDIRECT_LIMIT,
    CROSS_ORIGIN_REDIRECT,
    OVERSIZED,
    MALFORMED_URL,
}

/**
 * What one manifest request produced.
 *
 * [Unavailable] stays a `data object` while [Rejected] carries data, and the asymmetry is the point.
 * An `IOException`, a timeout or a cancelled call has no status to report, and "no answer" has to
 * stay distinguishable from "the server answered and the browser refused the answer" — to a user
 * both are regular browsing, and to a site owner behind a misconfigured CDN that difference is the
 * whole diagnosis.
 *
 * The status and redirect count are observational: `DEVX-001` reports them and no browsing decision
 * reads them.
 */
internal sealed interface ManifestFetchResult {
    data class Fetched(
        val bytes: ByteArray,
        val metadata: ManifestCacheMetadata,
        val httpStatus: Int = HTTP_OK,
        val redirects: Int = 0,
    ) : ManifestFetchResult

    data class NotModified(
        val metadata: ManifestCacheMetadata,
        val httpStatus: Int = HTTP_NOT_MODIFIED,
        val redirects: Int = 0,
    ) : ManifestFetchResult

    data object Unavailable : ManifestFetchResult

    data class Rejected(
        val reason: FetchRejection,
        val httpStatus: Int? = null,
        val redirects: Int = 0,
    ) : ManifestFetchResult
}

internal const val HTTP_OK: Int = 200
internal const val HTTP_NOT_MODIFIED: Int = 304

internal fun interface CacheableManifestSource {
    suspend fun fetch(origin: String, validators: ManifestRequestValidators): ManifestFetchResult
}

internal class OkHttpManifestSource(
    private val client: OkHttpClient = defaultClient(),
) : ManifestSource, CacheableManifestSource {
    override suspend fun fetch(origin: String): ByteArray? =
        when (val result = fetch(origin, ManifestRequestValidators())) {
            is ManifestFetchResult.Fetched -> result.bytes
            else -> null
        }

    override suspend fun fetch(
        origin: String,
        validators: ManifestRequestValidators,
    ): ManifestFetchResult = withContext(Dispatchers.IO) {
        val siteOrigin = SiteOrigin.parse(origin)?.takeIf { it.scheme == HTTPS }
            ?: return@withContext ManifestFetchResult.Rejected(FetchRejection.NOT_HTTPS)
        val initial = siteOrigin.canonical.toHttpUrl().newBuilder()
            .encodedPath(SiteSkinSchema.WELL_KNOWN_PATH)
            .build()
        try {
            fetchFollowingRedirects(siteOrigin, initial, validators)
        } catch (_: IOException) {
            ManifestFetchResult.Unavailable
        } catch (_: IllegalArgumentException) {
            ManifestFetchResult.Rejected(FetchRejection.MALFORMED_URL)
        }
    }

    private suspend fun fetchFollowingRedirects(
        origin: SiteOrigin,
        initial: HttpUrl,
        validators: ManifestRequestValidators,
    ): ManifestFetchResult {
        var url = initial
        var redirects = 0
        while (true) {
            client.newCall(request(url, validators)).await().use { response ->
                if (!response.isRedirect) return readResponse(response, redirects)
                if (redirects == SiteSkinLimits.MAX_REDIRECTS) {
                    return ManifestFetchResult.Rejected(FetchRejection.REDIRECT_LIMIT, response.code, redirects)
                }
                url = redirectTarget(response, origin)
                    ?: return ManifestFetchResult.Rejected(
                        FetchRejection.CROSS_ORIGIN_REDIRECT,
                        response.code,
                        redirects,
                    )
                redirects += 1
            }
        }
    }

    private fun request(url: HttpUrl, validators: ManifestRequestValidators): Request =
        Request.Builder().url(url).get().apply {
            validators.etag?.let { header("If-None-Match", it) }
            validators.lastModified?.let { header("If-Modified-Since", it) }
        }.build()

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(response))
                } else {
                    response.close()
                }
            }
        })
    }

    private fun redirectTarget(response: Response, origin: SiteOrigin): HttpUrl? {
        val target = response.header("Location")?.let(response.request.url::resolve) ?: return null
        val targetOrigin = SiteOrigin.parse(target.toString()) ?: return null
        return target.takeIf { targetOrigin.scheme == HTTPS && targetOrigin == origin }
    }

    private fun readResponse(response: Response, redirects: Int): ManifestFetchResult {
        val metadata = responseMetadata(response)
        val code = response.code
        if (code == HTTP_NOT_MODIFIED) return ManifestFetchResult.NotModified(metadata, code, redirects)
        if (!response.isSuccessful) {
            return ManifestFetchResult.Rejected(FetchRejection.HTTP_ERROR, code, redirects)
        }
        val body = response.body
        if (body.contentLength() > SiteSkinLimits.MAX_MANIFEST_BYTES) {
            return ManifestFetchResult.Rejected(FetchRejection.OVERSIZED, code, redirects)
        }
        val limit = SiteSkinLimits.MAX_MANIFEST_BYTES.toLong() + 1
        val buffer = Buffer()
        val source = body.source()
        while (buffer.size < limit && source.read(buffer, limit - buffer.size) != -1L) {
            // Read only through the sentinel byte; it distinguishes an exact-limit body from overflow.
        }
        val bytes = buffer.readByteArray()
        return if (bytes.size <= SiteSkinLimits.MAX_MANIFEST_BYTES) {
            ManifestFetchResult.Fetched(bytes, metadata, code, redirects)
        } else {
            ManifestFetchResult.Rejected(FetchRejection.OVERSIZED, code, redirects)
        }
    }

    private fun responseMetadata(response: Response) = ManifestCacheMetadata(
        cacheControl = response.header("Cache-Control"),
        etag = response.header("ETag"),
        lastModified = response.header("Last-Modified"),
    )

    private companion object {
        const val HTTPS = "https"
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val IO_TIMEOUT_SECONDS = 5L
        const val CALL_TIMEOUT_SECONDS = 10L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
