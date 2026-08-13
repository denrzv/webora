package dev.siteskin.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SecurityValidatorTest {
    private val json = Json

    @Test fun `normalizes safe manifest into trusted configuration`() {
        val result = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
            "branding":{"primaryColor":"#abc"},"bottomNavigation":[
            {"id":"home","label":"Home","icon":"home","action":{"type":"internal_url","url":"/"}}]}""",
        )
        assertNotNull(result.configuration)
        assertEquals("https://shop.example/", result.configuration!!.site.homeUrl)
        assertEquals("#AABBCC", result.configuration!!.branding!!.primaryColor)
        assertEquals("https://shop.example/", result.configuration!!.bottomNavigation!!.single().action.url)
    }

    @Test fun `drops denied action unknown icon and later duplicate then clamps`() {
        val longLabel = "x".repeat(40)
        val result = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},"bottomNavigation":[
            {"id":"safe","label":"$longLabel","icon":"future_icon","action":{"type":"internal_url","url":"/"}},
            {"id":"unsafe","label":"Unsafe","action":{"type":"external_url","url":"javascript:alert(1)"}},
            {"id":"safe","label":"Later","action":{"type":"refresh"}}]}""",
        )
        val item = result.configuration!!.bottomNavigation!!.single()
        assertEquals("x".repeat(32), item.label)
        assertEquals("generic", item.icon)
        assertEquals(
            listOf(
                DiagnosticCode.SCHEME_DENIED, DiagnosticCode.ICON_UNKNOWN,
                DiagnosticCode.DUPLICATE_ID, DiagnosticCode.LIMIT_TRUNCATED,
            ),
            result.diagnostics.map { it.code },
        )
    }

    @Test fun `semantic icon vocabulary passes while resource-like input falls back`() {
        val icons = listOf(
            "home", "catalog", "flower", "grid_view", "shopping_cart", "person", "call", "share", "menu", "search",
            "ic_launcher",
        )
        val items = icons.mapIndexed { index, icon ->
            """{"id":"item-$index","label":"Item $index","icon":"$icon","action":{"type":"refresh"}}"""
        }.joinToString(",")

        val result = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},"menu":[$items]}""",
        )

        assertEquals(icons.dropLast(1) + "generic", result.configuration!!.menu!!.map { it.icon })
        assertEquals(listOf(DiagnosticCode.ICON_UNKNOWN), result.diagnostics.map { it.code })
    }

    @Test fun `diagnostics follow normalization stages across one manifest`() {
        val items = (1..8).joinToString(",") { index ->
            val id = if (index == 3) "item-2" else "item-$index"
            val url = if (index == 1) "https://evil.example" else "/$index"
            """{"id":"$id","label":"Item $index","icon":"home",
                "action":{"type":"internal_url","url":"$url"}}"""
        }
        val result = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
            "branding":{"backgroundColor":"#777777","textColor":"#777777"},
            "toolbar":{"title":"${"t".repeat(70)}"},"bottomNavigation":[$items]}""",
        )
        assertEquals(
            listOf(
                DiagnosticCode.ORIGIN_MISMATCH,
                DiagnosticCode.DUPLICATE_ID,
                DiagnosticCode.LIMIT_TRUNCATED,
                DiagnosticCode.LIMIT_TRUNCATED,
                DiagnosticCode.CONTRAST_CORRECTED,
            ),
            result.diagnostics.map { it.code },
        )
        assertEquals(
            listOf(
                "/bottomNavigation/0/action/url", "/bottomNavigation/2", "/bottomNavigation",
                "/toolbar/title", "/branding/backgroundColor",
            ),
            result.diagnostics.map { it.pointer },
        )
    }

    @Test fun `invalid serving origin cannot construct trusted configuration`() {
        val result = SecurityValidator.validate(
            json.parseToJsonElement(MINIMAL).jsonObject,
            "http://shop.example",
        )
        assertNull(result.configuration)
    }

    @Test fun `string truncation preserves a grapheme cluster`() {
        val title = "a".repeat(63) + "e\u0301tail"
        val result = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
            "toolbar":{"title":"$title"}}""",
        )
        assertEquals("a".repeat(63) + "e\u0301", result.configuration!!.toolbar!!.title)
    }

    private fun validate(body: String): SecurityValidationResult = SecurityValidator.validate(
        json.parseToJsonElement(body).jsonObject,
        "https://shop.example",
    )

    private companion object {
        const val MINIMAL = """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}"""
    }
}
