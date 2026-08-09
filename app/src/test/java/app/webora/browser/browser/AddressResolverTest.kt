package app.webora.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressResolverTest {
    @Test
    fun `resolves explicit and host-like web addresses`() {
        assertEquals("https://example.com/path?q=1", resolveAddressInput("https://EXAMPLE.com/path?q=1"))
        assertEquals("https://example.com/path", resolveAddressInput("example.com/path"))
        assertEquals("http://localhost:8080", resolveAddressInput("http://localhost:8080"))
    }

    @Test
    fun `encodes ordinary text through browser-owned search`() {
        assertEquals(
            "https://www.google.com/search?q=flowers+near+me",
            resolveAddressInput("flowers near me"),
        )
    }

    @Test
    fun `rejects denied or ambiguous destinations`() {
        listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://settings/system",
            "https://user:password@example.com",
            "https://example.com/#fragment",
            "https://exa mple.com",
            "https://example.com\n.evil.test",
            "",
        ).forEach { assertNull(it, resolveAddressInput(it)) }
    }
}
