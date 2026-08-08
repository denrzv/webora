package dev.siteskin.core.manifest

import dev.siteskin.core.SiteSkinLimits
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestParserTest {
    @Test
    fun oversizedInputStopsAtFirstByteBeyondLimit() {
        val input = GeneratingInputStream(SiteSkinLimits.MAX_MANIFEST_BYTES + 50_000)

        val result = ManifestParser.parse(input)

        assertRejectedWith(result, ManifestDiagnosticCode.SIZE_EXCEEDED)
        assertEquals(SiteSkinLimits.MAX_MANIFEST_BYTES + 1, input.bytesRead)
    }

    @Test
    fun malformedJsonIsRejected() {
        val result = ManifestParser.parse("{not-json".byteInputStream())

        assertRejectedWith(result, ManifestDiagnosticCode.PARSE)
    }

    @Test
    fun invalidUtf8IsRejected() {
        val result = ManifestParser.parse(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))

        assertRejectedWith(result, ManifestDiagnosticCode.PARSE)
    }

    @Test
    fun emptyAndWrongShapedInputAreRejectedWithoutThrowing() {
        assertRejectedWith(ManifestParser.parse(byteArrayOf().inputStream()), ManifestDiagnosticCode.PARSE)
        assertRejectedWith(ManifestParser.parse("[]".byteInputStream()), ManifestDiagnosticCode.PARSE)
    }

    @Test
    fun parserDoesNotCloseCallerStream() {
        val input = CloseTrackingInputStream("{}".encodeToByteArray())

        val result = ManifestParser.parse(input)

        assertTrue(result is ManifestParseResult.Parsed)
        assertFalse(input.closed)
    }



    @Test
    fun streamReadFailureIsRejectedWithoutThrowing() {
        val input = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("connection reset")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = read()
        }

        val result = ManifestParser.parse(input)

        assertRejectedWith(result, ManifestDiagnosticCode.PARSE)
    }

    @Test
    fun allValidParsingFixturesParse() {
        val specDir = java.io.File(requireNotNull(System.getProperty("siteskin.spec.dir")))
        val fixtures = specDir.resolve("fixtures/valid").listFiles { file ->
            file.extension == "json"
        }.orEmpty()

        assertTrue("valid fixture corpus must not be empty", fixtures.isNotEmpty())
        fixtures.forEach { fixture ->
            val result = ManifestParser.parse(fixture.inputStream())
            assertTrue("${fixture.name} did not parse", result is ManifestParseResult.Parsed)
        }
    }

    @Test
    fun unknownFieldsAreIgnoredWithPaths() {
        val result = ManifestParser.parse(
            """{"schemaVersion":"1.1","future":true,"site":{"id":"x","name":"X","tagline":"Hi"}}"""
                .byteInputStream(),
        ) as ManifestParseResult.Parsed

        assertEquals("1.1", result.manifest.schemaVersion)
        assertEquals("X", result.manifest.site?.name)
        assertEquals(listOf("$.future", "$.site.tagline"), result.warnings.map { it.path })
        assertTrue(result.warnings.all { it.code == ManifestDiagnosticCode.FIELD_UNKNOWN })
    }

    @Test
    fun unknownFieldsInsideItemsAreReportedPerOccurrence() {
        val result = ManifestParser.parse(
            """{"bottomNavigation":[{"id":"a","label":"A","badge":1,"action":{"type":"home","target":"x"}},{"id":"b","label":"B","badge":2,"action":{"type":"refresh"}}]}"""
                .byteInputStream(),
        ) as ManifestParseResult.Parsed

        assertEquals(
            listOf(
                "$.bottomNavigation[0].badge",
                "$.bottomNavigation[0].action.target",
                "$.bottomNavigation[1].badge",
            ),
            result.warnings.map { it.path },
        )
        assertEquals(listOf("a", "b"), result.manifest.bottomNavigation?.map { it.id })
    }

    private fun assertRejectedWith(result: ManifestParseResult, code: ManifestDiagnosticCode) {
        assertTrue(result is ManifestParseResult.Rejected)
        assertEquals(code, (result as ManifestParseResult.Rejected).error.code)
        assertEquals(code.wireValue, result.error.wireCode)
    }

    private class GeneratingInputStream(private val size: Int) : InputStream() {
        var bytesRead: Int = 0
            private set

        override fun read(): Int = if (bytesRead++ < size) ' '.code else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRead >= size) return -1
            val count = minOf(length, size - bytesRead)
            buffer.fill(' '.code.toByte(), offset, offset + count)
            bytesRead += count
            return count
        }
    }

    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed: Boolean = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }
}
