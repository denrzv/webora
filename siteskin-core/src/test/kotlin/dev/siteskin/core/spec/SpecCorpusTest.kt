package dev.siteskin.core.spec

import io.github.optimumcode.json.schema.JsonSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

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
    fun layerOrderCoversEveryRegisteredLayer() {
        // Completeness in both directions, like everyRegisteredCodeHasAFixture. A layer defined but
        // left out of the order would make rejectingLayerIndex silently return null for its codes,
        // and the short-circuit invariant below would stop seeing them — a gap that looks like a
        // passing test.
        assertEquals(
            "diagnostics.json's `layers` and `layerOrder` must describe the same set",
            SpecCorpus.declaredLayers.sorted(),
            SpecCorpus.layerOrder.sorted(),
        )
        assertEquals(
            "layerOrder must not repeat a layer",
            SpecCorpus.layerOrder.size,
            SpecCorpus.layerOrder.toSet().size,
        )

        val unplaced = SpecCorpus.registry.values
            .filter { SpecCorpus.layerIndexOf(it.code) == null }
            .map { "${it.code} (layer '${it.layer}')" }
        assertTrue("Registered codes whose layer is not in layerOrder: $unplaced", unplaced.isEmpty())
    }

    @Test
    fun diagnosticsDoNotCrossARejectingLayer() {
        // Rejection short-circuits: a manifest refused at the parse layer is never origin-checked,
        // so a fixture expecting both SS-E-PARSE and SS-E-ORIGIN-MISMATCH is describing a sequence
        // that cannot happen. Nothing caught that before — the corpus checked each code against the
        // registry individually and never against the others it was listed beside.
        val crossings = SpecCorpus.fixtures.flatMap { fixture ->
            val rejectAt = SpecCorpus.rejectingLayerIndex(fixture) ?: return@flatMap emptyList()
            fixture.diagnostics.mapNotNull { diagnostic ->
                val at = SpecCorpus.layerIndexOf(diagnostic.code) ?: return@mapNotNull null
                if (at <= rejectAt) {
                    null
                } else {
                    "${fixture.name}: ${diagnostic.code} sits at layer " +
                        "'${SpecCorpus.layerOrder[at]}', after the rejection at " +
                        "'${SpecCorpus.layerOrder[rejectAt]}' — that layer never runs"
                }
            }
        }
        assertTrue(crossings.joinToString("\n"), crossings.isEmpty())
    }

    @Test
    fun parsesFlagAgreesWithTheLayerOrder() {
        // `parses: false` and "rejected at or before the parse layer" are two spellings of one
        // fact, and they were free to drift. A fixture claiming not to parse while expecting only a
        // security diagnostic would satisfy both malformedFixturesFailToParse and the registry
        // checks, and still be incoherent.
        //
        // Syntactic validity is deliberately separate from parser-policy acceptance: the depth
        // fixture is valid JSON but is rejected before tree construction. Oversized input is also
        // valid JSON and refused even earlier at transport.
        val parseLayer = SpecCorpus.layerOrder.indexOf("parse")
        val disagreements = SpecCorpus.fixtures.mapNotNull { fixture ->
            val rejectAt = SpecCorpus.rejectingLayerIndex(fixture)
            val expectsParseLayerReject = fixture.diagnostics.any { diagnostic ->
                diagnostic.disposition == "reject" && SpecCorpus.layerIndexOf(diagnostic.code) == parseLayer
            }

            when {
                !fixture.bodyParses && (rejectAt == null || rejectAt > parseLayer) ->
                    "${fixture.name} declares parses=false but expects no rejection at or before 'parse'"
                expectsParseLayerReject && fixture.bodyParses && fixture.name != "invalid/deeply-nested" ->
                    "${fixture.name} expects a parse-layer rejection but declares parses=true"
                else -> null
            }
        }
        assertTrue(disagreements.joinToString("\n"), disagreements.isEmpty())
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
    fun duplicateIdDropsTheLaterOccurrence() {
        // The direction is the whole content of the rule. An implementation collecting items into a
        // last-write-wins map also "drops duplicates", and would pass any test that merely counted
        // the survivors — so this asserts *which* one survived.
        val fixture = SpecCorpus.fixtures.single { it.name == "invalid/duplicate-nav-id" }
        val surviving = fixture.resultArray("bottomNavigation")

        assertEquals("Exactly one item survives", 1, surviving.size)
        assertEquals(
            "The FIRST occurrence must win — 'Cart', not 'Basket'",
            "Cart",
            surviving.single().jsonObject.getValue("label").jsonPrimitive.content,
        )
        assertEquals("/bottomNavigation/1", fixture.diagnostics.single().pointer)
    }

    @Test
    fun showDomainIsIgnoredNotHonoured() {
        // ADR-006's attack as a fixture. Rejecting the manifest would also be safe, so "ignored"
        // has to be asserted specifically: the field warns, does not appear in the result, and
        // costs the site nothing else it asked for.
        val fixture = SpecCorpus.fixtures.single { it.name == "invalid/showdomain-ignored" }

        assertEquals(
            "showDomain must be reported as an unknown field, not an error",
            listOf("SS-W-FIELD-UNKNOWN"),
            fixture.diagnostics.map { it.code },
        )
        assertTrue("An ignored field must not reject the manifest", !fixture.isRejected)

        val toolbar = fixture.result().getValue("toolbar").jsonObject
        assertTrue("showDomain must not survive into the canonical result", "showDomain" !in toolbar)
        assertEquals(
            "The site keeps its title — beside the domain, not instead of it",
            "Your Bank",
            toolbar.getValue("title").jsonPrimitive.content,
        )
    }

    @Test
    fun contrastCorrectionMovesTheManifestColourNotTheText() {
        // The direction of the correction is the security property. Adjusting the text colour would
        // also reach AA while destroying the signal the rule exists to protect.
        val fixture = SpecCorpus.fixtures.single { it.name == "invalid/hostile-contrast" }
        val branding = fixture.result().getValue("branding").jsonObject

        assertEquals(
            "The browser-owned text colour must be untouched",
            "#2B1B24",
            branding.getValue("textColor").jsonPrimitive.content,
        )
        assertTrue(
            "The manifest's background colour must have moved",
            branding.getValue("backgroundColor").jsonPrimitive.content != "#2E1E27",
        )
    }

    @Test
    fun emptiedCollectionsArePresentNotOmitted() {
        // "The site asked for navigation and none of it survived" is a different state from "the
        // site never asked", and only the first one still renders a SiteSkin toolbar.
        val fixture = SpecCorpus.fixtures.single { it.name == "invalid/all-navigation-dropped" }
        val result = fixture.result()

        assertTrue("bottomNavigation must be present as []", "bottomNavigation" in result)
        assertEquals(0, result.getValue("bottomNavigation").jsonArray.size)
        assertTrue("Dropping every item must not reject the manifest", !fixture.isRejected)
    }

    @Test
    fun everyDeniedSchemeHasItsOwnFixture() {
        // PRD acceptance criterion 5 names these five explicitly. Asserted individually rather than
        // by counting SS-E-SCHEME-DENIED fixtures, because five fixtures for `javascript:` would
        // satisfy a count while leaving `intent:` — the one ADR-007 singles out as the reason this
        // is an allow-list — completely untested.
        val bodies = SpecCorpus.fixtures
            .filterNot { it.isValidBucket }
            .joinToString("\n") { it.bodyFile.readText() }

        val missing = DENIED_SCHEMES.filterNot { bodies.contains("$it:") }
        assertTrue("Denied schemes with no fixture: $missing", missing.isEmpty())
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
        // Covers both ways a fixture can be structurally invalid: expecting SS-E-SCHEMA-INVALID,
        // and declaring `schemaValid: false` without it. The second case only exists because an
        // earlier layer rejects the document first — see version-major-2-alien.
        val notFailing = SpecCorpus.fixtures
            .filter { it.bodyParses && !it.schemaValid() }
            .filter { SpecCorpus.schemaErrorsFor(it).isEmpty() }
            .map { it.name }
        assertTrue(
            "These are declared structurally invalid but the schema accepts them: $notFailing",
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
        //
        // The filter is `schemaValid()`, not `!expectsSchemaFailure()`. The difference is the one
        // fixture that is deliberately malformed *without* an SS-E-SCHEMA-INVALID diagnostic,
        // because the version layer refuses it first. Keeping the question "is this document
        // structurally valid?" separate from "would a browser ever ask?" is what lets `oversized`
        // and `version-major-2` go on proving they are structurally valid — both are rejected
        // before the schema runs, and both say so in their own notes.
        val unexpectedlyFailing = SpecCorpus.fixtures
            .filter { it.bodyParses && it.schemaValid() }
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
    fun versionTableMatchesTheSchemaGrammar() {
        // Every row of spec/versions.json is put through the REAL validator, in a document that is
        // otherwise minimal and valid, and its verdict compared to the table's `wellFormed` column.
        //
        // Not a Regex rebuilt from the schema's `pattern`: that is how this test was first written,
        // and it was weaker than it looked. `Regex.matches` is a full-region match while a JSON
        // Schema `pattern` is a *find*, so it asserted a stricter grammar than the schema applies
        // and stayed green when the leading `^` was deleted — the exact drift the test exists to
        // catch. Going through the validator also lets the `number` and `absent` rows be checked
        // here rather than skipped, since `type` and `required` failures are things a validator
        // knows about and a grammar does not.
        val disagreements = SpecCorpus.versionDecisions.mapNotNull { decision ->
            val accepted = SpecCorpus.schemaAcceptsVersion(decision)
            if (accepted == decision.wellFormed) {
                null
            } else {
                "${decision.label}: table says wellFormed=${decision.wellFormed}, " +
                    "but siteskin-1.0.schema.json ${if (accepted) "accepts" else "rejects"} it"
            }
        }
        assertTrue(disagreements.joinToString("\n"), disagreements.isEmpty())
    }

    @Test
    fun versionTableSeparatesGrammarFromPolicy() {
        // SS-E-SCHEMA-INVALID and SS-E-VERSION-UNSUPPORTED must not become two labels for one
        // check. Every version-layer rejection is well-formed and fails a policy the schema cannot
        // express; every schema-layer rejection is malformed. If those sets ever overlap, the layer
        // split in SPEC.md 4.1 is fiction and this is where it shows.
        val confused = SpecCorpus.versionDecisions.mapNotNull { decision ->
            when {
                decision.code == "SS-E-VERSION-UNSUPPORTED" && !decision.wellFormed ->
                    "${decision.label} rejects for its major but is not well-formed — the version " +
                        "layer never sees a malformed string"
                decision.code == "SS-E-SCHEMA-INVALID" && decision.wellFormed ->
                    "${decision.label} rejects at the schema layer but is well-formed"
                decision.decision == "accept" && !decision.wellFormed ->
                    "${decision.label} is accepted but is not well-formed"
                else -> null
            }
        }
        assertTrue(confused.joinToString("\n"), confused.isEmpty())
    }

    @Test
    fun versionTableAcceptanceFollowsTheSupportedMajors() {
        // Acceptance is recomputed here as "well-formed AND major ∈ supportedMajors", deliberately
        // without consulting the table's own `decision` column — so the two have to agree rather
        // than this test restating one of them. It is what would catch a row hand-edited to accept
        // a major the allow-list does not contain.
        val misjudged = SpecCorpus.versionDecisions
            .filter { it.form == "string" && it.wellFormed }
            .mapNotNull { decision ->
                val major = requireNotNull(decision.stringValue).substringBefore('.').toInt()
                val shouldAccept = major in SpecCorpus.supportedMajors
                if (shouldAccept == (decision.decision == "accept")) {
                    null
                } else {
                    "${decision.label}: major $major, supported=${SpecCorpus.supportedMajors}, " +
                        "but table says '${decision.decision}'"
                }
            }
        assertTrue(misjudged.joinToString("\n"), misjudged.isEmpty())
    }

    @Test
    fun versionTableCoversTheBoundary() {
        // Named individually rather than by counting rows, for the reason
        // everyDeniedSchemeHasItsOwnFixture gives: fifteen spellings of "1.0.0" would satisfy a
        // count while leaving the trailing-newline case — the one that was actually broken —
        // untested.
        val spellings = SpecCorpus.versionDecisions.mapNotNull { it.stringValue }.toSet()
        val required = listOf(
            "1.0", "1.1", "1.999", "0.9", "2.0", "10.0",
            "1", "1.0.0", "01.0", "1.00", "v1.0", "", " 1.0", "1.0 ", "1.0\n",
        )
        val missing = required.filterNot { it in spellings }.map { "\"${it.replace("\n", "\\n")}\"" }
        assertTrue("Version spellings with no table entry: $missing", missing.isEmpty())

        val forms = SpecCorpus.versionDecisions.map { it.form }.toSet()
        assertTrue("The table must cover a non-string version", "number" in forms)
        assertTrue("The table must cover an absent version", "absent" in forms)

        assertEquals("Supported majors are an allow-list of exactly {1}", listOf(1), SpecCorpus.supportedMajors)
    }

    @Test
    fun versionTableCodesAreRegistered() {
        // Mirrors everyFixtureCodeIsRegistered. A table inventing a code would read plausibly and
        // pin nothing.
        val unregistered = SpecCorpus.versionDecisions
            .mapNotNull { it.code }
            .filterNot { it in SpecCorpus.registry }
            .distinct()
        assertTrue("Version table uses unregistered codes: $unregistered", unregistered.isEmpty())

        val rejectsWithoutCode = SpecCorpus.versionDecisions
            .filter { it.decision == "reject" && it.code == null }
            .map { it.label }
        assertTrue("A rejection must name its diagnostic: $rejectsWithoutCode", rejectsWithoutCode.isEmpty())

        val acceptsWithCode = SpecCorpus.versionDecisions
            .filter { it.decision == "accept" && it.code != null }
            .map { it.label }
        assertTrue("An accepted version must not name a diagnostic: $acceptsWithCode", acceptsWithCode.isEmpty())
    }

    @Test
    fun schemaPatternsAnchorAtEndOfInput() {
        // JSON Schema specifies ECMA-262 regexes, where an unflagged `$` matches only at end of
        // input. java.util.regex disagrees: its `$` also matches immediately before a FINAL line
        // terminator. So on a JVM validator every `^...$` pattern here silently accepted a trailing
        // newline, and "schemaVersion": "1.0\n" validated against a schema that means not to allow
        // it — two spellings of one version, in a field that keys the manifest cache.
        //
        // Asserted structurally *and* behaviourally below, because either alone is weak: the string
        // check would pass a pattern that is anchored but wrong, and the behavioural check covers
        // only the field it probes.
        val schemaText = SpecCorpus.specDir.resolve("siteskin-1.0.schema.json").readText()
        val bareDollar = Regex(""""pattern"\s*:\s*"([^"]*)"""")
            .findAll(schemaText)
            .map { it.groupValues[1] }
            .filter { it.endsWith("$") }
            .toList()
        assertTrue(
            "These patterns end with a bare `$` and therefore accept a trailing newline on a JVM " +
                "validator. Anchor with (?![\\s\\S]) instead — see SPEC.md section 4.5(b): $bareDollar",
            bareDollar.isEmpty(),
        )
    }

    @Test
    fun schemaVersionRejectsTrailingAndLeadingWhitespace() {
        // The behavioural half of the assertion above, run through the real validator rather than
        // through a regex we reason about. Every one of these was accepted before SPEC-002 except
        // the leading forms.
        fun accepts(version: String): Boolean {
            val doc = json.parseToJsonElement(
                """{"schemaVersion": ${JsonPrimitive(version)}, "site": {"id": "x", "name": "X"}}""",
            )
            return SpecCorpus.schema.validate(doc) {}
        }

        assertTrue("A plain version must still validate", accepts("1.0"))
        listOf("1.0\n", "1.0 ", "1.0\t", "\n1.0", " 1.0", "1.0\r\n", "01.0", "1.00", "1", "1.0.0", "")
            .forEach { assertTrue("schemaVersion '$it' must be rejected", !accepts(it)) }
    }

    @Test
    fun bloomFlowersFixtureMatchesThePublishedCopy() {
        // denrzv/bloom-flowers serves a byte-identical copy of this fixture at
        // /.well-known/siteskin.json, and its CI checks the same hash. Pinning it in both repos,
        // rather than fetching across them, means whichever side is edited alone breaks its own
        // build immediately — no network, no branch-name dependency, and no window in which the
        // published manifest and the fixture it is supposed to demonstrate silently disagree.
        //
        // If this fails because the fixture legitimately changed: update the constant here, copy
        // the file to bloom-flowers, and update .well-known/siteskin.json.sha256 there. All three,
        // or the guard is doing nothing.
        val bytes = SpecCorpus.fixturesDir.resolve("valid/bloom-flowers.json").readBytes()
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

        assertEquals(
            "spec/fixtures/valid/bloom-flowers.json changed without its published copy following",
            BLOOM_FLOWERS_SHA256,
            actual,
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

        /** Pinned in denrzv/bloom-flowers too, at .well-known/siteskin.json.sha256. */
        const val BLOOM_FLOWERS_SHA256 =
            "1b6a30f83f1e853e4c3d8e1bb3db3b00aabf4139e4fa5871211b757e6e857d71"

        /** PRD acceptance criterion 5. Each needs a fixture of its own. */
        val DENIED_SCHEMES = listOf("javascript", "file", "content", "intent", "data")
    }
}
