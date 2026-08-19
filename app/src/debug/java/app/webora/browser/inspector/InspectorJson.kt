package app.webora.browser.inspector

/**
 * The inspector's diagnostics as one JSON document.
 *
 * **A pure function of exactly one [InspectorSnapshot], and that signature is the security control.**
 * The panel is handed a snapshot assembled for one canonical origin by `rememberInspectorSnapshot`;
 * a copy handler that reached past its parameter for the recorder, the browser mode or the active
 * tab is how one origin's transport would join another's applied chrome in a document a developer
 * then pastes into an issue. There is no parameter through which a second source could arrive, which
 * is the same shape `hubDrawerHeight`, `rendererMountAction` and `refreshAction` already take.
 *
 * **The document narrows the model and may never widen it.** The panel bounds every
 * website-controlled value through [inspectorValue] at its render site; a serializer reading the
 * snapshot's raw fields would put *more* on the clipboard than the surface it claims to mirror —
 * a widening achieved without adding a single field. Every string here therefore goes through
 * [bounded], which is the file's **only** `JsonString` construction site: an unbounded value is not
 * something an edit can forget, it is something an edit has to add a second construction site to do,
 * and `InspectorCopyContractTest` counts them.
 *
 * **Keys mirror property names one for one**, and not because it reads nicely. It is what lets
 * `InspectorJsonTest`'s totality sweep be a reflective walk rather than a hand-written table — and
 * `NET-004` recorded why that matters: a table is correct on the day it is written, while a sweep
 * covers the field nobody remembered. A key is always a compiled literal; no website value becomes
 * one, because a site that could invent field names could forge a report a developer reads as the
 * browser's own.
 *
 * **The panel's `—` is a display convention and does not appear here.** An absent value is `null`.
 * Exporting the em-dash would fail the issue's type-preservation requirement while looking like it
 * passed, and would make a display glyph a sentinel every consumer has to know about.
 */
internal fun inspectorJson(snapshot: InspectorSnapshot): String = JsonObject(
    listOf(
        "origin" to bounded(snapshot.origin),
        "activation" to bounded(snapshot.activation.name),
        "consent" to bounded(snapshot.consent?.name),
        "siteSkinEnabled" to JsonBool(snapshot.siteSkinEnabled),
        "brandAsset" to bounded(snapshot.brandAsset.name),
        // Two sibling keys rather than one nested object, even though nesting would read better.
        // DEVX-001 keeps the live slot state and the pipeline trace deliberately apart because a
        // disagreement between them is itself worth seeing — MONOGRAM beside a DECODED stage means
        // the asset decoded and the publication guard dropped it.
        "brandAssetTrace" to (snapshot.brandAssetTrace?.let(::brandAssetTrace) ?: JsonNull),
        "record" to (snapshot.record?.let(::record) ?: JsonNull),
        "applied" to (snapshot.applied?.let(::applied) ?: JsonNull),
    ),
).render()

private fun brandAssetTrace(trace: BrandAssetTrace): JsonValue = JsonObject(
    listOf(
        "stage" to bounded(trace.stage.name),
        "rejection" to bounded(trace.rejection?.name),
        "httpStatus" to number(trace.httpStatus),
        "redirects" to number(trace.redirects),
        "width" to number(trace.width),
        "height" to number(trace.height),
        "elapsedMillis" to JsonNumber(trace.elapsedMillis),
        "attempts" to number(trace.attempts),
    ),
)

private fun record(record: ManifestTraceRecord): JsonValue = JsonObject(
    listOf(
        "origin" to bounded(record.origin),
        "generation" to JsonNumber(record.generation),
        "transport" to transport(record.transport),
        "validation" to validation(record.validation),
    ),
)

private fun transport(transport: ManifestTransportTrace): JsonValue = JsonObject(
    listOf(
        "manifestUrl" to bounded(transport.manifestUrl),
        "outcome" to bounded(transport.outcome.name),
        "cacheState" to bounded(transport.cacheState.name),
        "httpStatus" to number(transport.httpStatus),
        "redirects" to number(transport.redirects),
        "rejection" to bounded(transport.rejection?.name),
    ),
)

private fun validation(validation: ManifestValidationTrace): JsonValue = JsonObject(
    listOf(
        "result" to bounded(validation.result.name),
        "schemaVersion" to bounded(validation.schemaVersion),
        // An array of objects rather than the panel's one row per code, because a consumer diffing
        // two captures wants the code and its pointer as separate values it can compare.
        "diagnostics" to JsonArray(validation.diagnostics.map(::diagnostic)),
    ),
)

private fun diagnostic(diagnostic: TraceDiagnostic): JsonValue = JsonObject(
    listOf(
        "code" to bounded(diagnostic.code),
        // SS-W-FIELD-UNKNOWN reports the key it did not recognise, so a pointer is arbitrary website
        // text. The panel renders the code in its label slot and the pointer in its value slot; here
        // both go through the same bound, because the label/value distinction is a layout fact and
        // there is no layout.
        "pointer" to bounded(diagnostic.pointer),
    ),
)

private fun applied(applied: InspectorAppliedChrome): JsonValue = JsonObject(
    listOf(
        "siteName" to bounded(applied.siteName),
        "siteId" to bounded(applied.siteId),
        "homeUrl" to bounded(applied.homeUrl),
        "activeNavigationId" to bounded(applied.activeNavigationId),
        "counts" to JsonArray(applied.counts.map(::count)),
        "navigation" to JsonArray(applied.navigation.map(::navigationItem)),
        "hub" to hub(applied.hub),
        "theme" to theme(applied.theme),
    ),
)

private fun count(count: InspectorItemCount): JsonValue = JsonObject(
    listOf(
        "collection" to bounded(count.collection.name),
        "trusted" to number(count.trusted),
        "rendered" to number(count.rendered),
        // Computed rather than stored, and included anyway: it should never be true, and a consumer
        // reading a pasted document has no way to derive "these two layers disagree" from two
        // numbers without knowing that is the question.
        "diverged" to JsonBool(count.diverged),
    ),
)

private fun navigationItem(item: InspectorItem): JsonValue = JsonObject(
    listOf(
        "id" to bounded(item.id),
        "label" to bounded(item.label),
        "actionType" to bounded(item.actionType),
        "active" to JsonBool(item.active),
    ),
)

/**
 * What the site asked for beside what the browser composed, as two values and never an arrow.
 *
 * `requested` is `null` when the manifest declared no `presentation` object at all — "I asked for the
 * default" and "I forgot to ask" produce the same drawer and are different things to tell a site
 * owner, which is the distinction core's nullable holder exists to preserve.
 */
private fun hub(hub: InspectorHub): JsonValue = JsonObject(
    listOf(
        "requested" to bounded(hub.requested?.name),
        "effective" to bounded(hub.effective.name),
    ),
)

private fun theme(theme: InspectorTheme): JsonValue = JsonObject(
    listOf(
        "darkTheme" to JsonBool(theme.darkTheme),
        "roles" to JsonArray(theme.roles.map(::colorRole)),
    ),
)

private fun colorRole(role: InspectorColorRole): JsonValue = JsonObject(
    listOf(
        "role" to bounded(role.role.name),
        // `trusted` is the *normalized* value, not the value the manifest wrote: core's security
        // validation may already have corrected it, and SS-W-CONTRAST-CORRECTED in the diagnostics
        // above is the only account of that. Named as DEVX-001 named it, so a pasted document cannot
        // be read as "what the site asked for".
        "trusted" to bounded(role.trusted),
        "applied" to bounded(role.applied),
    ),
)

/**
 * The one place a string enters the document.
 *
 * [inspectorValue] is `untrustedText` at `SiteSkinLimits.MAX_SUBTITLE_LENGTH`, the same bound the
 * panel's rows use — it flattens whitespace, ISO control characters and Unicode `FORMAT` characters,
 * so neither a newline forging a second field nor a `RIGHT-TO-LEFT OVERRIDE` reordering everything
 * after it survives into a document someone pastes.
 *
 * Enum names go through it too. The walk is a no-op on a compiled identifier, and routing them
 * anywhere else would create the second construction site this helper exists to prevent — the rule
 * is enforceable only while it has no exceptions.
 */
private fun bounded(raw: String?): JsonValue = raw?.let { JsonString(inspectorValue(it)) } ?: JsonNull

private fun number(value: Int?): JsonValue = value?.let { JsonNumber(it.toLong()) } ?: JsonNull
