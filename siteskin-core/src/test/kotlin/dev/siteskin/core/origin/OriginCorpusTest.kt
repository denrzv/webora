package dev.siteskin.core.origin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drives the published conformance corpus through [UrlResolver].
 *
 * `UrlResolverTest` asserts the rules with URLs written for the purpose. This asserts the same
 * resolver against the fixtures a *site owner* reads and a second implementer would build from —
 * so the corpus stops being documentation and starts being executable. If the two ever disagree,
 * the corpus wins: `SPEC.md` §13 makes it the contract, and the implementation is written to
 * satisfy it rather than the reverse.
 *
 * Deliberately asserts **no** `SS-*` diagnostic code. Mapping a rejection to
 * `SS-E-ORIGIN-MISMATCH` is `CORE-004`'s job, and duplicating that mapping here would mean two
 * places to change and one of them silently wrong. What this pins is narrower and is exactly what
 * `CORE-001` owns: the URL at the fixture's `pointer` is refused, and every other URL in the same
 * fixture is not.
 */
class OriginCorpusTest {

    private val json = Json

    /**
     * The origin-binding fixtures, with the JSON Pointer each one expects to fail.
     *
     * Named explicitly rather than discovered by scanning for `SS-E-ORIGIN-MISMATCH`. A scan would
     * silently shrink to nothing if the code were renamed, and this test would keep passing while
     * covering less — the failure mode the count assertion below also exists to prevent.
     */
    private val originFixtures = listOf(
        "nav-cross-origin",
        "nav-protocol-relative",
        "nav-traversal-escape",
        "nav-userinfo-authority",
        "nav-port-change",
        "home-url-cross-origin",
        "logo-subdomain",
    )

    private val specDir = File(
        requireNotNull(System.getProperty("siteskin.spec.dir")) {
            "siteskin.spec.dir is not set — see siteskin-core/build.gradle.kts"
        },
    )

    private val invalidDir = specDir.resolve("fixtures/invalid")

    @Test
    fun theOriginFixturesAreAllPresent() {
        // Without this, a renamed or moved fixture turns every assertion below into a no-op over an
        // empty list and the suite stays green while testing nothing.
        val missing = originFixtures.filterNot { invalidDir.resolve("$it.json").isFile }
        assertTrue("Origin fixtures missing from ${invalidDir.path}: $missing", missing.isEmpty())
        assertEquals("Expected exactly the fixtures this test claims to cover", 7, originFixtures.size)
    }

    @Test
    fun theUrlAtEachExpectedPointerIsRejected() {
        originFixtures.forEach { name ->
            val body = readJson("$name.json")
            val expected = readJson("$name.expected.json")
            val origin = originOf(expected)

            val pointer = expected.getValue("diagnostics").jsonArray
                .single().jsonObject.getValue("pointer").jsonPrimitive.content
            val offending = resolvePointer(body, pointer)

            val result = UrlResolver.resolveInternal(origin, offending)
            assertTrue(
                "$name: the URL at $pointer is `$offending`, which the corpus says must be " +
                    "refused for origin ${origin.canonical} — resolver returned $result",
                result is UrlResolution.Rejected,
            )
        }
    }

    @Test
    fun everyOtherUrlInThoseFixturesResolves() {
        // The other half of drop-item: a fixture proves a hostile URL is refused *and* that it costs
        // the site only that item. If the resolver rejected everything it would pass the test above
        // while destroying the integration, so this is what makes that test mean something.
        originFixtures.forEach { name ->
            val body = readJson("$name.json")
            val expected = readJson("$name.expected.json")
            val origin = originOf(expected)
            val offendingPointer = expected.getValue("diagnostics").jsonArray
                .single().jsonObject.getValue("pointer").jsonPrimitive.content

            urlPointersIn(body)
                .filterNot { (pointer, _) -> pointer == offendingPointer }
                .forEach { (pointer, url) ->
                    val result = UrlResolver.resolveInternal(origin, url)
                    assertTrue(
                        "$name: `$url` at $pointer is not the fixture's offending URL and must " +
                            "resolve inside ${origin.canonical} — resolver returned $result",
                        result is UrlResolution.Resolved,
                    )
                }
        }
    }

    @Test
    fun theBloomFlowersManifestResolvesEndToEnd() {
        // The published manifest. Every URL in it must resolve, or denrzv/bloom-flowers is serving
        // an integration the browser would partly discard.
        val body = readJson("../valid/bloom-flowers.json")
        val expected = readJson("../valid/bloom-flowers.expected.json")
        val origin = originOf(expected)

        val urls = urlPointersIn(body)
        assertTrue("No URLs found in the reference manifest", urls.isNotEmpty())

        urls.forEach { (pointer, url) ->
            assertTrue(
                "bloom-flowers: `$url` at $pointer must resolve inside ${origin.canonical}",
                UrlResolver.resolveInternal(origin, url) is UrlResolution.Resolved,
            )
        }
    }

    // --- helpers ------------------------------------------------------------------------------

    private fun readJson(relative: String): JsonObject =
        json.parseToJsonElement(invalidDir.resolve(relative).readText()).jsonObject

    private fun originOf(expected: JsonObject): SiteOrigin {
        val raw = expected.getValue("origin").jsonPrimitive.content
        return requireNotNull(SiteOrigin.parse(raw)) { "fixture origin `$raw` does not parse" }
    }

    /** Resolves an RFC 6901 JSON Pointer to the string it addresses. */
    private fun resolvePointer(root: JsonObject, pointer: String): String {
        val target = pointer.removePrefix("/").split('/').fold<String, JsonElement>(root) { node, token ->
            val key = token.replace("~1", "/").replace("~0", "~")
            when (node) {
                is JsonObject -> node.getValue(key)
                is JsonArray -> node[key.toInt()]
                else -> error("pointer $pointer runs past a scalar at $key")
            }
        }
        return (target as JsonPrimitive).content
    }

    /**
     * Every URL-bearing field in a manifest, as `pointer to value`.
     *
     * Walks the document rather than reading known paths, so a fixture that grows a URL somewhere
     * new is covered without this test being updated — the alternative silently under-tests.
     */
    private fun urlPointersIn(node: JsonElement, pointer: String = ""): List<Pair<String, String>> =
        when (node) {
            is JsonObject -> node.entries.flatMap { (key, value) ->
                val childPointer = "$pointer/$key"
                if (value is JsonPrimitive && key in URL_KEYS) {
                    listOf(childPointer to value.content)
                } else {
                    urlPointersIn(value, childPointer)
                }
            }
            is JsonArray -> node.flatMapIndexed { index, value -> urlPointersIn(value, "$pointer/$index") }
            else -> emptyList()
        }

    private companion object {
        /** `match` patterns are not URLs — `CORE-006` owns those. */
        val URL_KEYS = setOf("url", "homeUrl", "logoUrl")
    }
}
