package app.webora.browser.siteskin

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpBrandAssetSourceTest {
    private val servers = mutableListOf<MockWebServer>()

    @After fun shutDownServers() = servers.forEach { runCatching { it.shutdown() } }

    @Test fun `fetches allowed media from exact HTTPS origin`() = runTest {
        val server = server().apply { enqueue(image(PNG, "image/png; charset=binary")) }

        val result = source().fetch(origin(server), assetUrl(server, "/logo.png"))

        assertTrue(result is BrandAssetFetchResult.Fetched)
        result as BrandAssetFetchResult.Fetched
        assertArrayEquals(PNG, result.bytes)
        assertEquals(BrandImageFormat.PNG, result.format)
        assertEquals("/logo.png", server.takeRequest().path)
    }

    @Test fun `follows two exact origin redirects and rejects origin changes`() = runTest {
        val success = server().apply {
            enqueue(redirect(assetUrl(this, "/one")))
            enqueue(redirect(assetUrl(this, "/two")))
            enqueue(image(PNG, "image/png"))
        }
        assertTrue(source().fetch(origin(success), assetUrl(success, "/start")) is BrandAssetFetchResult.Fetched)

        val crossOrigin = server()
        val rejected = server().apply { enqueue(redirect(assetUrl(crossOrigin, "/logo"))) }
        assertSame(
            BrandAssetFetchResult.Rejected,
            source().fetch(origin(rejected), assetUrl(rejected, "/start")),
        )
        assertEquals(0, crossOrigin.requestCount)

        val subdomain = server().apply {
            enqueue(redirect(assetUrl(this, "/logo").replace("example.test", "cdn.example.test")))
        }
        assertSame(BrandAssetFetchResult.Rejected, source().fetch(origin(subdomain), assetUrl(subdomain, "/start")))
    }

    @Test fun `refuses a third redirect`() = runTest {
        val server = server().apply { repeat(3) { enqueue(redirect(assetUrl(this, "/next$it"))) } }

        assertSame(BrandAssetFetchResult.Rejected, source().fetch(origin(server), assetUrl(server, "/start")))
        assertEquals(3, server.requestCount)
    }

    @Test fun `rejects absent unsupported and spoofed media declarations`() = runTest {
        val server = server().apply {
            enqueue(MockResponse().setBody(Buffer().write(PNG)))
            enqueue(image("<svg/>".toByteArray(), "image/svg+xml"))
            enqueue(image("<svg/>".toByteArray(), "image/png"))
        }

        repeat(2) {
            assertSame(BrandAssetFetchResult.Rejected, source().fetch(origin(server), assetUrl(server, "/logo")))
        }
        val spoofed = source().fetch(origin(server), assetUrl(server, "/logo")) as BrandAssetFetchResult.Fetched
        assertEquals(BrandImageFormat.PNG, spoofed.format)
        assertNullSignature(spoofed.bytes)
    }

    @Test fun `rejects declared and streamed oversized bodies plus HTTP failure and timeout`() = runTest {
        val oversized = ByteArray(BrandAssetLimits.MAX_BYTES + 1)
        val server = server().apply {
            enqueue(image(PNG, "image/png").setHeader("Content-Length", oversized.size))
            enqueue(image(oversized, "image/png"))
            enqueue(MockResponse().setResponseCode(503))
            enqueue(image(PNG, "image/png").setHeadersDelay(1, TimeUnit.SECONDS))
        }
        val source = source(readTimeoutMillis = 50)

        repeat(3) {
            assertSame(BrandAssetFetchResult.Rejected, source.fetch(origin(server), assetUrl(server, "/logo")))
        }
        assertSame(BrandAssetFetchResult.Unavailable, source.fetch(origin(server), assetUrl(server, "/logo")))
    }

    @Test fun `cancelling fetch cancels underlying call`() = runTest {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown()
            while (!chain.call().isCanceled()) Thread.sleep(CANCEL_POLL_MILLIS)
            cancelled.countDown()
            throw IOException("cancelled")
        }.build()
        val job = launch { OkHttpBrandAssetSource(client).fetch("https://example.test", "https://example.test/logo") }

        testScheduler.runCurrent()
        assertTrue(entered.await(CANCEL_AWAIT_SECONDS, TimeUnit.SECONDS))
        job.cancelAndJoin()

        // cancelAndJoin returns once the coroutine completes, which happens before the
        // OkHttp dispatcher thread observes the cancelled call. Reading the flag here
        // races that thread; awaiting it does not. The await is still a control: drop
        // invokeOnCancellation and the call is never cancelled, so this times out.
        assertTrue(cancelled.await(CANCEL_AWAIT_SECONDS, TimeUnit.SECONDS))
    }

    private fun assertNullSignature(bytes: ByteArray) = assertEquals(null, brandImageFormat(bytes))

    private fun source(readTimeoutMillis: Long = 5_000) = OkHttpBrandAssetSource(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val local = chain.request().url.newBuilder().scheme("http").host("localhost").build()
                chain.proceed(chain.request().newBuilder().url(local).build())
            }
            .followRedirects(false)
            .followSslRedirects(false)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build(),
    )

    private fun server() = MockWebServer().also { it.start(); servers += it }
    private fun origin(server: MockWebServer) = assetUrl(server, "").removeSuffix("/")
    private fun assetUrl(server: MockWebServer, path: String) = server.url(path.ifEmpty { "/" }).newBuilder()
        .scheme("https").host("example.test").build().toString()
    private fun redirect(location: String) = MockResponse().setResponseCode(302).addHeader("Location", location)
    private fun image(bytes: ByteArray, type: String) = MockResponse()
        .setHeader("Content-Type", type).setBody(Buffer().write(bytes))

    private companion object {
        const val CANCEL_POLL_MILLIS = 1L
        const val CANCEL_AWAIT_SECONDS = 5L
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
