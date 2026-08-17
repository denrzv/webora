package app.webora.browser.web

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebViewClient
import android.webkit.WebView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import app.webora.browser.browser.LoadErrorKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardenedWebViewClientTest {
    @Test fun `successful main frame completion publishes browser observed URL and title`() {
        val view = mockk<WebView>()
        every { view.title } returns "Observed title"
        var completed: Pair<String, String?>? = null
        val client = HardenedWebViewClient(
            onMainFrameCompleted = { _, url, title -> completed = url to title },
        )

        client.onPageFinished(view, "https://example.com/page")

        assertEquals("https://example.com/page" to "Observed title", completed)
    }

    @Test fun `failed main frame finish is not completion and next navigation can complete`() {
        val view = mockk<WebView>()
        val request = mockk<WebResourceRequest>()
        val error = mockk<WebResourceError>()
        val uri = mockk<Uri>()
        every { view.title } returns "Page"
        every { request.isForMainFrame } returns true
        every { request.url } returns uri
        every { uri.toString() } returns "https://failed.example/"
        every { error.errorCode } returns WebViewClient.ERROR_CONNECT
        val completions = mutableListOf<String>()
        val client = HardenedWebViewClient(
            onMainFrameCompleted = { _, url, _ -> completions += url },
        )

        client.onPageStarted(view, "https://failed.example/", null)
        client.onReceivedError(view, request, error)
        client.onPageFinished(view, "https://failed.example/")
        client.onPageStarted(view, "https://safe.example/", null)
        client.onPageFinished(view, "https://safe.example/")

        assertEquals(listOf("https://safe.example/"), completions)
    }

    @Test
    fun `navigation policy keeps web urls and emits only supported external schemes`() {
        assertFalse(shouldOverrideNavigation("https://example.com", true) { error("must not emit") })
        var emitted: String? = null
        assertTrue(shouldOverrideNavigation("mailto:hello@example.com", true) { emitted = it.uri })
        assertEquals("mailto:hello@example.com", emitted)
        emitted = null
        assertTrue(shouldOverrideNavigation("intent://attack", true) { emitted = it.uri })
        assertEquals(null, emitted)
    }

    @Test
    fun `subframes cannot request external navigation`() {
        var emitted = false
        assertFalse(shouldOverrideNavigation("mailto:hello@example.com", false) { emitted = true })
        assertFalse(emitted)
    }

    @Test
    fun `a subresource error cannot replace the page`() {
        // `BROWSE-004`'s main-frame filter had no test until BROWSE-009's negative control looked
        // for one: dropping `request.isForMainFrame` left the whole suite green. An image that 404s
        // is not a page that failed to load, and an error page over a working page is a worse
        // failure than the one it would be reporting.
        val view = mockk<WebView>()
        val request = mockk<WebResourceRequest>()
        val error = mockk<WebResourceError>()
        val uri = mockk<Uri>()
        every { view.title } returns "Page"
        every { request.isForMainFrame } returns false
        every { request.url } returns uri
        every { uri.toString() } returns "https://cdn.example/missing.png"
        every { error.errorCode } returns WebViewClient.ERROR_HOST_LOOKUP
        var failed = false
        val completions = mutableListOf<String>()
        val client = HardenedWebViewClient(
            onMainFrameCompleted = { _, url, _ -> completions += url },
            onMainFrameFailed = { _, _ -> failed = true },
        )

        client.onPageStarted(view, "https://example.com/page", null)
        client.onReceivedError(view, request, error)
        client.onPageFinished(view, "https://example.com/page")

        assertFalse("a subresource failure is not a page failure", failed)
        assertEquals("the page still completed", listOf("https://example.com/page"), completions)
    }

    @Test
    fun `a cancelled main frame handshake settles the page exactly once`() {
        val view = mockk<WebView>()
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = mockk<SslError>()
        every { view.title } returns "Page"
        every { error.url } returns TLS_URL
        val failures = mutableListOf<Pair<String, LoadErrorKind>>()
        val completions = mutableListOf<String>()
        val client = HardenedWebViewClient(
            onMainFrameCompleted = { _, url, _ -> completions += url },
            onMainFrameFailed = { url, kind -> failures += url to kind },
        )

        client.onPageStarted(view, TLS_URL, null)
        client.onReceivedSslError(view, handler, error)
        // A second delivery for the same navigation — the framework may also raise
        // ERROR_FAILED_SSL_HANDSHAKE through onReceivedError, and one navigation is one failure.
        client.onReceivedSslError(view, handler, error)
        client.onPageFinished(view, TLS_URL)

        verify(exactly = 2) { handler.cancel() }
        verify(exactly = 0) { handler.proceed() }
        assertEquals(listOf(TLS_URL to LoadErrorKind.TLS), failures)
        assertTrue("a failed navigation is not a completion", completions.isEmpty())
    }

    @Test
    fun `a handshake failure below the main frame cancels without replacing the page`() {
        // The reason BROWSE-004 published nothing from here: this callback does not identify the
        // frame, so a subframe or subresource on another host must not become the page's error.
        val view = mockk<WebView>()
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = mockk<SslError>()
        every { error.url } returns "https://tracker.example/pixel.gif"
        var failed = false
        val client = HardenedWebViewClient(onMainFrameFailed = { _, _ -> failed = true })

        client.onPageStarted(view, TLS_URL, null)
        client.onReceivedSslError(view, handler, error)

        verify(exactly = 1) { handler.cancel() }
        assertFalse("a subresource must not replace the page", failed)
    }

    @Test
    fun `the TLS settlement rule is total and fails closed`() {
        assertEquals(TLS_URL, mainFrameTlsFailure(TLS_URL, TLS_URL, null))
        assertNull("no URL is no claim", mainFrameTlsFailure(null, TLS_URL, null))
        assertNull("a blank URL is no claim", mainFrameTlsFailure(" ", " ", null))
        assertNull("no observed main frame is no claim", mainFrameTlsFailure(TLS_URL, null, null))
        assertNull("already settled stays settled", mainFrameTlsFailure(TLS_URL, TLS_URL, TLS_URL))
    }

    @Test
    fun `the renderer never proceeds through a certificate error`() {
        // Code, not prose. The first version of this test read the whole file and failed on its own
        // KDoc, which says `handler.proceed()` appears nowhere — the same trap `UX-002` recorded,
        // where a comment describing a rule participates in the rule.
        val offenders = mainSources()
            .flatMap { file -> file.code().map { file.name to it } }
            .filter { (_, line) -> line.contains("handler.proceed(") }

        assertTrue("a certificate error may never be proceeded through: $offenders", offenders.isEmpty())
        assertTrue(
            "the cancel must be reachable code, not a comment",
            source("app/webora/browser/web/HardenedWebViewClient.kt").code().any {
                it.contains("handler.cancel()")
            },
        )
        assertFalse(
            "negative control: the scan must see a proceed in code",
            listOf("        handler.proceed()").none { it.contains("handler.proceed(") },
        )
    }

    private fun File.code(): List<String> = readLines()
        .map(String::trim)
        .filterNot { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }

    private fun mainSources(): List<File> = mainSourceRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun source(relative: String): File = File(mainSourceRoot, relative)

    private val mainSourceRoot: File
        get() = requireNotNull(System.getProperty(SOURCE_ROOT_PROPERTY))
            .split(File.pathSeparator)
            .map(::File)
            .single { it.invariantSeparatorsPath.endsWith("/src/main/java") }

    @Test
    fun `framework errors map to closed browser owned reasons`() {
        assertEquals(LoadErrorKind.CONNECTION, classifyWebViewError(WebViewClient.ERROR_HOST_LOOKUP))
        assertEquals(LoadErrorKind.NETWORK, classifyWebViewError(WebViewClient.ERROR_IO))
        assertEquals(LoadErrorKind.TLS, classifyWebViewError(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE))
        assertEquals(LoadErrorKind.UNKNOWN, classifyWebViewError(Int.MIN_VALUE))
    }

    private companion object {
        const val TLS_URL = "https://expired.example/"
        const val SOURCE_ROOT_PROPERTY = "webora.app.src"
    }
}
