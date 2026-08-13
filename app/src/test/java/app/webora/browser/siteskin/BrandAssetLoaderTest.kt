package app.webora.browser.siteskin

import android.graphics.Bitmap
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BrandAssetLoaderTest {
    @Test fun `missing logo returns monogram without transport`() = runTest {
        var fetched = false
        val loader = loader(source = BrandAssetSource { _, _ -> fetched = true; rejected() })

        val outcome = loader.load(configuration(logo = null))

        assertEquals(BrandAsset.Monogram("B"), outcome.asset)
        assertEquals(BrandAssetStage.NOT_DECLARED, outcome.trace.stage)
        assertTrue(!fetched)
    }

    @Test fun `matching format and safe bounds decode a bitmap`() = runTest {
        val bitmap = mockk<Bitmap>()
        val decoder = FakeDecoder(BrandImageBounds(64, 48, BrandImageFormat.PNG), bitmap)

        val outcome = loader(decoder = decoder).load(configuration())

        assertSame(bitmap, (outcome.asset as BrandAsset.BitmapAsset).bitmap)
        assertEquals(BrandAssetStage.DECODED, outcome.trace.stage)
        assertEquals(64, outcome.trace.width)
        assertEquals(48, outcome.trace.height)
        assertEquals(1, decoder.probes)
        assertEquals(1, decoder.decodes)
    }

    @Test fun `signature MIME probe and dimensions must all agree before decode`() = runTest {
        val cases = listOf(
            fetched("<svg/>".toByteArray(), BrandImageFormat.PNG) to BrandImageBounds(10, 10, BrandImageFormat.PNG),
            fetched(PNG, BrandImageFormat.PNG) to BrandImageBounds(10, 10, BrandImageFormat.WEBP),
            fetched(PNG, BrandImageFormat.PNG) to BrandImageBounds(1025, 1, BrandImageFormat.PNG),
            fetched(PNG, BrandImageFormat.PNG) to null,
        )

        cases.forEach { (fetchResult, bounds) ->
            val decoder = FakeDecoder(bounds, mockk())
            val outcome = loader(source = BrandAssetSource { _, _ -> fetchResult }, decoder = decoder)
                .load(configuration())
            assertEquals(BrandAsset.Monogram("B"), outcome.asset)
            assertEquals(0, decoder.decodes)
        }
    }

    /**
     * The assertion `NET-004` exists to make possible.
     *
     * Before this ticket every row below produced the same `Monogram("B")` and nothing else, so the
     * loader's own tests could not distinguish a refused media type from an unreachable host from a
     * decoder that gave up — which is precisely what a site owner looking at the monogram could not
     * distinguish either. The old loader cannot satisfy this test at all: it has no second value to
     * assert on.
     *
     * The `asset` column is asserted too, so making the stages distinct may not change what the
     * browser renders.
     */
    @Test fun `every refusal names its own stage and still publishes the monogram`() = runTest {
        val decoded = BrandImageBounds(10, 10, BrandImageFormat.PNG)
        val cases = listOf(
            case(BrandAssetStage.TRANSPORT_REJECTED, source = { rejected(BrandAssetRejection.HTTP_ERROR) }),
            case(BrandAssetStage.TRANSPORT_UNAVAILABLE, source = { BrandAssetFetchResult.Unavailable }),
            case(
                BrandAssetStage.SIGNATURE_MISMATCH,
                source = { fetched("<svg/>".toByteArray(), BrandImageFormat.PNG) },
            ),
            case(BrandAssetStage.BOUNDS_UNREADABLE, decoder = { FakeDecoder(null, mockk()) }),
            case(
                BrandAssetStage.BOUNDS_REFUSED,
                decoder = { FakeDecoder(BrandImageBounds(1025, 1, BrandImageFormat.PNG), mockk()) },
            ),
            case(BrandAssetStage.DECODE_FAILED, decoder = { FakeDecoder(decoded, null) }),
            case(BrandAssetStage.UNEXPECTED_ERROR, source = { error("decoder boundary") }),
        )

        val stages = cases.map { case ->
            val outcome = loader(source = case.source, decoder = case.decoder()).load(configuration())
            assertEquals("${case.expected} must still publish the monogram", MONOGRAM, outcome.asset)
            assertEquals(case.expected, outcome.trace.stage)
            outcome.trace.stage
        }

        // A guard against a matrix that stopped exercising anything: seven rows, seven stages.
        assertEquals(cases.size, stages.toSet().size)
    }

    @Test fun `a transport rejection carries its reason status and redirect count`() = runTest {
        val loader = loader(
            source = { _, _ -> BrandAssetFetchResult.Rejected(BrandAssetRejection.REDIRECT_LIMIT, 302, 2) },
        )

        val trace = loader.load(configuration()).trace

        assertEquals(BrandAssetStage.TRANSPORT_REJECTED, trace.stage)
        assertEquals(BrandAssetRejection.REDIRECT_LIMIT, trace.rejection)
        assertEquals(302, trace.httpStatus)
        assertEquals(2, trace.redirects)
    }

    /** No answer arrived, so there is nothing to report about one. */
    @Test fun `an unavailable transport reports no status and no rejection`() = runTest {
        val trace = loader(source = { _, _ -> BrandAssetFetchResult.Unavailable }).load(configuration()).trace

        assertEquals(BrandAssetStage.TRANSPORT_UNAVAILABLE, trace.stage)
        assertNull(trace.rejection)
        assertNull(trace.httpStatus)
    }

    @Test fun `cancellation propagates instead of becoming a stage`() = runTest {
        val loader = loader(source = BrandAssetSource { _, _ -> throw CancellationException("superseded") })

        try {
            loader.load(configuration())
            fail("CancellationException expected")
        } catch (_: CancellationException) {
            // Expected: cancellation is lifecycle control, not a remote failure result.
        }
    }

    @Test fun `elapsed time is recorded on every path`() = runTest {
        assertTrue(loader().load(configuration()).trace.elapsedMillis >= 0)
        assertTrue(loader().load(configuration(logo = null)).trace.elapsedMillis >= 0)
    }

    private fun loader(
        source: BrandAssetSource = BrandAssetSource { _, _ -> fetched(PNG, BrandImageFormat.PNG) },
        decoder: BrandAssetDecoder = FakeDecoder(BrandImageBounds(10, 10, BrandImageFormat.PNG), mockk()),
    ) = BrandAssetLoader(source, decoder)

    private fun case(
        expected: BrandAssetStage,
        source: () -> BrandAssetFetchResult = { fetched(PNG, BrandImageFormat.PNG) },
        decoder: () -> BrandAssetDecoder = { FakeDecoder(BrandImageBounds(10, 10, BrandImageFormat.PNG), mockk()) },
    ) = Case(expected, { _, _ -> source() }, decoder)

    private class Case(
        val expected: BrandAssetStage,
        val source: BrandAssetSource,
        val decoder: () -> BrandAssetDecoder,
    )

    private fun configuration(logo: String? = "/logo.png") =
        SiteSkinValidator.validate(
            manifest(logo).byteInputStream(),
            "https://brand.example",
        ).let { (it as SiteSkinValidationOutcome.Accepted).configuration }

    private fun manifest(logo: String?): String {
        val branding = logo?.let { ""","branding":{"logoUrl":"$it"}""" }.orEmpty()
        return """{"schemaVersion":"1.0","site":{"id":"brand","name":"Bloom"}$branding}"""
    }

    private class FakeDecoder(
        private val bounds: BrandImageBounds?,
        private val bitmap: Bitmap?,
    ) : BrandAssetDecoder {
        var probes = 0
        var decodes = 0

        override suspend fun probe(bytes: ByteArray): BrandImageBounds? {
            probes += 1
            return bounds
        }

        override suspend fun decode(bytes: ByteArray): Bitmap? {
            decodes += 1
            return bitmap
        }
    }

    private companion object {
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val MONOGRAM = BrandAsset.Monogram("B")
        fun fetched(bytes: ByteArray, format: BrandImageFormat) = BrandAssetFetchResult.Fetched(bytes, format)
        fun rejected(reason: BrandAssetRejection = BrandAssetRejection.HTTP_ERROR) =
            BrandAssetFetchResult.Rejected(reason)
    }
}
