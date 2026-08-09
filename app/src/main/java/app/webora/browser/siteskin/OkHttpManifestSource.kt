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

internal class OkHttpManifestSource(
    private val client: OkHttpClient = defaultClient(),
) : ManifestSource {
    override suspend fun fetch(origin: String): ByteArray? = withContext(Dispatchers.IO) {
        val siteOrigin = SiteOrigin.parse(origin)?.takeIf { it.scheme == HTTPS } ?: return@withContext null
        val initial = siteOrigin.canonical.toHttpUrl().newBuilder()
            .encodedPath(SiteSkinSchema.WELL_KNOWN_PATH)
            .build()
        try {
            fetchFollowingRedirects(siteOrigin, initial)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun fetchFollowingRedirects(origin: SiteOrigin, initial: HttpUrl): ByteArray? {
        var url = initial
        var redirects = 0
        while (true) {
            client.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                if (!response.isRedirect) return readSuccessfulBody(response)
                if (redirects == SiteSkinLimits.MAX_REDIRECTS) return null
                url = redirectTarget(response, origin) ?: return null
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

    private fun readSuccessfulBody(response: Response): ByteArray? {
        if (!response.isSuccessful) return null
        val body = response.body
        if (body.contentLength() > SiteSkinLimits.MAX_MANIFEST_BYTES) return null
        val limit = SiteSkinLimits.MAX_MANIFEST_BYTES.toLong() + 1
        val buffer = Buffer()
        val source = body.source()
        while (buffer.size < limit && source.read(buffer, limit - buffer.size) != -1L) {
            // Read only through the sentinel byte; it distinguishes an exact-limit body from overflow.
        }
        return buffer.readByteArray().takeIf { it.size <= SiteSkinLimits.MAX_MANIFEST_BYTES }
    }

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
