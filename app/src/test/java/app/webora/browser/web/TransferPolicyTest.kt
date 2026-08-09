package app.webora.browser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferPolicyTest {
    @Test
    fun `downloads accept absolute http and https only`() {
        assertEquals("https://example.com/file.pdf", downloadUrl("https://example.com/file.pdf"))
        assertNull(downloadUrl("file:///sdcard/secret"))
        assertNull(downloadUrl("javascript:alert(1)"))
        assertNull(downloadUrl("https:///missing-host"))
    }

    @Test
    fun `upload mime hints are normalized to a bounded allow list`() {
        assertEquals("image/*", uploadMimeType(arrayOf("IMAGE/PNG", "image/jpeg")))
        assertEquals("application/pdf", uploadMimeType(arrayOf("application/pdf")))
        assertNull(uploadMimeType(arrayOf("application/x-dangerous")))
        assertNull(uploadMimeType(emptyArray()))
        assertNull(uploadMimeType(Array(100) { "image/png" }))
    }

    @Test
    fun `only content uri can be returned to a page`() {
        assertEquals("content://picker/document/1", selectedUploadUri("content://picker/document/1"))
        assertNull(selectedUploadUri("file:///sdcard/secret"))
        assertNull(selectedUploadUri("https://example.com/file"))
    }
}
