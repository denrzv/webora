package dev.siteskin.lint

import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.origin.SiteOrigin
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestDiscoveryTest {
    private val servers = mutableListOf<MockWebServer>()

    @After fun shutDownServers() {
        servers.forEach(MockWebServer::shutdown)
    }

    @Test fun `requests well known path and validates streamed response`() {
        val server = server().apply { enqueue(jsonResponse(MINIMAL)) }

        val result = discovery().load(origin(server)) as ManifestLoadResult.Validated

        assertTrue(result.outcome is SiteSkinValidationOutcome.Rejected)
        assertEquals("/.well-known/siteskin.json", server.takeRequest().path)
    }

    @Test fun `follows two same origin redirects`() {
        val server = server().apply {
            enqueue(redirect(302, "/one"))
            enqueue(redirect(307, "/two"))
            enqueue(jsonResponse(MINIMAL))
        }

        assertTrue(discovery().load(origin(server)) is ManifestLoadResult.Validated)
        assertEquals(listOf("/.well-known/siteskin.json", "/one", "/two"),
            List(3) { server.takeRequest().path })
    }

    @Test fun `refuses redirect to a distinct origin`() {
        val first = server()
        val second = server()
        first.enqueue(redirect(302, second.url("/stolen").toString()))

        val result = discovery().load(origin(first))

        assertTrue(result is ManifestLoadResult.Failed)
        assertEquals(0, second.requestCount)
    }

    @Test fun `refuses a third redirect`() {
        val server = server().apply {
            repeat(3) { enqueue(redirect(302, "/next$it")) }
        }

        val result = discovery().load(origin(server)) as ManifestLoadResult.Failed

        assertTrue(result.message.contains("more than 2"))
        assertEquals(3, server.requestCount)
    }

    @Test fun `reports status and timeout without response contents`() {
        val statusServer = server().apply {
            enqueue(MockResponse().setResponseCode(503).setBody("secret body"))
        }
        val status = discovery().load(origin(statusServer)) as ManifestLoadResult.Failed
        assertEquals("manifest request returned HTTP 503", status.message)

        val slowServer = server().apply {
            enqueue(jsonResponse(MINIMAL).setHeadersDelay(1, TimeUnit.SECONDS))
        }
        val timeoutClient = OkHttpClient.Builder().readTimeout(50, TimeUnit.MILLISECONDS).build()
        val timeout = ManifestDiscovery(timeoutClient).load(origin(slowServer)) as ManifestLoadResult.Failed
        assertEquals("manifest request failed", timeout.message)
    }

    @Test fun `accepts absent or JSON content type and rejects a contradictory declaration`() {
        val server = server().apply {
            enqueue(jsonResponse(MINIMAL).addHeader("Content-Type", "application/json; charset=utf-8"))
            enqueue(jsonResponse(MINIMAL).addHeader("Content-Type", "application/manifest+json"))
            enqueue(jsonResponse(MINIMAL).addHeader("Content-Type", "text/html"))
        }

        assertTrue(discovery().load(origin(server)) is ManifestLoadResult.Validated)
        assertTrue(discovery().load(origin(server)) is ManifestLoadResult.Validated)
        val rejected = discovery().load(origin(server)) as ManifestLoadResult.Failed
        assertEquals("manifest response declared a non-JSON media type", rejected.message)
    }

    private fun discovery() = ManifestDiscovery(
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build(),
    )

    private fun server(): MockWebServer = MockWebServer().also {
        it.start()
        servers += it
    }

    private fun origin(server: MockWebServer): SiteOrigin =
        requireNotNull(SiteOrigin.parse(server.url("/").toString()))

    private fun jsonResponse(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun redirect(code: Int, location: String) = MockResponse()
        .setResponseCode(code)
        .addHeader("Location", location)

    private companion object {
        const val MINIMAL = """{"schemaVersion":"1.0","site":{"id":"x","name":"X"}}"""
    }
}
