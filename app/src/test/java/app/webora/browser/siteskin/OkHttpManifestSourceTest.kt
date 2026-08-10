package app.webora.browser.siteskin

import dev.siteskin.core.SiteSkinLimits
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class OkHttpManifestSourceTest {
    private val servers = mutableListOf<MockWebServer>()

    @After fun shutDownServers() = servers.forEach(MockWebServer::shutdown)

    @Test fun `fetches only the well known path over https`() = runTest {
        val server = server().apply { enqueue(MockResponse().setBody("manifest")) }

        assertArrayEquals("manifest".toByteArray(), source().fetch(origin(server)))
        assertEquals("/.well-known/siteskin.json", server.takeRequest().path)
        assertNull(source().fetch("http://localhost:${server.port}"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `follows two exact origin redirects and refuses a third`() = runTest {
        val success = server().apply {
            enqueue(redirect(origin(this) + "/one"))
            enqueue(redirect(origin(this) + "/two"))
            enqueue(MockResponse().setBody("ok"))
        }
        assertArrayEquals("ok".toByteArray(), source().fetch(origin(success)))

        val overflow = server().apply { repeat(3) { enqueue(redirect(origin(this) + "/next$it")) } }
        assertNull(source().fetch(origin(overflow)))
        assertEquals(3, overflow.requestCount)
    }

    @Test fun `same origin redirect loop terminates at the browser owned hop limit`() = runTest {
        val server = server()
        server.enqueue(redirect(origin(server) + "/loop"))
        server.enqueue(redirect(origin(server) + "/.well-known/siteskin.json"))
        server.enqueue(redirect(origin(server) + "/loop"))

        assertNull(source().fetch(origin(server)))
        assertEquals(SiteSkinLimits.MAX_REDIRECTS + 1, server.requestCount)
    }

    @Test fun `refuses a redirect to another origin`() = runTest {
        val target = server()
        val targetUrl = target.url("/stolen").newBuilder().scheme("https").build().toString()
        val start = server().apply { enqueue(redirect(targetUrl)) }

        assertNull(source().fetch(origin(start)))
        assertEquals(0, target.requestCount)
    }

    @Test fun `cancelling fetch cancels the underlying call`() = runTest {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown()
            while (!chain.call().isCanceled()) Thread.sleep(CANCEL_POLL_MILLIS)
            cancelled.countDown()
            throw IOException("cancelled")
        }.build()
        val job = launch { OkHttpManifestSource(client).fetch("https://example.test") }

        testScheduler.runCurrent()
        assertTrue(entered.await(CANCEL_AWAIT_SECONDS, TimeUnit.SECONDS))
        job.cancelAndJoin()

        // cancelAndJoin returns once the coroutine completes, which happens before the
        // OkHttp dispatcher thread observes the cancelled call. Reading the flag here
        // races that thread; awaiting it does not. The await is still a control: drop
        // invokeOnCancellation and the call is never cancelled, so this times out.
        assertTrue(cancelled.await(CANCEL_AWAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test fun `rejects unsuccessful oversized and stalled responses`() = runTest {
        val server = server().apply {
            enqueue(MockResponse().setResponseCode(503).setBody("secret"))
            enqueue(MockResponse().setBody("x").setHeader("Content-Length", SiteSkinLimits.MAX_MANIFEST_BYTES + 1))
            enqueue(MockResponse().setBody(Buffer().write(ByteArray(SiteSkinLimits.MAX_MANIFEST_BYTES + 1))))
            enqueue(MockResponse().setHeadersDelay(1, TimeUnit.SECONDS).setBody("late"))
        }
        val source = source(readTimeoutMillis = 50)

        repeat(4) { assertNull(source.fetch(origin(server))) }
    }

    @Test fun `sends validators and exposes not modified metadata`() = runTest {
        val server = server().apply {
            enqueue(
                MockResponse().setResponseCode(304)
                    .setHeader("Cache-Control", "max-age=120")
                    .setHeader("ETag", "new-tag"),
            )
        }

        val result = source().fetch(origin(server), ManifestRequestValidators("old-tag", "yesterday"))
        val request = server.takeRequest()

        assertEquals("old-tag", request.getHeader("If-None-Match"))
        assertEquals("yesterday", request.getHeader("If-Modified-Since"))
        assertTrue(result is ManifestFetchResult.NotModified)
        assertEquals("new-tag", (result as ManifestFetchResult.NotModified).metadata.etag)
        assertEquals("max-age=120", result.metadata.cacheControl)
    }

    @Test fun `distinguishes HTTP rejection from transport unavailability`() = runTest {
        val server = server().apply { enqueue(MockResponse().setResponseCode(503)) }

        assertSame(ManifestFetchResult.Rejected, source().fetch(origin(server), ManifestRequestValidators()))
        server.shutdown()
        assertSame(ManifestFetchResult.Unavailable, source().fetch(origin(server), ManifestRequestValidators()))
    }

    private fun source(readTimeoutMillis: Long = 5_000) = OkHttpManifestSource(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val localUrl = request.url.newBuilder().scheme("http").host("localhost").build()
                chain.proceed(request.newBuilder().url(localUrl).build())
            }
            .followRedirects(false)
            .followSslRedirects(false)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build(),
    )

    private fun server() = MockWebServer().also {
        it.start()
        servers += it
    }

    private fun origin(server: MockWebServer) = server.url("/").newBuilder()
        .scheme("https")
        .host("example.test")
        .build()
        .toString()
        .removeSuffix("/")
    private fun redirect(location: String) = MockResponse().setResponseCode(302).addHeader("Location", location)

    private companion object {
        const val CANCEL_POLL_MILLIS = 1L
        const val CANCEL_AWAIT_SECONDS = 5L
    }
}
