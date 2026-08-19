package app.webora.browser.inspector

import app.webora.browser.siteskin.BrandAssetRejection
import app.webora.browser.siteskin.FetchRejection
import app.webora.browser.siteskin.HubSurface
import app.webora.browser.siteskin.SiteConsentDecision
import dev.siteskin.core.SiteSkinLimits
import dev.siteskin.core.model.HubPresentation
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The projection: which fields, in which order, bounded how.
 *
 * Separate from `JsonDocumentTest` because the questions are different. That file asks whether the
 * writer produces valid JSON; this one asks whether the document says exactly what the panel says —
 * no more, which is a disclosure question, and no less, which is a drift question.
 */
class InspectorJsonTest {

    @Test fun `a fully populated snapshot pins names, order and nesting in one place`() {
        assertEquals(GOLDEN, inspectorJson(populated()))
    }

    @Test fun `an empty snapshot is a valid document of nulls rather than half a document`() {
        // The state a site owner debugging "nothing happened" needs to paste. A serializer that
        // could not describe it would be missing exactly the case the inspector exists for.
        val document = inspectorJson(
            InspectorSnapshot(
                origin = null,
                activation = InspectorActivation.PENDING,
                consent = null,
                siteSkinEnabled = true,
                brandAsset = InspectorBrandAsset.NONE,
                brandAssetTrace = null,
                record = null,
                applied = null,
            ),
        )

        assertEquals(
            listOf(
                "{",
                """  "origin": null,""",
                """  "activation": "PENDING",""",
                """  "consent": null,""",
                """  "siteSkinEnabled": true,""",
                """  "brandAsset": "NONE",""",
                """  "brandAssetTrace": null,""",
                """  "record": null,""",
                """  "applied": null""",
                "}",
            ).joinToString("\n"),
            document,
        )
    }

    @Test fun `an absent value is null and never the panel's dash`() {
        // `—` is R.string.inspector_absent, a display convention. Exporting it would make a glyph a
        // sentinel every consumer has to know about, and would fail type preservation while looking
        // like it passed.
        val document = inspectorJson(populated().copy(consent = null))

        assertTrue(document.contains(""""consent": null"""))
        assertFalse(document.contains("—"))
    }

    @Test fun `a hostile diagnostic pointer arrives bounded and flattened`() {
        // SS-W-FIELD-UNKNOWN reports the key it did not recognise, so this is the one field in the
        // document that is arbitrary website text. The panel bounds it at its render site; a
        // serializer reading the raw model would export more than the surface it mirrors.
        val hostile = "‮" + "a\nb c" + "x".repeat(HOSTILE_LENGTH)

        val document = inspectorJson(populated(pointer = hostile))
        val emitted = document.lineSequence()
            .single { it.trimStart().startsWith(""""pointer":""") }
            .substringAfter(""""pointer": """")
            .substringBeforeLast(""""""")

        // The panel's bound, applied to the same input. Asserting equality rather than a length
        // ceiling is what makes this the "same values the Inspector renders" criterion: a serializer
        // that bounded at a *different* limit would satisfy a ceiling and still disagree with the
        // surface it claims to mirror.
        assertEquals(inspectorValue(hostile), emitted)
        assertFalse("a format character must not survive into a pasted document", emitted.contains("\u202E"))
        assertFalse("a newline must not forge a second field", emitted.contains("\\n"))
        assertTrue(
            "the value must be bounded, was ${emitted.length}",
            emitted.length <= SiteSkinLimits.MAX_SUBTITLE_LENGTH,
        )
        assertTrue("the raw value must not survive", hostile.length > emitted.length)
    }

    @Test fun `the payload is a function of the model, never of the layout`() {
        // The panel scrolls and clips; the document does neither. Twenty menu items is SPEC.md's
        // permitted maximum, and all five navigation items must appear whatever a viewport shows.
        val navigation = (1..NAVIGATION_ITEMS).map { InspectorItem("item$it", "Label $it", "internal_url", false) }

        val document = inspectorJson(populated(navigation = navigation))

        assertEquals(NAVIGATION_ITEMS, Regex("""\s+"id": """").findAll(document).count())
        (1..NAVIGATION_ITEMS).forEach { assertTrue(document.contains(""""id": "item$it"""")) }
    }

    @Test fun `serializing one snapshot twice is byte-identical`() {
        val snapshot = populated()

        assertEquals(inspectorJson(snapshot), inspectorJson(snapshot))
    }

    @Test fun `a rejected record serializes as validly as an accepted one`() {
        val document = inspectorJson(
            populated().copy(
                activation = InspectorActivation.UNAVAILABLE,
                applied = null,
                record = ManifestTraceRecord(
                    origin = ORIGIN,
                    generation = 7,
                    transport = ManifestTransportTrace(
                        manifestUrl = MANIFEST_URL,
                        outcome = TraceTransportOutcome.REJECTED,
                        cacheState = TraceCacheState.MISS,
                        httpStatus = HTTP_NOT_FOUND,
                        rejection = FetchRejection.HTTP_ERROR,
                    ),
                    validation = ManifestValidationTrace(result = TraceValidationResult.NOT_RUN),
                ),
            ),
        )

        assertTrue(document.contains(""""outcome": "REJECTED""""))
        assertTrue(document.contains(""""rejection": "HTTP_ERROR""""))
        assertTrue(document.contains(""""schemaVersion": null"""))
        assertTrue(document.contains(""""diagnostics": []"""))
        assertTrue(document.contains(""""applied": null"""))
    }

    @Test fun `every declared field of every reachable record appears as a key`() {
        // Totality, reflectively. A hand-written expectation is correct on the day it is written;
        // this covers the field nobody remembered to serialize, which is how a display and a
        // clipboard silently stop agreeing.
        val document = inspectorJson(populated())
        val missing = reachableRecords().flatMap { type ->
            type.serializableFields().map { it.name }
        }.distinct().filterNot { document.contains("\"$it\":") }

        assertTrue("these snapshot fields never reach the copied document: $missing", missing.isEmpty())
    }

    @Test fun `the sweep is not vacuous`() {
        // A reflective walk that found nothing would pass forever. Pin the floor so a broken filter
        // fails here rather than quietly disabling the rule above.
        val records = reachableRecords()

        assertTrue("expected the snapshot graph to have records; found ${records.size}", records.size >= MIN_RECORDS)
    }

    @Test fun `the payload can only hold what a diagnostic record may hold`() {
        // The issue's requirement 10, as a property of the model rather than an assertion about
        // today's fields. A cookie, an authorization header, a Map of response headers, a ByteArray
        // of page bytes or a Bitmap cannot be added to InspectorSnapshot — with or without this
        // ticket's serializer — without turning this red.
        val offenders = mutableListOf<String>()
        walkTypes(InspectorSnapshot::class.java, mutableSetOf()) { owner, field, type ->
            if (!type.isPermittedInAPayload()) offenders += "${owner.simpleName}.${field}: ${type.name}"
        }

        assertTrue(
            "a diagnostic payload may hold only numbers, booleans, bounded strings, closed enums, " +
                "lists and other inspector records:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test fun `the document is built from one snapshot and nothing else`() {
        // The anti-mixing control, as a signature. A copy path that could reach the recorder, the
        // browser mode or the active tab is how one origin's transport joins another's applied
        // chrome. Two-sided because erasure alone cannot answer it: arity and erased types come from
        // the compiled method, the declared type from the declaration line, per UX-026's note on the
        // same shape.
        val method = Class.forName("app.webora.browser.inspector.InspectorJsonKt")
            .declaredMethods
            .single { it.name == "inspectorJson" }

        assertEquals(1, method.parameterCount)
        assertEquals(InspectorSnapshot::class.java, method.parameterTypes.single())
        assertEquals(String::class.java, method.returnType)

        val declaration = INSPECTOR_JSON_SOURCE.readText()
            .lineSequence()
            .single { it.startsWith("internal fun inspectorJson(") }
        assertEquals("internal fun inspectorJson(snapshot: InspectorSnapshot): String = JsonObject(", declaration)
    }

    private fun populated(
        pointer: String = "/unknownField",
        navigation: List<InspectorItem> = listOf(InspectorItem("home", "Home", "internal_url", true)),
    ) = InspectorSnapshot(
        origin = ORIGIN,
        activation = InspectorActivation.INTEGRATED,
        consent = SiteConsentDecision.ALLOW,
        siteSkinEnabled = true,
        brandAsset = InspectorBrandAsset.DECODED_BITMAP,
        brandAssetTrace = BrandAssetTrace(
            stage = BrandAssetStage.DECODED,
            rejection = null,
            httpStatus = HTTP_OK,
            redirects = 0,
            width = LOGO_EDGE,
            height = LOGO_EDGE,
            elapsedMillis = ELAPSED,
            attempts = 1,
        ),
        record = acceptedRecord(pointer),
        applied = appliedChrome(navigation),
    )

    private fun acceptedRecord(pointer: String) = ManifestTraceRecord(
        origin = ORIGIN,
        generation = 3,
        transport = ManifestTransportTrace(
            manifestUrl = MANIFEST_URL,
            outcome = TraceTransportOutcome.FETCHED,
            cacheState = TraceCacheState.MISS,
            httpStatus = HTTP_OK,
            redirects = 0,
            rejection = null,
        ),
        validation = ManifestValidationTrace(
            result = TraceValidationResult.ACCEPTED,
            schemaVersion = "1.0",
            diagnostics = listOf(TraceDiagnostic("SS-W-FIELD-UNKNOWN", pointer)),
        ),
    )

    private fun appliedChrome(navigation: List<InspectorItem>) = InspectorAppliedChrome(
        siteName = "Bloom Flowers",
        siteId = "bloom",
        homeUrl = "https://shop.example/",
        activeNavigationId = "home",
        counts = listOf(InspectorItemCount(InspectorCollection.BOTTOM_NAVIGATION, 4, 4)),
        navigation = navigation,
        hub = InspectorHub(requested = HubPresentation.DRAWER, effective = HubSurface.DRAWER),
        theme = InspectorTheme(
            darkTheme = false,
            roles = listOf(InspectorColorRole(InspectorColorRoleName.PRIMARY, "#D94F8A", "#D94F8A")),
        ),
    )

    private companion object {
        const val ORIGIN = "https://shop.example"
        const val MANIFEST_URL = "https://shop.example/.well-known/siteskin.json"
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        const val LOGO_EDGE = 512
        const val ELAPSED = 891L
        const val NAVIGATION_ITEMS = 5
        const val HOSTILE_LENGTH = 400
        const val MIN_RECORDS = 10

        val INSPECTOR_JSON_SOURCE =
            java.io.File("src/debug/java/app/webora/browser/inspector/InspectorJson.kt")
                .also { require(it.isFile) { "InspectorJson.kt not found at ${it.absolutePath}" } }

        val GOLDEN = listOf(
            "{",
            """  "origin": "https://shop.example",""",
            """  "activation": "INTEGRATED",""",
            """  "consent": "ALLOW",""",
            """  "siteSkinEnabled": true,""",
            """  "brandAsset": "DECODED_BITMAP",""",
            """  "brandAssetTrace": {""",
            """    "stage": "DECODED",""",
            """    "rejection": null,""",
            """    "httpStatus": 200,""",
            """    "redirects": 0,""",
            """    "width": 512,""",
            """    "height": 512,""",
            """    "elapsedMillis": 891,""",
            """    "attempts": 1""",
            "  },",
            """  "record": {""",
            """    "origin": "https://shop.example",""",
            """    "generation": 3,""",
            """    "transport": {""",
            """      "manifestUrl": "https://shop.example/.well-known/siteskin.json",""",
            """      "outcome": "FETCHED",""",
            """      "cacheState": "MISS",""",
            """      "httpStatus": 200,""",
            """      "redirects": 0,""",
            """      "rejection": null""",
            "    },",
            """    "validation": {""",
            """      "result": "ACCEPTED",""",
            """      "schemaVersion": "1.0",""",
            """      "diagnostics": [""",
            "        {",
            """          "code": "SS-W-FIELD-UNKNOWN",""",
            """          "pointer": "/unknownField"""",
            "        }",
            "      ]",
            "    }",
            "  },",
            """  "applied": {""",
            """    "siteName": "Bloom Flowers",""",
            """    "siteId": "bloom",""",
            """    "homeUrl": "https://shop.example/",""",
            """    "activeNavigationId": "home",""",
            """    "counts": [""",
            "      {",
            """        "collection": "BOTTOM_NAVIGATION",""",
            """        "trusted": 4,""",
            """        "rendered": 4,""",
            """        "diverged": false""",
            "      }",
            "    ],",
            """    "navigation": [""",
            "      {",
            """        "id": "home",""",
            """        "label": "Home",""",
            """        "actionType": "internal_url",""",
            """        "active": true""",
            "      }",
            "    ],",
            """    "hub": {""",
            """      "requested": "DRAWER",""",
            """      "effective": "DRAWER"""",
            "    },",
            """    "theme": {""",
            """      "darkTheme": false,""",
            """      "roles": [""",
            "        {",
            """          "role": "PRIMARY",""",
            """          "trusted": "#D94F8A",""",
            """          "applied": "#D94F8A"""",
            "        }",
            "      ]",
            "    }",
            "  }",
            "}",
        ).joinToString("\n")

        /**
         * Every record type reachable from [InspectorSnapshot].
         *
         * Discovered rather than listed, for the reason the whole sweep exists: a listed set leaves
         * a new record uncovered by default.
         */
        fun reachableRecords(): List<Class<*>> {
            val found = mutableSetOf<Class<*>>()
            walkTypes(InspectorSnapshot::class.java, found) { _, _, _ -> }
            return found.toList()
        }

        /**
         * Walks the field graph, reporting every leaf type to [onField].
         *
         * [visited] accumulates the record types themselves, which is what makes one walk answer both
         * questions. A `List<T>` contributes its element type, read from the generic signature —
         * erasure alone would report `java.util.List` and hide whatever is inside it.
         */
        fun walkTypes(
            type: Class<*>,
            visited: MutableSet<Class<*>>,
            onField: (Class<*>, String, Class<*>) -> Unit,
        ) {
            if (!visited.add(type)) return
            type.serializableFields().forEach { field ->
                val leaf = field.elementType()
                onField(type, field.name, leaf)
                if (leaf.isInspectorRecord()) walkTypes(leaf, visited, onField)
            }
        }

        /**
         * Constructor-backed instance fields.
         *
         * **Static fields are filtered, not only synthetic ones.** The Compose compiler adds
         * `public static final int $stable` to every stable class and does not mark it synthetic, so
         * a synthetic-only filter fails on correct code — `UX-024` recorded exactly that.
         */
        fun Class<*>.serializableFields() = declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }

        fun java.lang.reflect.Field.elementType(): Class<*> =
            if (List::class.java.isAssignableFrom(type)) {
                (genericType as ParameterizedType).actualTypeArguments.single() as Class<*>
            } else {
                type
            }

        fun Class<*>.isInspectorRecord(): Boolean =
            !isEnum && name.startsWith("app.webora.browser.inspector.")

        /**
         * What a diagnostic value may be.
         *
         * A number, a boolean, a bounded string, a constant of a closed vocabulary, or another
         * inspector record. Nothing else — and an enum is admitted as a category because an enum is
         * a compiled vocabulary rather than data, which is the property `NET-004` relied on when it
         * closed `BrandAssetStage`.
         */
        fun Class<*>.isPermittedInAPayload(): Boolean = when {
            isEnum -> true
            isInspectorRecord() -> true
            else -> name in PERMITTED_SCALARS
        }

        val PERMITTED_SCALARS = setOf(
            "java.lang.String",
            "int",
            "java.lang.Integer",
            "long",
            "java.lang.Long",
            "boolean",
            "java.lang.Boolean",
        )
    }
}
