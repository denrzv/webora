package app.webora.browser.siteskin

import android.graphics.Bitmap
import dev.siteskin.core.SiteSkinValidationOutcome
import dev.siteskin.core.SiteSkinValidator
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BrandAssetLoaderTest {
    @Test fun `missing logo returns monogram without transport`() = runTest {
        var fetched = false
        val loader = loader(source = BrandAssetSource { _, _ -> fetched = true; BrandAssetFetchResult.Rejected })

        assertEquals(BrandAsset.Monogram("B"), loader.load(configuration(logo = null)))
        assertTrue(!fetched)
    }

    @Test fun `matching format and safe bounds decode a bitmap`() = runTest {
        val bitmap = mockk<Bitmap>()
        val decoder = FakeDecoder(BrandImageBounds(64, 48, BrandImageFormat.PNG), bitmap)

        val result = loader(decoder = decoder).load(configuration())

        assertSame(bitmap, (result as BrandAsset.BitmapAsset).bitmap)
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
            val result = loader(source = BrandAssetSource { _, _ -> fetchResult }, decoder = decoder)
                .load(configuration())
            assertEquals(BrandAsset.Monogram("B"), result)
            assertEquals(0, decoder.decodes)
        }
    }

    @Test fun `transport decode and unexpected failures use fallback`() = runTest {
        val rejected = loader(source = BrandAssetSource { _, _ -> BrandAssetFetchResult.Rejected })
        assertEquals(BrandAsset.Monogram("B"), rejected.load(configuration()))

        val nullDecode = loader(decoder = FakeDecoder(BrandImageBounds(10, 10, BrandImageFormat.PNG), null))
        assertEquals(BrandAsset.Monogram("B"), nullDecode.load(configuration()))

        val throwing = loader(source = BrandAssetSource { _, _ -> error("decoder boundary") })
        assertEquals(BrandAsset.Monogram("B"), throwing.load(configuration()))
    }

    @Test fun `cancellation propagates instead of becoming fallback`() = runTest {
        val loader = loader(source = BrandAssetSource { _, _ -> throw CancellationException("superseded") })

        try {
            loader.load(configuration())
            fail("CancellationException expected")
        } catch (_: CancellationException) {
            // Expected: cancellation is lifecycle control, not a remote failure result.
        }
    }

    private fun loader(
        source: BrandAssetSource = BrandAssetSource { _, _ -> fetched(PNG, BrandImageFormat.PNG) },
        decoder: BrandAssetDecoder = FakeDecoder(BrandImageBounds(10, 10, BrandImageFormat.PNG), mockk()),
    ) = BrandAssetLoader(source, decoder)

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
        fun fetched(bytes: ByteArray, format: BrandImageFormat) = BrandAssetFetchResult.Fetched(bytes, format)
    }
}
