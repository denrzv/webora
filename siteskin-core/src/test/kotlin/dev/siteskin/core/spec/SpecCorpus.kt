package dev.siteskin.core.spec

import io.github.optimumcode.json.schema.JsonSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Reads the conformance corpus in `spec/` — the contract `:siteskin-core` is written to satisfy.
 *
 * Deliberately parses the fixtures as untyped [JsonObject] rather than into `@Serializable` DTOs.
 * The corpus constrains the implementation, so binding it to our own types would let a change in
 * those types silently change what the corpus means. It also has to keep working for fixtures that
 * are *not* well-formed against the schema, which is most of `invalid/`.
 */
internal object SpecCorpus {

    private val json = Json { ignoreUnknownKeys = false }

    /** Set by the `test` task; see `siteskin-core/build.gradle.kts`. */
    val specDir: File = File(
        requireNotNull(System.getProperty("siteskin.spec.dir")) {
            "siteskin.spec.dir is not set — the test task must point at the repo's spec/ directory"
        },
    )

    val fixturesDir: File = specDir.resolve("fixtures")

    /**
     * The published JSON Schema. Structural validity only — it cannot express origin binding,
     * because it does not know the origin a manifest was served from. See [SpecCorpusTest].
     */
    val schema: JsonSchema by lazy {
        JsonSchema.fromDefinition(specDir.resolve("siteskin-1.0.schema.json").readText())
    }

    /** Validates a fixture body against [schema], returning the failures as readable strings. */
    fun schemaErrorsFor(fixture: Fixture): List<String> {
        val body = json.parseToJsonElement(fixture.bodyFile.readText())
        val errors = mutableListOf<String>()
        schema.validate(body) { errors += "${it.objectPath}: ${it.message}" }
        return errors
    }

    /**
     * True when this fixture is expected to fail the JSON Schema — that is, when any of its
     * expected diagnostics is registered at the `schema` layer. Every other fixture, including the
     * deliberately invalid ones, must *pass* the schema: their rules live in the security layer.
     */
    fun expectsSchemaFailure(fixture: Fixture): Boolean =
        fixture.diagnostics.any { registry[it.code]?.layer == "schema" }

    /**
     * The order validation runs in, read from the registry rather than hardcoded here. Rejection at
     * one layer short-circuits every later one.
     */
    val layerOrder: List<String> by lazy {
        registryRoot.getValue("layerOrder").jsonArray.map { it.jsonPrimitive.content }
    }

    /** Position of a registered code's layer in [layerOrder], or `null` if either is unknown. */
    fun layerIndexOf(code: String): Int? =
        registry[code]?.layer?.let { layer -> layerOrder.indexOf(layer).takeIf { it >= 0 } }

    /**
     * The layer at which this fixture's manifest stops being processed, or `null` if nothing
     * rejects it. Only a `reject` disposition short-circuits: a dropped item or a warning leaves
     * the remaining layers to run.
     */
    fun rejectingLayerIndex(fixture: Fixture): Int? =
        fixture.diagnostics
            .filter { it.disposition == "reject" }
            .mapNotNull { layerIndexOf(it.code) }
            .minOrNull()

    private val registryRoot: JsonObject by lazy {
        json.parseToJsonElement(specDir.resolve("diagnostics.json").readText()).jsonObject
    }

    private val versionsRoot: JsonObject by lazy {
        json.parseToJsonElement(specDir.resolve("versions.json").readText()).jsonObject
    }

    /** Majors this schema revision supports. An allow-list, not a deny-list of known-bad majors. */
    val supportedMajors: List<Int> by lazy {
        versionsRoot.getValue("supportedMajors").jsonArray.map { it.jsonPrimitive.int }
    }

    /** The version decision table — `spec/versions.json`. */
    val versionDecisions: List<VersionDecision> by lazy {
        versionsRoot.getValue("decisions").jsonArray.map { entry ->
            val obj = entry.jsonObject
            VersionDecision(
                form = obj.getValue("form").jsonPrimitive.content,
                declared = obj["version"],
                wellFormed = obj.getValue("wellFormed").jsonPrimitive.content.toBooleanStrict(),
                decision = obj.getValue("decision").jsonPrimitive.content,
                code = obj["code"]?.jsonPrimitive?.content,
            )
        }
    }

    /**
     * Whether the published schema accepts [decision]'s `schemaVersion` in a document that is
     * otherwise minimal and valid.
     *
     * Deliberately drives the **real validator** rather than reconstructing the grammar as a
     * [Regex]. An earlier version of this did the latter and matched with `Regex.matches`, a
     * full-region match — but a JSON Schema `pattern` is applied as a *find*, so the test asserted a
     * stricter grammar than the schema enforces and could not see a missing `^`. Verified: deleting
     * the leading anchor left that assertion green.
     *
     * Going through the validator also covers the `number` and `absent` forms, which a grammar
     * cannot speak to at all — those fail on `type` and `required` respectively.
     */
    fun schemaAcceptsVersion(decision: VersionDecision): Boolean {
        val document = buildJsonObject {
            decision.declared?.let { put("schemaVersion", it) }
            put(
                "site",
                buildJsonObject {
                    put("id", JsonPrimitive("probe"))
                    put("name", JsonPrimitive("Probe"))
                },
            )
        }
        return schema.validate(document) {}
    }

    /** The layer names the registry defines, independent of the order they run in. */
    val declaredLayers: Set<String> by lazy {
        registryRoot.getValue("layers").jsonObject.keys
    }

    /** Every registered diagnostic, keyed by code. */
    val registry: Map<String, RegisteredDiagnostic> by lazy {
        registryRoot.getValue("diagnostics").jsonArray.associate { entry ->
            val obj = entry.jsonObject
            val code = obj.getValue("code").jsonPrimitive.content
            code to RegisteredDiagnostic(
                code = code,
                layer = obj.getValue("layer").jsonPrimitive.content,
                disposition = obj.getValue("disposition").jsonPrimitive.content,
            )
        }
    }

    /** Every fixture in the corpus, valid and invalid alike. */
    val fixtures: List<Fixture> by lazy {
        listOf("valid", "invalid").flatMap { bucket ->
            val dir = fixturesDir.resolve(bucket)
            if (!dir.isDirectory) return@flatMap emptyList()
            dir.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".json") && !it.name.endsWith(EXPECTED_SUFFIX) }
                .sortedBy { it.name }
                .map { body -> readFixture(bucket, body) }
        }
    }

    /** Fixture bodies with no expectation sibling, and expectations with no body. */
    fun unpairedFiles(): List<String> {
        val orphans = mutableListOf<String>()
        listOf("valid", "invalid").forEach { bucket ->
            val dir = fixturesDir.resolve(bucket)
            if (!dir.isDirectory) return@forEach
            val files = dir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".json") }
            val bodies = files.filterNot { it.name.endsWith(EXPECTED_SUFFIX) }.map { it.name }.toSet()
            val expectations = files.filter { it.name.endsWith(EXPECTED_SUFFIX) }
                .map { it.name.removeSuffix(EXPECTED_SUFFIX) + ".json" }.toSet()

            (bodies - expectations).forEach { orphans += "$bucket/$it has no ${EXPECTED_SUFFIX} sibling" }
            (expectations - bodies).forEach { orphans += "$bucket/$it expectation has no fixture body" }
        }
        return orphans.sorted()
    }

    private fun readFixture(bucket: String, body: File): Fixture {
        val name = body.name.removeSuffix(".json")
        val expectedFile = body.parentFile.resolve("$name$EXPECTED_SUFFIX")
        val expected = json.parseToJsonElement(expectedFile.readText()).jsonObject

        return Fixture(
            name = "$bucket/$name",
            bucket = bucket,
            bodyFile = body,
            origin = expected["origin"]?.jsonPrimitive?.content,
            bodyParses = expected["parses"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
            // Defaults to "structurally valid unless it expects a schema-layer diagnostic", which
            // is what every fixture written before SPEC-002 assumed — so none of them needed
            // editing. Declared explicitly only by a fixture that is deliberately malformed
            // *without* SS-E-SCHEMA-INVALID, which can only happen when an earlier layer rejects
            // it first. Like `parses`, the declaration is asserted against the real schema rather
            // than trusted; see SpecCorpusTest.securityLayerFixturesPassTheSchema.
            declaredSchemaValid = expected["schemaValid"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
            hasResult = expected.containsKey("result"),
            resultObject = expected["result"]?.jsonObject,
            diagnostics = expected["diagnostics"]?.jsonArray.orEmpty().map { entry ->
                val obj = entry.jsonObject
                ExpectedDiagnostic(
                    code = obj.getValue("code").jsonPrimitive.content,
                    disposition = obj.getValue("disposition").jsonPrimitive.content,
                    pointer = obj["pointer"]?.jsonPrimitive?.content,
                )
            },
        )
    }

    private const val EXPECTED_SUFFIX = ".expected.json"
}

/**
 * One row of `spec/versions.json`: a spelling of `schemaVersion` and the decision it MUST produce.
 *
 * [declared] is the raw JSON element so the table can carry the two non-string cases — an unquoted
 * number, and the field being absent entirely — which a `String` field could not distinguish from
 * the string `"null"`.
 */
internal data class VersionDecision(
    val form: String,
    val declared: JsonElement?,
    val wellFormed: Boolean,
    val decision: String,
    val code: String?,
) {
    /** The value as it appears in a manifest, or `null` when [form] is `absent`. */
    val stringValue: String? get() = if (form == "string") declared?.jsonPrimitive?.content else null

    val label: String
        get() = when (form) {
            "absent" -> "<absent>"
            "number" -> "<number ${declared?.jsonPrimitive?.content}>"
            else -> "\"${stringValue?.replace("\n", "\\n")}\""
        }
}

internal data class RegisteredDiagnostic(
    val code: String,
    val layer: String,
    val disposition: String,
)

internal data class ExpectedDiagnostic(
    val code: String,
    val disposition: String,
    val pointer: String?,
)

internal data class Fixture(
    val name: String,
    val bucket: String,
    val bodyFile: File,
    val origin: String?,
    val bodyParses: Boolean,
    val declaredSchemaValid: Boolean?,
    val hasResult: Boolean,
    val resultObject: JsonObject?,
    val diagnostics: List<ExpectedDiagnostic>,
) {
    val isValidBucket: Boolean get() = bucket == "valid"

    /**
     * Whether this document is expected to satisfy `siteskin-1.0.schema.json`.
     *
     * A property of the *document*, deliberately kept separate from whether a browser would ever
     * consult the schema — that is the layer order's business. Keeping the two apart is what lets
     * `oversized` and `version-major-2` go on proving they are structurally valid (which their
     * expectation notes claim in prose) even though both are rejected before the schema runs.
     */
    fun schemaValid(): Boolean =
        declaredSchemaValid ?: !SpecCorpus.expectsSchemaFailure(this)

    /** The canonical result this fixture pins. Fails loudly rather than returning empty. */
    fun result(): JsonObject = requireNotNull(resultObject) { "$name declares no `result`" }

    fun resultArray(field: String): List<JsonElement> =
        result()[field]?.jsonArray ?: error("$name has no `result.$field`")

    /** True when any expected diagnostic discards the whole manifest. */
    val isRejected: Boolean get() = diagnostics.any { it.disposition == "reject" }
}
