package dev.siteskin.core.spec

import io.github.optimumcode.json.schema.JsonSchema
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Corpus integrity. These tests do not exercise any validation logic — there is none yet — but they
 * are what stops `spec/fixtures/` decaying into decoration between now and `CORE-004`.
 *
 * The load-bearing one is [everyRegisteredCodeHasAFixture]: it turns "a rule with no fixture does
 * not exist" from a slogan in CLAUDE.md into a failing build. Its counterpart
 * [everyFixtureCodeIsRegistered] closes the other direction, so a fixture cannot invent a code.
 *
 * Behavioural coverage of the security layer arrives with `CORE-003` (schema validation) and
 * `CORE-004` (security validation and normalization), which extend this class rather than replace
 * it. See `docs/tasklist/SPEC-001.md` § Deferred.
 */
class SpecCorpusTest {

    private val json = Json

    @Test
    fun corpusIsDiscovered() {
        // Every other test in this class is a "for all fixtures" assertion, and every one of them
        // passes vacuously against an empty list. If siteskin.spec.dir ever points somewhere wrong
        // — a moved directory, a broken Gradle wiring — the suite would go green while asserting
        // nothing at all. This is the test that makes the others mean something.
        assertTrue("spec/ not found at ${SpecCorpus.specDir}", SpecCorpus.specDir.isDirectory)
        assertTrue("spec/fixtures/ not found at ${SpecCorpus.fixturesDir}", SpecCorpus.fixturesDir.isDirectory)
        assertTrue("Diagnostic registry is empty", SpecCorpus.registry.isNotEmpty())
        assertTrue("No fixtures discovered under ${SpecCorpus.fixturesDir}", SpecCorpus.fixtures.isNotEmpty())
    }

    @Test
    fun fixtureBodiesAndExpectationsArePaired() {
        val orphans = SpecCorpus.unpairedFiles()
        assertTrue(
            "Every fixture body needs an .expected.json sibling and vice versa:\n  " +
                orphans.joinToString("\n  "),
            orphans.isEmpty(),
        )
    }

    @Test
    fun everyExpectationDeclaresAnOrigin() {
        // Almost every security rule in the spec is relative to the serving origin, and a manifest
        // does not carry its own. A fixture without one cannot express origin binding at all, so
        // there is deliberately no default to fall back to.
        val missing = SpecCorpus.fixtures.filter { it.origin.isNullOrBlank() }.map { it.name }
        assertTrue("Fixtures missing an `origin`: $missing", missing.isEmpty())

        val malformed = SpecCorpus.fixtures
            .filterNot { it.origin.orEmpty().matches(ORIGIN_FORM) }
            .map { "${it.name} -> ${it.origin}" }
        assertTrue("Origins must be scheme://host[:port] with no path: $malformed", malformed.isEmpty())
    }

    @Test
    fun everyFixtureCodeIsRegistered() {
        val unregistered = SpecCorpus.fixtures
            .flatMap { fixture -> fixture.diagnostics.map { fixture.name to it.code } }
            .filterNot { (_, code) -> code in SpecCorpus.registry }
            .map { (fixture, code) -> "$fixture uses unregistered $code" }
        assertTrue(unregistered.joinToString("\n"), unregistered.isEmpty())
    }

    @Test
    fun everyRegisteredCodeHasAFixture() {
        val used = SpecCorpus.fixtures.flatMap { it.diagnostics }.map { it.code }.toSet()
        val unfixtured = (SpecCorpus.registry.keys - used).sorted()
        assertTrue(
            "A rule with no fixture does not exist. Unfixtured codes: $unfixtured\n" +
                "Add the fixture in the same commit as the registry entry — see " +
                "docs/tasklist/SPEC-001.md § Sequencing invariant.",
            unfixtured.isEmpty(),
        )
    }

    @Test
    fun fixtureDispositionsMatchTheRegistry() {
        // The disposition is part of a diagnostic's definition, not a per-fixture choice. A fixture
        // claiming SS-E-ORIGIN-MISMATCH rejects the manifest would quietly contradict the spec.
        val mismatched = SpecCorpus.fixtures.flatMap { fixture ->
            fixture.diagnostics.mapNotNull { expected ->
                val registered = SpecCorpus.registry[expected.code] ?: return@mapNotNull null
                if (registered.disposition == expected.disposition) {
                    null
                } else {
                    "${fixture.name}: ${expected.code} declares '${expected.disposition}', " +
                        "registry says '${registered.disposition}'"
                }
            }
        }
        assertTrue(mismatched.joinToString("\n"), mismatched.isEmpty())
    }

    @Test
    fun rejectFixturesCarryNoResult() {
        // Nothing renders from a rejected manifest, so an expected result would be describing a
        // state that cannot exist. Conversely a drop-item fixture must pin what survived.
        val rejectedWithResult = SpecCorpus.fixtures
            .filter { it.isRejected && it.hasResult }
            .map { it.name }
        assertTrue("Rejected fixtures must not carry a `result`: $rejectedWithResult", rejectedWithResult.isEmpty())

        val survivorsWithoutResult = SpecCorpus.fixtures
            .filter { !it.isRejected && it.diagnostics.isNotEmpty() && !it.hasResult }
            .map { it.name }
        assertTrue(
            "A fixture that only drops or warns must pin what survives in `result`: $survivorsWithoutResult",
            survivorsWithoutResult.isEmpty(),
        )
    }

    @Test
    fun validFixturesDeclareNoRejection() {
        val rejecting = SpecCorpus.fixtures.filter { it.isValidBucket && it.isRejected }.map { it.name }
        assertTrue("A fixture in valid/ cannot expect a rejection: $rejecting", rejecting.isEmpty())

        val withoutResult = SpecCorpus.fixtures.filter { it.isValidBucket && !it.hasResult }.map { it.name }
        assertTrue("Every valid/ fixture must pin its normalized result: $withoutResult", withoutResult.isEmpty())
    }

    @Test
    fun invalidFixturesDeclareAtLeastOneDiagnostic() {
        val silent = SpecCorpus.fixtures
            .filter { !it.isValidBucket && it.diagnostics.isEmpty() }
            .map { it.name }
        assertTrue("An invalid/ fixture with no expected diagnostic asserts nothing: $silent", silent.isEmpty())
    }

    @Test
    fun everyRegisteredCodeAppearsInSpec() {
        // Catches a code added to the registry and to a fixture but never written down for the
        // humans the spec is actually for. Containment rather than table parsing: the assertion
        // should survive someone reformatting the markdown.
        val spec = SpecCorpus.specDir.resolve("SPEC.md").readText()
        val undocumented = SpecCorpus.registry.keys.filterNot { spec.contains(it) }.sorted()
        assertTrue("Registered but absent from SPEC.md: $undocumented", undocumented.isEmpty())
    }

    @Test
    fun specDeclaresItselfReady() {
        val spec = SpecCorpus.specDir.resolve("SPEC.md").readText()
        assertTrue(
            "spec/SPEC.md must carry `Status: SPEC_READY` once SPEC-001 lands",
            spec.lineSequence().any { it.trim() == "Status: SPEC_READY" },
        )
    }

    @Test
    fun malformedFixturesFailToParse() {
        // `parses: false` is asserted, not taken on trust — otherwise a fixture could claim to be
        // malformed while being perfectly good JSON, and SS-E-PARSE would have no real evidence.
        SpecCorpus.fixtures.forEach { fixture ->
            val parsed = runCatching { json.parseToJsonElement(fixture.bodyFile.readText()) }.isSuccess
            assertEquals(
                "${fixture.name}: expected bodyParses=${fixture.bodyParses} but parsing " +
                    if (parsed) "succeeded" else "failed",
                fixture.bodyParses,
                parsed,
            )
        }
    }

    @Test
    fun validFixturesPassTheSchema() {
        val failures = SpecCorpus.fixtures
            .filter { it.isValidBucket }
            .mapNotNull { fixture ->
                val errors = SpecCorpus.schemaErrorsFor(fixture)
                if (errors.isEmpty()) null else "${fixture.name}:\n    " + errors.joinToString("\n    ")
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun schemaLayerFixturesFailTheSchema() {
        val notFailing = SpecCorpus.fixtures
            .filter { it.bodyParses && SpecCorpus.expectsSchemaFailure(it) }
            .filter { SpecCorpus.schemaErrorsFor(it).isEmpty() }
            .map { it.name }
        assertTrue(
            "These declare an SS-E-SCHEMA-INVALID diagnostic but the schema accepts them: $notFailing",
            notFailing.isEmpty(),
        )
    }

    @Test
    fun securityLayerFixturesPassTheSchema() {
        // The assertion that keeps the layer split honest. Origin binding and scheme allow-listing
        // are deliberately absent from the schema: it does not know the serving origin, and a
        // security control expressed in two languages drifts silently. If someone later encodes one
        // of those rules as a `pattern` or an `enum`, its fixtures start failing here and the
        // duplication is caught at the moment it is introduced rather than at CORE-004.
        val unexpectedlyFailing = SpecCorpus.fixtures
            .filter { it.bodyParses && !SpecCorpus.expectsSchemaFailure(it) }
            .mapNotNull { fixture ->
                val errors = SpecCorpus.schemaErrorsFor(fixture)
                if (errors.isEmpty()) null else "${fixture.name}:\n    " + errors.joinToString("\n    ")
            }
        assertTrue(
            "A fixture whose rule lives in the security layer must still be structurally valid.\n" +
                "If one of these is genuinely malformed, fix the fixture. If the schema grew a\n" +
                "security rule, remove it — that rule belongs in CORE-004.\n" +
                unexpectedlyFailing.joinToString("\n"),
            unexpectedlyFailing.isEmpty(),
        )
    }

    @Test
    fun schemaDoesNotEnumerateActionTypesOrIcons() {
        // ADR-007 requires an unrecognised action type to drop one item and keep the manifest. An
        // `enum` here would silently upgrade that to a whole-document rejection, and the corpus
        // would not notice because no valid fixture uses an unknown type. Asserted directly.
        val unknownType = json.parseToJsonElement(
            """
            {
              "schemaVersion": "1.0",
              "site": { "id": "probe", "name": "Probe" },
              "bottomNavigation": [
                { "id": "x", "label": "X", "icon": "not_a_real_icon_name",
                  "action": { "type": "teleport" } }
              ]
            }
            """.trimIndent(),
        )
        val errors = mutableListOf<String>()
        val accepted = SpecCorpus.schema.validate(unknownType) { errors += it.message }
        assertTrue(
            "An unknown action type or icon name must be structurally valid so the security layer " +
                "can drop the item rather than the schema rejecting the document: $errors",
            accepted,
        )
    }

    @Test
    fun jsonSchemaValidatorSupportsDraft2020_12() {
        // Pins the assumption behind the dependency choice rather than leaving it to a comment.
        // If a future bump drops 2020-12 support, this fails here instead of failing obscurely
        // inside a corpus assertion.
        val schema = JsonSchema.fromDefinition(
            """
            {
              "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "schemaVersion": { "type": "string", "pattern": "^[0-9]+\\.[0-9]+${'$'}" } },
              "required": ["schemaVersion"]
            }
            """.trimIndent(),
        )

        assertTrue(schema.validate(json.parseToJsonElement("""{"schemaVersion":"1.0"}""")) {})
        assertTrue(!schema.validate(json.parseToJsonElement("""{"schemaVersion":"one"}""")) {})
        assertTrue(!schema.validate(json.parseToJsonElement("""{}""")) {})
    }

    private companion object {
        val ORIGIN_FORM = Regex("^https?://[a-z0-9.-]+(:[0-9]+)?$")
    }
}
