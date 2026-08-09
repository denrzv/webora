package dev.siteskin.lint

import dev.siteskin.core.SiteSkinValidator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpusCliTest {
    @Test fun `command result and codes match every conformance fixture`() {
        fixtures().forEach { fixture ->
            val output = ByteArrayOutputStream()
            val error = ByteArrayOutputStream()
            val command = Command(ManifestLoader {
                fixture.body.inputStream().use { input ->
                    ManifestLoadResult.Validated(SiteSkinValidator.validate(input, fixture.origin))
                }
            })

            val status = command.run(arrayOf(fixture.origin), PrintStream(output), PrintStream(error))
            val actualCodes = output.toString().lineSequence()
                .filter(String::isNotBlank)
                .map { it.substringBefore(' ') }
                .toList()

            assertEquals(fixture.name, if (fixture.activates) 0 else 1, status)
            assertEquals(fixture.name, fixture.codes, actualCodes)
            assertTrue(fixture.name, error.toString().isEmpty())
        }
    }

    @Test fun `Bloom Flowers is an explicit passing distribution fixture`() {
        val fixture = fixtures().single { it.name == "valid/bloom-flowers" }

        val outcome = fixture.body.inputStream().use { SiteSkinValidator.validate(it, fixture.origin) }

        val result = Command(ManifestLoader { ManifestLoadResult.Validated(outcome) })
            .run(arrayOf(fixture.origin), PrintStream(ByteArrayOutputStream()), System.err)
        assertEquals(0, result)
    }

    private fun fixtures(): List<CliFixture> {
        val root = File(requireNotNull(System.getProperty("siteskin.spec.dir"))).resolve("fixtures")
        return listOf("valid", "invalid").flatMap { bucket ->
            root.resolve(bucket).listFiles().orEmpty()
                .filter { it.name.endsWith(".json") && !it.name.endsWith(".expected.json") }
                .sortedBy(File::getName)
                .map { body -> readFixture(bucket, body) }
        }.also { assertTrue("fixture corpus must not be empty", it.isNotEmpty()) }
    }

    private fun readFixture(bucket: String, body: File): CliFixture {
        val stem = body.name.removeSuffix(".json")
        val expected = json.parseToJsonElement(body.parentFile.resolve("$stem.expected.json").readText()).jsonObject
        return CliFixture(
            name = "$bucket/$stem",
            body = body,
            origin = expected.getValue("origin").jsonPrimitive.content,
            activates = expected.containsKey("result"),
            codes = expected["diagnostics"]?.jsonArray.orEmpty().map { entry ->
                entry.jsonObject.getValue("code").jsonPrimitive.content
            },
        )
    }

    private data class CliFixture(
        val name: String,
        val body: File,
        val origin: String,
        val activates: Boolean,
        val codes: List<String>,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = false }
    }
}
