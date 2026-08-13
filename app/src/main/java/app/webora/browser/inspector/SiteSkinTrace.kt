package app.webora.browser.inspector

import app.webora.browser.siteskin.BrandAssetRejection
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

/**
 * Which stage of the brand-asset pipeline produced the asset the top bar is showing.
 *
 * `NET-003` makes a monogram the correct output of every non-cancellation failure, and for the user
 * that is right: an unreachable logo must never break browsing. It is also why one glyph came to
 * stand for "no logo declared", "the CDN answered 404", "the bytes are not the type the header
 * claimed", "the image is larger than the cap" and "the decoder gave up" — five different things to
 * fix behind one indistinguishable symptom. `NET-004` is the ticket that paid for that, on the
 * reference integration itself.
 *
 * Browser-owned and closed, like [TraceTransportOutcome]: a website can cause a stage to be
 * *selected* and can neither supply nor phrase one.
 */
internal enum class BrandAssetStage {
    /** A bitmap was decoded and published. */
    DECODED,

    /** The manifest declared no logo, so no request was made. */
    NOT_DECLARED,

    /** The server answered and the transport refused the answer. See [BrandAssetTrace.rejection]. */
    TRANSPORT_REJECTED,

    /** No answer arrived at all — an `IOException` or a timeout. */
    TRANSPORT_UNAVAILABLE,

    /** The bytes do not carry the signature of the media type the response declared. */
    SIGNATURE_MISMATCH,

    /** Bounds-only decoding could not read a size and a format from the bytes. */
    BOUNDS_UNREADABLE,

    /** The probed format disagreed with the declared one, or the dimensions exceed the caps. */
    BOUNDS_REFUSED,

    /** Bounds were accepted and full decoding still returned nothing. */
    DECODE_FAILED,

    /** Anything the stages above do not describe, so a new failure is never silently a known one. */
    UNEXPECTED_ERROR,
}

/**
 * What one brand-asset load did.
 *
 * Observational throughout, like the manifest traces above: nothing here re-enters a decision. Every
 * field is a number or a closed enum value, so no response header, server message or URL can reach a
 * display surface through it.
 *
 * [elapsedMillis] is the field that separates a refusal from a load that simply had not finished when
 * something looked — the question `NET-004` could not answer from anything the browser recorded, and
 * the reason a hosted frame showing a monogram was ambiguous for four runs.
 */
internal data class BrandAssetTrace(
    val stage: BrandAssetStage,
    val rejection: BrandAssetRejection? = null,
    val httpStatus: Int? = null,
    val redirects: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val elapsedMillis: Long = 0,
    val attempts: Int = 1,
)
