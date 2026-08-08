package dev.siteskin.core.manifest

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinManifestDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun completeManifestMapsEverySchemaField() {
        val manifest = json.decodeFromString<SiteSkinManifestDto>(COMPLETE_MANIFEST)

        assertEquals("1.0", manifest.schemaVersion)
        assertEquals(SiteDto("bloom", "Bloom Flowers", "Bloom", "/"), manifest.site)
        assertEquals("#112233", manifest.branding?.primaryColor)
        assertEquals("/logo.png", manifest.branding?.logoUrl)
        assertEquals(ToolbarDto("Bloom", "Fresh"), manifest.toolbar)
        assertEquals("home", manifest.bottomNavigation?.single()?.id)
        assertEquals("internal_url", manifest.bottomNavigation?.single()?.action?.type)
        assertEquals(listOf("/", "/catalog/**"), manifest.bottomNavigation?.single()?.match)
        assertEquals("menu-home", manifest.menu?.single()?.id)
        assertEquals("call", manifest.quickActions?.single()?.icon)
        assertEquals("+10000000000", manifest.quickActions?.single()?.action?.value)
    }

    @Test
    fun missingSchemaRequiredFieldsRemainUntrustedData() {
        val manifest = json.decodeFromString<SiteSkinManifestDto>("{}")

        assertNull(manifest.schemaVersion)
        assertNull(manifest.site)
        assertNull(manifest.branding)
        assertNull(manifest.bottomNavigation)
    }

    @Test
    fun dtoApiDoesNotExposeTrustedConfiguration() {
        val dtoProperties = SiteSkinManifestDto::class.java.declaredFields.map { it.type.name }
        val dtoMethods = SiteSkinManifestDto::class.java.declaredMethods.map { it.returnType.name }

        assertFalse((dtoProperties + dtoMethods).any { it.endsWith("SiteSkinConfiguration") })
        assertTrue(SiteSkinManifestDto::class.java.simpleName.endsWith("Dto"))
    }

    private companion object {
        val COMPLETE_MANIFEST =
            """
            {
              "schemaVersion":"1.0",
              "site":{"id":"bloom","name":"Bloom Flowers","shortName":"Bloom","homeUrl":"/"},
              "branding":{"primaryColor":"#112233","secondaryColor":"#223344","backgroundColor":"#ffffff","textColor":"#000000","logoUrl":"/logo.png"},
              "toolbar":{"title":"Bloom","subtitle":"Fresh"},
              "bottomNavigation":[{"id":"home","label":"Home","icon":"home","action":{"type":"internal_url","url":"/"},"match":["/","/catalog/**"]}],
              "menu":[{"id":"menu-home","label":"Menu home","action":{"type":"home"}}],
              "quickActions":[{"id":"phone","label":"Call","icon":"call","action":{"type":"phone","value":"+10000000000"}}]
            }
            """.trimIndent()
    }
}
