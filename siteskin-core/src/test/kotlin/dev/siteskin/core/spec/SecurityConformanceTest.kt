package dev.siteskin.core.spec

import dev.siteskin.core.DiagnosticCode
import dev.siteskin.core.ManifestDiagnostic
import dev.siteskin.core.SecurityValidator
import dev.siteskin.core.model.BrandingConfiguration
import dev.siteskin.core.model.NavigationItem
import dev.siteskin.core.model.SiteSkinConfiguration
import dev.siteskin.core.model.ToolbarConfiguration
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityConformanceTest {
    @Test fun `security reachable fixtures match canonical results and diagnostics`() {
        fixtures().forEach { fixture ->
            val result = SecurityValidator.validate(SpecCorpus.parsedBody(fixture), fixture.origin!!)
            assertEquals(fixture.name, fixture.result(), result.configuration!!.canonicalJson())
            assertEquals(fixture.name, expectedDiagnostics(fixture), result.diagnostics)
        }
    }

    private fun fixtures(): List<Fixture> = SpecCorpus.fixtures.filter { fixture ->
        fixture.bodyParses && fixture.schemaValid() && fixture.hasResult &&
            (SpecCorpus.rejectingLayerIndex(fixture)?.let { it < securityLayer } != true)
    }

    private fun expectedDiagnostics(fixture: Fixture): List<ManifestDiagnostic> = fixture.diagnostics
        .filter { it.code != DiagnosticCode.FIELD_UNKNOWN.value }
        .map { expected ->
            val code = DiagnosticCode.entries.single { it.value == expected.code }
            ManifestDiagnostic(code, expected.pointer)
        }

    private val securityLayer = SpecCorpus.layerOrder.indexOf("security")
}

private fun SiteSkinConfiguration.canonicalJson(): JsonObject = buildJsonObject {
    put("schemaVersion", schemaVersion)
    put("origin", origin)
    put("site", buildJsonObject {
        put("id", site.id)
        put("name", site.name)
        site.shortName?.let { put("shortName", it) }
        put("homeUrl", site.homeUrl)
    })
    branding?.let { put("branding", it.canonicalJson()) }
    toolbar?.let { put("toolbar", it.canonicalJson()) }
    // Silent when the manifest was silent. `presentation` is nullable precisely so this line
    // writes nothing for the fixtures that never mentioned it — including bloom-flowers, whose
    // expected body is pinned by SHA-256 in two repositories.
    presentation?.let { presentation ->
        put(
            "presentation",
            buildJsonObject {
                put("hub", presentation.hub.name.lowercase())
                // Omitted when empty, like every other absent optional. A materialised `[]` would
                // mean "the site asked for a dock and nothing survived", which is a different
                // document from one that never asked — SPEC.md section 12's distinction.
                if (presentation.dock.isNotEmpty()) {
                    put("dock", buildJsonArray { presentation.dock.forEach { id -> add(JsonPrimitive(id)) } })
                }
            },
        )
    }
    bottomNavigation?.let { put("bottomNavigation", it.canonicalJson()) }
    menu?.let { put("menu", it.canonicalJson()) }
    quickActions?.let { put("quickActions", it.canonicalJson()) }
}

private fun BrandingConfiguration.canonicalJson(): JsonObject = buildJsonObject {
    primaryColor?.let { put("primaryColor", it) }
    secondaryColor?.let { put("secondaryColor", it) }
    backgroundColor?.let { put("backgroundColor", it) }
    textColor?.let { put("textColor", it) }
    logoUrl?.let { put("logoUrl", it) }
}

private fun ToolbarConfiguration.canonicalJson(): JsonObject = buildJsonObject {
    title?.let { put("title", it) }
    subtitle?.let { put("subtitle", it) }
}

private fun List<NavigationItem>.canonicalJson(): JsonArray = buildJsonArray {
    this@canonicalJson.forEach { item ->
        add(buildJsonObject {
            put("id", item.id)
            put("label", item.label)
            item.icon?.let { put("icon", it) }
            put("action", buildJsonObject {
                put("type", item.action.type)
                item.action.url?.let { put("url", it) }
                item.action.value?.let { put("value", it) }
            })
            if (item.match.isNotEmpty()) put("match", buildJsonArray { item.match.forEach { add(JsonPrimitive(it)) } })
        })
    }
}
