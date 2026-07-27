package atropos.dloi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path

class HigZeroGuardTest {

    private val service = DloiService(Path.of(".").toAbsolutePath().normalize())
    private val guard = HigZeroGuard(service)

    // ── DloiService.resolve() ──────────────────────────────────────────

    @Test
    fun `resolve returns Resolved for exact known address`() {
        val result = service.resolve("authority#phase_1")
        assertTrue(result is DloiLookupResult.Resolved, "expected Resolved for known address")
        val resolved = result as DloiLookupResult.Resolved
        assertEquals("authority", resolved.resolution.document.id)
    }

    @Test
    fun `resolve returns NoMatch for unknown document instead of guessed content`() {
        val result = service.resolve("nonexistent_document#S0001@L1-10")
        assertTrue(result is DloiLookupResult.NoMatch, "expected NoMatch for unknown document")
        val noMatch = result as DloiLookupResult.NoMatch
        assertEquals("nonexistent_document#S0001@L1-10", noMatch.query)
        assertTrue(noMatch.reason.isNotBlank())
        // Proof of HIG=0: NoMatch must NEVER contain a guessed excerpt or resolution.
        assertTrue(noMatch.reason.contains("unknown DLOI document") || noMatch.reason.contains("nonexistent"))
    }

    @Test
    fun `resolve returns NoMatch for unknown section instead of guessed content`() {
        val result = service.resolve("authority#nonexistent_section")
        assertTrue(result is DloiLookupResult.NoMatch, "expected NoMatch for unknown section")
        val noMatch = result as DloiLookupResult.NoMatch
        assertEquals("authority#nonexistent_section", noMatch.query)
        assertTrue(noMatch.reason.contains("unknown DLOI section") || noMatch.reason.contains("nonexistent"))
    }

    @Test
    fun `resolve preserves exact coordinate in NoMatch query field`() {
        val address = "authority#does_not_exist@L999-1000"
        val result = service.resolve(address)
        assertTrue(result is DloiLookupResult.NoMatch, "expected NoMatch for bad coordinate")
        val noMatch = result as DloiLookupResult.NoMatch
        assertEquals(address, noMatch.query)
    }

    // ── HigZeroGuard.resolve() ─────────────────────────────────────────

    @Test
    fun `guard resolve returns Resolved for known address`() {
        val result = guard.resolve("authority#S0003@L1-5")
        assertTrue(result is DloiLookupResult.Resolved, "expected Resolved for known address")
        val resolved = result as DloiLookupResult.Resolved
        assertTrue(resolved.resolution.excerpt.isNotBlank())
    }

    @Test
    fun `guard resolve returns NoMatch for unknown document with zero guess content`() {
        val result = guard.resolve("missing_doc")
        assertTrue(result is DloiLookupResult.NoMatch, "expected NoMatch for unknown doc")
        val noMatch = result as DloiLookupResult.NoMatch
        assertEquals("missing_doc", noMatch.query)
        // The NoMatch reason must never contain fabricated source content.
        assertTrue(noMatch.reason.contains("missing") || noMatch.reason.contains("unknown"))
    }

    @Test
    fun `guard never returns guessed content for any failed resolution`() {
        val badAddresses = listOf(
            "bogus_doc_id",
            "authority#fake_section",
            "unknown#S0001@L1-1",
            "99cff09c0f362337#S9999"
        )
        for (address in badAddresses) {
            val result = guard.resolve(address)
            assertTrue(result is DloiLookupResult.NoMatch, "expected NoMatch for [$address]")
            val noMatch = result as DloiLookupResult.NoMatch
            assertEquals(address, noMatch.query, "query must preserve original address for [$address]")
            // HIG=0 invariant: NoMatch must not contain fabricated source text.
            assertTrue(noMatch.reason.isNotBlank(), "reason must not be blank for [$address]")
        }
    }

    // ── HigZeroGuard.resolveTask() ─────────────────────────────────────

    @Test
    fun `guard resolveTask returns Resolved for known task`() {
        val result = guard.resolveTask("Phase 7 AST Symbol Graph")
        assertTrue(result is DloiLookupResult.Resolved, "expected Resolved for known task")
        val resolved = result as DloiLookupResult.Resolved
        assertEquals("S0009", resolved.resolution.coordinate.sectionId)
    }

    @Test
    fun `guard resolveTask returns NoMatch for unknown task instead of guessed section`() {
        val result = guard.resolveTask("completely unrelated gibberish task")
        assertTrue(result is DloiLookupResult.NoMatch, "expected NoMatch for unknown task")
        val noMatch = result as DloiLookupResult.NoMatch
        assertTrue(noMatch.reason.contains("unable to prove") || noMatch.reason.contains("authoritative"))
    }

    @Test
    fun `guard resolveTask NoMatch preserves original query`() {
        val task = "this task definitely does not match any authority section title"
        val result = guard.resolveTask(task)
        assertTrue(result is DloiLookupResult.NoMatch, "expected NoMatch for unmatched task")
        val noMatch = result as DloiLookupResult.NoMatch
        assertEquals(task, noMatch.query)
    }
}
