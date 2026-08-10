package app.webora.browser.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSkinTraceRecorderTest {

    @Test
    fun `the latest record for an origin replaces the previous one`() {
        val recorder = SiteSkinTraceRecorder()
        recorder.record(record(ORIGIN, generation = 1, status = 404))
        recorder.record(record(ORIGIN, generation = 2, status = 200))

        val latest = recorder.latest(ORIGIN)

        assertEquals(2L, latest?.generation)
        assertEquals(200, latest?.transport?.httpStatus)
        assertEquals(listOf(ORIGIN), recorder.origins())
    }

    @Test
    fun `an unrecorded origin has no trace rather than an empty one`() {
        val recorder = SiteSkinTraceRecorder()
        recorder.record(record(ORIGIN))

        assertNull(recorder.latest("https://other.example"))
    }

    @Test
    fun `retention is bounded and drops the least recently recorded origin`() {
        val recorder = SiteSkinTraceRecorder(maxOrigins = 3)
        listOf("a", "b", "c").forEach { recorder.record(record("https://$it.example")) }

        // Touching "a" again must make "b" the least recently recorded, not "a".
        recorder.record(record("https://a.example", generation = 9))
        recorder.record(record("https://d.example"))

        assertEquals(
            listOf("https://c.example", "https://a.example", "https://d.example"),
            recorder.origins(),
        )
        assertNull(recorder.latest("https://b.example"))
        assertEquals(9L, recorder.latest("https://a.example")?.generation)
    }

    @Test
    fun `clearing drops every trace`() {
        val recorder = SiteSkinTraceRecorder()
        recorder.record(record(ORIGIN))

        recorder.clear()

        assertNull(recorder.latest(ORIGIN))
        assertTrue(recorder.origins().isEmpty())
    }

    @Test
    fun `every write advances the version so a caller can observe it`() {
        val recorder = SiteSkinTraceRecorder()
        val initial = recorder.version

        recorder.record(record(ORIGIN))
        val afterRecord = recorder.version
        recorder.clear()

        assertTrue(afterRecord > initial)
        assertTrue(recorder.version > afterRecord)
    }

    @Test
    fun `a record exposes no manifest bytes`() {
        // The trace is retained for the process lifetime. Holding the body would turn a debugging
        // aid into a retention decision about untrusted remote content.
        val offenders = (
            ManifestTraceRecord::class.java.declaredFields +
                ManifestTransportTrace::class.java.declaredFields +
                ManifestValidationTrace::class.java.declaredFields
            ).filter { it.type.isArray }

        assertTrue("trace records must not retain arrays of manifest bytes: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the discarding sink keeps nothing`() {
        // Not a tautology: None is what production installs, and it must have no state to leak.
        SiteSkinTraceSink.None.record(record(ORIGIN))

        assertTrue(SiteSkinTraceSink.None::class.java.declaredFields.none { !it.isSynthetic })
    }

    private fun record(
        origin: String,
        generation: Long = 1,
        status: Int? = 200,
    ) = ManifestTraceRecord(
        origin = origin,
        generation = generation,
        transport = ManifestTransportTrace(
            manifestUrl = "$origin/.well-known/siteskin.json",
            outcome = TraceTransportOutcome.FETCHED,
            cacheState = TraceCacheState.MISS,
            httpStatus = status,
        ),
        validation = ManifestValidationTrace(TraceValidationResult.ACCEPTED, "1.0"),
    )

    private companion object {
        const val ORIGIN = "https://site.example"
    }
}
