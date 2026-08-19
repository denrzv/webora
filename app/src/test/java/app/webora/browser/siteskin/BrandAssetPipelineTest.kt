package app.webora.browser.siteskin

import android.graphics.Bitmap
import app.webora.browser.browser.BrowserMode
import app.webora.browser.inspector.BrandAssetStage
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.origin.SiteOrigin
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * The reference integration's logo pipeline, from its published manifest to the value a consumer reads.
 *
 * `BrandAssetLoaderTest` proves the loader returns a decoded asset; nothing proved the decoded asset
 * ever *reaches* a reader. Both can be green while the transition is broken, which is the state
 * issue #128 reports — so this drives the whole chain with as little stubbed as a JVM allows:
 *
 * - the **real** checked-in Bloom manifest, through the **real** `SiteSkinValidator`;
 * - a deterministic repository-local 512 x 512 PNG over a real socket, so the JVM gate is hermetic;
 * - the **real** `OkHttpBrandAssetSource`, so URL resolution, the exact-origin recheck, the redirect
 *   policy, the size cap and the signature match are all genuinely exercised;
 * - the **real** `publishesBrandAsset` guard, driven with the modes a session actually produces.
 *
 * Exact artwork is not relevant to this JVM transition test because `BitmapFactory` is the one Android
 * dependency intentionally stood in for. Hosted visual acceptance remains the evidence for Bloom's
 * actual deployed logo; this test owns transport, decode gating and publication without depending on
 * a sibling repository checkout.
 */
class BrandAssetPipelineTest {
    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer()
        server.start()
    }

    @After fun stop() = server.shutdown()

    /**
     * The transition the issue says never happens: monogram, then the site's own asset.
     *
     * Asserted as a **change**, not as a final value. A reader that had been handed the loaded asset
     * from the start would satisfy an end-state assertion while proving nothing about the transition
     * that is actually broken.
     */
    @Test
    fun `a served logo replaces the monogram for the configuration that requested it`() = runBlocking {
        server.enqueue(pngResponse())
        val configuration = bloomConfiguration()

        val before = publishedAsset(published = null, readFor = configuration)
        assertTrue("before the load, a reader sees the deterministic fallback", before is BrandAsset.Monogram)
        assertEquals("B", (before as BrandAsset.Monogram).text)

        val outcome = loader().load(configuration)

        assertEquals("the PNG bytes must reach the decoder", BrandAssetStage.DECODED, outcome.trace.stage)
        assertEquals(200, outcome.trace.httpStatus)
        assertTrue("a decoded asset, not a monogram", outcome.asset is BrandAsset.BitmapAsset)

        val published = publishIfAllowed(outcome, configuration)
        val after = publishedAsset(published, readFor = configuration)
        assertTrue("after the load, the same reader sees the site's asset", after is BrandAsset.BitmapAsset)
    }

    /**
     * The guard still refuses a foreign configuration, which is the property the fix must not trade.
     *
     * Publishing more eagerly is the obvious over-correction for this bug, and `UX-012`/`HARDEN-002`
     * are what it would cost: one origin's brand over another origin's page.
     */
    @Test
    fun `a load cannot publish into a different configuration`() = runBlocking {
        server.enqueue(pngResponse())
        val loaded = bloomConfiguration()
        val other = bloomConfiguration()

        val outcome = loader().load(loaded)

        assertNull(
            "a foreign configuration must not receive it",
            publishIfAllowed(outcome, loaded, mode = integrated(other)),
        )
        assertNull("and neither must a regular tab", publishIfAllowed(outcome, loaded, mode = BrowserMode.Home))
        assertNotNull("while the owning configuration still does", publishIfAllowed(outcome, loaded))
    }

    /** A reader holding a different configuration falls back rather than borrowing the asset. */
    @Test
    fun `a published asset is only read by the configuration it was published for`() = runBlocking {
        server.enqueue(pngResponse())
        val loaded = bloomConfiguration()
        val other = bloomConfiguration()
        val published = publishIfAllowed(loader().load(loaded), loaded)

        assertTrue(publishedAsset(published, readFor = loaded) is BrandAsset.BitmapAsset)
        assertTrue(
            "another configuration must see its own fallback, never this one's asset",
            publishedAsset(published, readFor = other) is BrandAsset.Monogram,
        )
    }

    /** No `logoUrl` is not a failure, and must not become one. */
    @Test
    fun `a manifest without a logo keeps the deterministic monogram`() = runBlocking {
        val outcome = loader().load(configuration(MANIFEST_WITHOUT_LOGO))

        assertEquals(BrandAssetStage.NOT_DECLARED, outcome.trace.stage)
        assertTrue(outcome.asset is BrandAsset.Monogram)
    }

    /** A genuine HTTP failure stays on the fallback and says why. */
    @Test
    fun `a rejected logo stays on the fallback with a recorded stage`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val outcome = loader().load(bloomConfiguration())

        assertEquals(BrandAssetStage.TRANSPORT_REJECTED, outcome.trace.stage)
        assertEquals(404, outcome.trace.httpStatus)
        assertTrue(outcome.asset is BrandAsset.Monogram)
    }

    // --- the two lines BrowserScreen uses, so this test exercises them rather than paraphrasing ---

    private fun publishIfAllowed(
        outcome: BrandAssetOutcome,
        configuration: SiteSkinConfiguration,
        mode: BrowserMode = integrated(configuration),
    ): Pair<SiteSkinConfiguration, BrandAsset>? =
        if (publishesBrandAsset(mode, configuration)) configuration to outcome.asset else null

    private fun publishedAsset(
        published: Pair<SiteSkinConfiguration, BrandAsset>?,
        readFor: SiteSkinConfiguration,
    ): BrandAsset = published?.takeIf { it.first === readFor }?.second
        ?: BrandAsset.Monogram(brandMonogram(readFor.site.shortName, readFor.site.name))

    private fun integrated(configuration: SiteSkinConfiguration): BrowserMode =
        BrowserMode.Integrated(requireNotNull(SiteOrigin.parse(ORIGIN)), configuration)

    /**
     * The real source, over a client that rewrites the HTTPS facade back to the local server.
     *
     * SiteSkin is HTTPS-only at both layers — `SiteSkinValidator` refuses a cleartext origin and
     * `OkHttpBrandAssetSource` refuses a non-HTTPS asset — so a test that wants the *real* policy
     * exercised has to present a real HTTPS origin. `OkHttpBrandAssetSourceTest` established this
     * shape and it is reused rather than reinvented: the origin check, the redirect policy and the
     * media checks all run against `https://bloomflowers.example`, and only the socket is local.
     */
    private fun loader() = BrandAssetLoader(
        OkHttpBrandAssetSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val local = chain.request().url.newBuilder()
                        .scheme("http").host(server.hostName).port(server.port).build()
                    chain.proceed(chain.request().newBuilder().url(local).build())
                }
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
        ),
        StubDecoder(),
    )

    private fun bloomConfiguration(): SiteSkinConfiguration = configuration(bloomManifest())

    private fun configuration(manifest: String): SiteSkinConfiguration =
        SiteSkinValidator.validate(manifest.byteInputStream(), ORIGIN)
            .let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private fun pngResponse() = MockResponse()
        .setHeader("Content-Type", "image/png")
        .setBody(Buffer().write(bloomLogoBytes()))

    /**
     * A decoder that reports the repository-local fixture's real dimensions without Android framework decode.
     *
     * The fixture is genuinely 512 x 512, so the loader's pixel caps are exercised against the same
     * dimensions as the deployed Bloom artwork while keeping the JVM test independent of another checkout.
     */
    private class StubDecoder : BrandAssetDecoder {
        override suspend fun probe(bytes: ByteArray) = BrandImageBounds(512, 512, BrandImageFormat.PNG)
        override suspend fun decode(bytes: ByteArray): Bitmap = mockk(relaxed = true)
    }

    private companion object {
        /** The reference integration's real manifest stays repository-local and versioned with Webora. */
        fun bloomManifest(): String = repoFile("../spec/fixtures/valid/bloom-flowers.json").readText()

        /**
         * The media body is a deterministic test fixture on this module's classpath.
         * MIME decoding deliberately tolerates the final newline enforced by repository formatters.
         */
        fun bloomLogoBytes(): ByteArray = requireNotNull(
            BrandAssetPipelineTest::class.java.getResourceAsStream(BLOOM_LOGO_RESOURCE),
        ) { "expected classpath resource $BLOOM_LOGO_RESOURCE to exist" }
            .bufferedReader()
            .use { Base64.getMimeDecoder().decode(it.readText()) }

        fun repoFile(path: String): File = File(path).also {
            check(it.exists()) { "expected ${it.absolutePath} to exist" }
        }

        /** The origin Bloom's fixture is bound to; only the socket beneath it is local. */
        const val ORIGIN = "https://bloomflowers.example"
        const val BLOOM_LOGO_RESOURCE = "/bloom-logo-reference.png.b64"

        const val MANIFEST_WITHOUT_LOGO =
            """{"schemaVersion":"1.0","site":{"id":"bloom","name":"Bloom Flowers"}}"""
    }
}
