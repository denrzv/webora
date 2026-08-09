package app.webora.browser.siteskin

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapBrandAssetDecoderTest {
    @Test fun probesAndDecodesPngWithRealBitmapFactory() = runBlocking {
        val source = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val decoder = BitmapBrandAssetDecoder()

        assertEquals(BrandImageBounds(WIDTH, HEIGHT, BrandImageFormat.PNG), decoder.probe(bytes))
        assertNotNull(decoder.decode(bytes))
    }

    private companion object {
        const val WIDTH = 12
        const val HEIGHT = 8
    }
}
