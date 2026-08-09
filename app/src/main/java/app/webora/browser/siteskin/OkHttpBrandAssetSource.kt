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

internal sealed interface BrandAssetFetchResult {
    data class Fetched(val bytes: ByteArray, val format: BrandImageFormat) : BrandAssetFetchResult
    data object Unavailable : BrandAssetFetchResult
    data object Rejected : BrandAssetFetchResult
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
                ?: return@withContext BrandAssetFetchResult.Rejected
            val initial = runCatching { assetUrl.toHttpUrl() }.getOrNull()
                ?: return@withContext BrandAssetFetchResult.Rejected
            if (SiteOrigin.parse(initial.toString()) != siteOrigin) return@withContext BrandAssetFetchResult.Rejected
            try {
                fetchFollowingRedirects(siteOrigin, initial)
            } catch (_: IOException) {
                BrandAssetFetchResult.Unavailable
            } catch (_: IllegalArgumentException) {
                BrandAssetFetchResult.Rejected
            }
        }

    private suspend fun fetchFollowingRedirects(origin: SiteOrigin, initial: HttpUrl): BrandAssetFetchResult {
        var url = initial
        var redirects = 0
        while (true) {
            client.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                if (!response.isRedirect) return readResponse(response)
                if (redirects == SiteSkinLimits.MAX_REDIRECTS) return BrandAssetFetchResult.Rejected
                url = redirectTarget(response, origin) ?: return BrandAssetFetchResult.Rejected
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

    private fun redirectTarget(response: Response, origin: SiteOrigin): HttpUrl? {
        val target = response.header(LOCATION)?.let(response.request.url::resolve) ?: return null
        return target.takeIf { SiteOrigin.parse(it.toString()) == origin }
    }

    private fun readResponse(response: Response): BrandAssetFetchResult {
        if (!response.isSuccessful) return BrandAssetFetchResult.Rejected
        val format = response.header(CONTENT_TYPE)?.toBrandImageFormat()
            ?: return BrandAssetFetchResult.Rejected
        val body = response.body
        if (body.contentLength() > BrandAssetLimits.MAX_BYTES) return BrandAssetFetchResult.Rejected
        val buffer = Buffer()
        val limit = BrandAssetLimits.MAX_BYTES.toLong() + 1
        while (buffer.size < limit && body.source().read(buffer, limit - buffer.size) != -1L) {
            // Read only through the sentinel byte so an oversized body is never fully consumed.
        }
        val bytes = buffer.readByteArray()
        return if (bytes.size <= BrandAssetLimits.MAX_BYTES) {
            BrandAssetFetchResult.Fetched(bytes, format)
        } else {
            BrandAssetFetchResult.Rejected
        }
    }

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
