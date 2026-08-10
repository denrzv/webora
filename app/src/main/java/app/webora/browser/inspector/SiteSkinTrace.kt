package app.webora.browser.inspector

import app.webora.browser.siteskin.FetchRejection

/**
 * What the browser recorded while deciding whether one origin's manifest could activate.
 *
 * This is *display data derived from untrusted input*, and the distinction matters: nothing reads a
 * trace except the developer panel. It never re-enters a decision, which is what lets the browser
 * record freely without widening what it trusts. The record deliberately holds no manifest bytes —
 * it holds counts, codes, pointers and bounded strings, so retaining one costs nothing and leaks
 * nothing.
 */
internal data class ManifestTraceRecord(
    val origin: String,
    val generation: Long,
    val transport: ManifestTransportTrace,
    val validation: ManifestValidationTrace,
)

/** How the manifest bytes were — or were not — obtained. */
internal data class ManifestTransportTrace(
    val manifestUrl: String,
    val outcome: TraceTransportOutcome,
    val cacheState: TraceCacheState,
    val httpStatus: Int? = null,
    val redirects: Int = 0,
    val rejection: FetchRejection? = null,
)

/** What the shared validator concluded, including the diagnostics the runtime otherwise drops. */
internal data class ManifestValidationTrace(
    val result: TraceValidationResult,
    val schemaVersion: String? = null,
    val diagnostics: List<TraceDiagnostic> = emptyList(),
)

/** One `SS-*` code and the JSON pointer it applies to, if any. */
internal data class TraceDiagnostic(val code: String, val pointer: String?)

/**
 * The transport's answer.
 *
 * [UNAVAILABLE] and [REJECTED] are kept apart on purpose. "No answer" (an `IOException`, a timeout,
 * a cancelled connection) and "the server answered and the browser refused the answer" look
 * identical to a user — both fall back to regular browsing — and are the entire difference to a
 * site owner debugging a misconfigured CDN.
 */
internal enum class TraceTransportOutcome {
    /** The page origin was not eligible for discovery, so no request was made. */
    NOT_ELIGIBLE,

    /** A fresh cache entry answered, so no request was made. */
    CACHED,

    /** A response carried a body. */
    FETCHED,

    /** A conditional request was answered `304`. */
    NOT_MODIFIED,

    /** The server answered and the browser refused the answer. See [FetchRejection]. */
    REJECTED,

    /** No answer arrived at all. */
    UNAVAILABLE,
}

/** Which of `NET-002`'s paths served this navigation. */
internal enum class TraceCacheState {
    NOT_APPLICABLE,
    MISS,
    FRESH_HIT,
    REVALIDATED,
    REFETCHED,
    STALE_REPLAYED,
}

/** Whether validation ran, and what it concluded. */
internal enum class TraceValidationResult {
    NOT_RUN,
    ACCEPTED,
    REJECTED,
}
