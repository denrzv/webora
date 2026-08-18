package dev.siteskin.core

import dev.siteskin.core.model.HubPresentation
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

    @Test fun `every recognised hub token normalizes to its closed enum value`() {
        mapOf(
            "auto" to HubPresentation.AUTO,
            "bouquet" to HubPresentation.BOUQUET,
            "drawer" to HubPresentation.DRAWER,
        ).forEach { (token, expected) ->
            val result = validate(
                """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
                "presentation":{"hub":"$token"}}""",
            )
            assertEquals(token, expected, result.configuration!!.presentation!!.hub)
            assertEquals(token, emptyList<DiagnosticCode>(), result.diagnostics.map { it.code })
        }
    }

    /**
     * The three ways of arriving at `AUTO`, kept apart on purpose.
     *
     * A wrongly-cased token is the interesting row: `HUBS` is a lookup and not a case-insensitive
     * comparison, so `"Drawer"` is an unrecognised value and warns. Lower-casing at read time would
     * make two spellings of one token — the leading-zero `schemaVersion` mistake `SPEC-002` fixed
     * by narrowing the grammar rather than normalizing, and the same reasoning applies to a field
     * whose whole grammar is `^[a-z]…`.
     */
    @Test fun `unknown wrongly-cased and absent hub values all reach AUTO`() {
        val declared = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
            "presentation":{"hub":"Drawer"}}""",
        )
        assertEquals(HubPresentation.AUTO, declared.configuration!!.presentation!!.hub)
        assertEquals(listOf(DiagnosticCode.PRESENTATION_UNKNOWN), declared.diagnostics.map { it.code })
        assertEquals("/presentation/hub", declared.diagnostics.single().pointer)

        val emptyObject = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},"presentation":{}}""",
        )
        assertEquals(HubPresentation.AUTO, emptyObject.configuration!!.presentation!!.hub)
        assertEquals(emptyList<DiagnosticCode>(), emptyObject.diagnostics.map { it.code })

        val absent = validate(MINIMAL)
        assertNull(absent.configuration!!.presentation)
        assertEquals(HubPresentation.AUTO, absent.configuration!!.hubPresentation)
    }

    /**
     * The accessor exists so no consumer writes `?: AUTO`, and a declared `auto` must be
     * indistinguishable from an absent object *to a consumer* while staying distinguishable in the
     * canonical result. Both halves are asserted here, because collapsing them in the model is the
     * change that would rewrite every pinned `.expected.json`.
     */
    @Test fun `the accessor collapses an absent object and a declared auto`() {
        val absent = validate(MINIMAL).configuration!!
        val declared = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
            "presentation":{"hub":"auto"}}""",
        ).configuration!!

        assertEquals(absent.hubPresentation, declared.hubPresentation)
        assertNull(absent.presentation)
        assertNotNull(declared.presentation)
    }

    /**
     * A hub value structurally cannot carry a resource reference, and the refusal is the schema's
     * pattern rather than the security layer's allow-list. This asserts the security half only:
     * whatever reaches the normalizer, the trusted model holds an enum and never a site-authored
     * string, so no consumer can be handed a URL by this field.
     */
    @Test fun `a resource-like hub value never survives as a string`() {
        val result = validate(
            """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"},
            "presentation":{"hub":"https://evil.example/x"}}""",
        )
        assertEquals(HubPresentation.AUTO, result.configuration!!.presentation!!.hub)
        assertEquals(listOf(DiagnosticCode.PRESENTATION_UNKNOWN), result.diagnostics.map { it.code })
    }

    private fun validate(body: String): SecurityValidationResult = SecurityValidator.validate(
        json.parseToJsonElement(body).jsonObject,
        "https://shop.example",
    )

    private companion object {
        const val MINIMAL = """{"schemaVersion":"1.0","site":{"id":"shop","name":"Shop"}}"""
    }
}
