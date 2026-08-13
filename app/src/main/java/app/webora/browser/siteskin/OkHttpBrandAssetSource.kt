package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinLimits
import dev.siteskin.core.origin.SiteOrigin
import java.io.IOException
import java.util.Locale
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

/**
 * Why the browser refused a logo the server actually returned.
 *
 * The manifest transport has had [FetchRejection] since `DEVX-001`; the asset transport had nothing,
 * and `NET-004` is the ticket that found out what that costs. A monogram is the correct output of
 * every failure `NET-003` describes, which is precisely why it cannot also be the only record of
 * which failure occurred.
 *
 * Owned by the transport for the same reason [FetchRejection] is: this is the layer that knows the
 * eight cases apart, and re-deriving them at a boundary would put the vocabulary somewhere it can
 * drift from the conditions it names.
 */
internal enum class BrandAssetRejection {
    /** The trusted configuration's own origin is not HTTPS, so no request was made. */
    NOT_HTTPS,

    /** The resolved logo URL is not a URL the transport can use. */
    MALFORMED_URL,

    /** The resolved logo URL's complete canonical origin is not the configuration's. */
    CROSS_ORIGIN,

    /** The server answered with a non-2xx status. */
    HTTP_ERROR,

    /** A third redirect. */
    REDIRECT_LIMIT,

    /** A redirect target left the exact canonical origin. */
    CROSS_ORIGIN_REDIRECT,

    /** No `Content-Type`, or one outside the PNG/WebP allow-list. */
    UNSUPPORTED_MEDIA_TYPE,

    /** Declared or streamed past [BrandAssetLimits.MAX_BYTES]. */
    OVERSIZED,
}

/**
 * What one brand-asset request produced.
 *
 * [Unavailable] stays a `data object` while [Rejected] carries data, exactly as in
 * [ManifestFetchResult] and for the same reason: an `IOException`, a timeout or a cancelled call has
 * no status to report, and "no answer" has to stay distinguishable from "the server answered and the
 * browser refused the answer".
 *
 * The status and redirect count are observational. No browsing decision reads them; `NET-004`'s
 * inspector line and the screenshot job's diagnostics are their only consumers.
 */
internal sealed interface BrandAssetFetchResult {
    data class Fetched(
        val bytes: ByteArray,
        val format: BrandImageFormat,
        val httpStatus: Int = HTTP_OK,
        val redirects: Int = 0,
    ) : BrandAssetFetchResult

    data object Unavailable : BrandAssetFetchResult

    data class Rejected(
        val reason: BrandAssetRejection,
        val httpStatus: Int? = null,
        val redirects: Int = 0,
    ) : BrandAssetFetchResult
}

internal fun interface BrandAssetSource {
    suspend fun fetch(origin: String, assetUrl: String): BrandAssetFetchResult
}

internal class OkHttpBrandAssetSource(
    private val client: OkHttpClient = defaultClient(),
) : BrandAssetSource {
    override suspend fun fetch(origin: String, assetUrl: String): BrandAssetFetchResult =
        withContext(Dispatchers.IO) {
            val siteOrigin = SiteOrigin.parse(origin)?.takeIf { it.scheme == HTTPS }
                ?: return@withContext rejected(BrandAssetRejection.NOT_HTTPS)
            val initial = runCatching { assetUrl.toHttpUrl() }.getOrNull()
                ?: return@withContext rejected(BrandAssetRejection.MALFORMED_URL)
            if (SiteOrigin.parse(initial.toString()) != siteOrigin) {
                return@withContext rejected(BrandAssetRejection.CROSS_ORIGIN)
            }
            try {
                fetchFollowingRedirects(siteOrigin, initial)
            } catch (_: IOException) {
                BrandAssetFetchResult.Unavailable
            } catch (_: IllegalArgumentException) {
                rejected(BrandAssetRejection.MALFORMED_URL)
            }
        }

    private suspend fun fetchFollowingRedirects(origin: SiteOrigin, initial: HttpUrl): BrandAssetFetchResult {
        var url = initial
        var redirects = 0
        while (true) {
            client.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                if (!response.isRedirect) return readResponse(response, redirects)
                if (redirects == SiteSkinLimits.MAX_REDIRECTS) {
                    return rejected(BrandAssetRejection.REDIRECT_LIMIT, response.code, redirects)
                }
                val target = redirectTarget(response)
                    ?: return rejected(BrandAssetRejection.MALFORMED_URL, response.code, redirects)
                if (SiteOrigin.parse(target.toString()) != origin) {
                    return rejected(BrandAssetRejection.CROSS_ORIGIN_REDIRECT, response.code, redirects)
                }
                url = target
                redirects += 1
            }
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resumeWith(Result.success(response)) else response.close()
            }
        })
    }

    private fun redirectTarget(response: Response): HttpUrl? =
        response.header(LOCATION)?.let(response.request.url::resolve)

    private fun readResponse(response: Response, redirects: Int): BrandAssetFetchResult {
        val status = response.code
        if (!response.isSuccessful) return rejected(BrandAssetRejection.HTTP_ERROR, status, redirects)
        val format = response.header(CONTENT_TYPE)?.toBrandImageFormat()
            ?: return rejected(BrandAssetRejection.UNSUPPORTED_MEDIA_TYPE, status, redirects)
        val body = response.body
        if (body.contentLength() > BrandAssetLimits.MAX_BYTES) {
            return rejected(BrandAssetRejection.OVERSIZED, status, redirects)
        }
        val buffer = Buffer()
        val limit = BrandAssetLimits.MAX_BYTES.toLong() + 1
        while (buffer.size < limit && body.source().read(buffer, limit - buffer.size) != -1L) {
            // Read only through the sentinel byte so an oversized body is never fully consumed.
        }
        val bytes = buffer.readByteArray()
        return if (bytes.size <= BrandAssetLimits.MAX_BYTES) {
            BrandAssetFetchResult.Fetched(bytes, format, status, redirects)
        } else {
            rejected(BrandAssetRejection.OVERSIZED, status, redirects)
        }
    }

    private fun rejected(
        reason: BrandAssetRejection,
        httpStatus: Int? = null,
        redirects: Int = 0,
    ) = BrandAssetFetchResult.Rejected(reason, httpStatus, redirects)

    private fun String.toBrandImageFormat(): BrandImageFormat? {
        val normalized = substringBefore(';').trim().lowercase(Locale.ROOT)
        return BrandImageFormat.entries.singleOrNull { it.mediaType == normalized }
    }

    private companion object {
        const val HTTPS = "https"
        const val CONTENT_TYPE = "Content-Type"
        const val LOCATION = "Location"
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
