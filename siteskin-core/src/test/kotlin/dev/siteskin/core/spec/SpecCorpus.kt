package dev.siteskin.core.spec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    /** Every registered diagnostic, keyed by code. */
    val registry: Map<String, RegisteredDiagnostic> by lazy {
        val root = json.parseToJsonElement(specDir.resolve("diagnostics.json").readText()).jsonObject
        root.getValue("diagnostics").jsonArray.associate { entry ->
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
            hasResult = expected.containsKey("result"),
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
    val hasResult: Boolean,
    val diagnostics: List<ExpectedDiagnostic>,
) {
    val isValidBucket: Boolean get() = bucket == "valid"

    /** True when any expected diagnostic discards the whole manifest. */
    val isRejected: Boolean get() = diagnostics.any { it.disposition == "reject" }
}
