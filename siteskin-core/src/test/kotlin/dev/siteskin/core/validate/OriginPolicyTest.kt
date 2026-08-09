package dev.siteskin.core.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginPolicyTest {
    @Test fun `canonical HTTPS origin resolves same-origin paths`() {
        val origin = OriginPolicy.parse("https://Example.COM:443")!!
        assertEquals("https://example.com", origin.value)
        assertEquals("https://example.com/catalog", origin.resolveInternal("/shop/../catalog"))
    }

    @Test fun `origin boundary rejects deceptive references`() {
        val origin = OriginPolicy.parse("https://example.com")!!
        listOf(
            "//evil.example/x", "https://evil.example/x", "https://example.com:8443/x",
            "https://example.com@evil.example/x", "/../../evil",
        ).forEach { assertNull(it, origin.resolveInternal(it)) }
    }

    @Test fun `serving origin must be an HTTPS root without credentials`() {
        listOf("http://example.com", "https://u@example.com", "https://example.com/path", "not a uri")
            .forEach { assertNull(it, OriginPolicy.parse(it)) }
    }

    @Test fun `external URLs are absolute HTTPS only`() {
        assertEquals("https://other.example/x", OriginPolicy.resolveExternal("https://other.example/x"))
        listOf("http://other.example", "javascript:alert(1)", "/relative", "https://u@other.example")
            .forEach { assertNull(it, OriginPolicy.resolveExternal(it)) }
    }
}
