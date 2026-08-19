package app.webora.browser.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The writer, alone.
 *
 * Split from `InspectorJsonTest` on purpose: this file knows nothing about inspectors, snapshots or
 * websites, and its only failure mode is an escaping or layout bug. One file covering both halves
 * would let a test for disclosure look like a test for escaping, and the reverse.
 */
class JsonDocumentTest {

    @Test fun `a quote and a backslash are escaped`() {
        assertEquals("\"say \\\"hi\\\"\\\\now\"", JsonString("say \"hi\"\\now").render())
    }

    @Test fun `the short escapes are used where JSON defines them`() {
        assertEquals("\"a\\nb\\tc\\rd\\be\\ff\"", JsonString("a\nb\tc\rd\u0008e\u000Cf").render())
    }

    @Test fun `a control character with no short form becomes a six-character escape`() {
        // JSON forbids a raw character below U+0020 inside a string, so a writer that emitted one
        // would produce a document that parses nowhere. The characters most likely to arrive here
        // are exactly the ones untrustedText strips, which makes this the second line and not the
        // first — and a second line that is missing is indistinguishable from one that is present
        // until the day the first line changes.
        assertEquals("\"a\\u0001b\"", JsonString("a\u0001b").render())
        assertEquals("\"\\u001F\"", JsonString("\u001F").render())
    }

    @Test fun `the character at the boundary is not escaped`() {
        // U+0020 is the first character JSON permits raw. Escaping it too would be harmless and
        // wrong, and the off-by-one is invisible in any document that has no control characters.
        assertEquals("\"a b\"", JsonString("a b").render())
    }

    @Test fun `non-ASCII passes through literally`() {
        // The clipboard is UTF-8 and the destination is a text editor or a prompt. Escaping every
        // non-ASCII character would produce a valid document nobody can read.
        assertEquals("\"Bluten hana e\"", JsonString("Bluten hana e").render())
        assertEquals("\"\u00E9\u82B1\"", JsonString("\u00E9\u82B1").render())
    }

    @Test fun `scalars render as JSON types rather than as strings`() {
        assertEquals("null", JsonNull.render())
        assertEquals("true", JsonBool(true).render())
        assertEquals("false", JsonBool(false).render())
        assertEquals("512", JsonNumber(512).render())
        assertEquals("-1", JsonNumber(-1).render())
        assertTrue(JsonNumber(0).render().none { it == '"' })
    }

    @Test fun `empty containers render empty rather than as a blank line`() {
        assertEquals("[]", JsonArray(emptyList()).render())
        assertEquals("{}", JsonObject(emptyList()).render())
    }

    @Test fun `an object renders its fields in declaration order`() {
        // Ordered pairs rather than a Map is what makes determinism a property of the type. A hash
        // map would put the order at the mercy of the key strings, so two captures of one origin
        // would diff against each other for no reason at all.
        val document = JsonObject(
            listOf(
                "zebra" to JsonNumber(1),
                "apple" to JsonNumber(2),
                "mango" to JsonNumber(3),
            ),
        ).render()

        assertEquals("{\n  \"zebra\": 1,\n  \"apple\": 2,\n  \"mango\": 3\n}", document)
    }

    @Test fun `nesting indents two spaces per level`() {
        val document = JsonObject(
            listOf(
                "outer" to JsonObject(
                    listOf(
                        "inner" to JsonArray(
                            listOf(JsonNumber(1), JsonObject(listOf("deep" to JsonBool(true)))),
                        ),
                    ),
                ),
            ),
        ).render()

        assertEquals(
            listOf(
                "{",
                "  \"outer\": {",
                "    \"inner\": [",
                "      1,",
                "      {",
                "        \"deep\": true",
                "      }",
                "    ]",
                "  }",
                "}",
            ).joinToString("\n"),
            document,
        )
    }

    @Test fun `a key is escaped like any other string`() {
        // No website value becomes a key, and the writer is not where that rule is enforced. It must
        // still be true that a key carrying a quote cannot break the document, because a writer that
        // is safe only while its caller is careful has moved the guarantee somewhere unassertable.
        assertEquals("{\n  \"a\\\"b\": 1\n}", JsonObject(listOf("a\"b" to JsonNumber(1))).render())
    }

    @Test fun `rendering is a pure function of the tree`() {
        val tree = JsonObject(listOf("a" to JsonArray(listOf(JsonString("x"), JsonNull))))

        assertEquals(tree.render(), tree.render())
    }

    @Test fun `the document carries no trailing newline`() {
        // The clipboard is meant to hold the JSON document itself. A trailing newline is invisible
        // where it is pasted and visible in a diff of two captures, which is the one place these
        // documents are meant to be compared.
        assertTrue(JsonObject(listOf("a" to JsonNumber(1))).render().endsWith("}"))
    }
}
