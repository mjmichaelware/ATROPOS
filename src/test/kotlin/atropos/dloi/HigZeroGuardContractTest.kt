/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import atropos.core.agent.SourceEvidence
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Batch 12 — HIG=0 is the only way DLOI resolution is reached.
 *
 * The guard existed with real logic and zero callers; every site did its own
 * ad-hoc exception handling and discarded the reason. These tests pin the
 * contract the guard now enforces for all of them.
 */
class HigZeroGuardContractTest {

    private fun guardOverEmptyRepo(): HigZeroGuard =
        HigZeroGuard(DloiService(Files.createTempDirectory("atropos-hig-")))

    @Test
    fun an_unresolvable_address_is_a_typed_miss_not_an_exception() {
        val result = guardOverEmptyRepo().resolve("nosuchdoc#S9999@L1-2")

        assertTrue(result is DloiLookupResult.NoMatch, "a failed lookup must be typed, not thrown")
        assertTrue((result as DloiLookupResult.NoMatch).reason.isNotBlank(), "a miss must say why")
        assertEquals("nosuchdoc#S9999@L1-2", result.query)
    }

    @Test
    fun a_malformed_address_is_a_typed_miss_too() {
        val result = guardOverEmptyRepo().resolve("this is not an address")

        assertTrue(result is DloiLookupResult.NoMatch)
        assertTrue((result as DloiLookupResult.NoMatch).reason.isNotBlank())
    }

    @Test
    fun an_unresolvable_task_is_a_typed_miss_and_offers_no_substitute() {
        val result = guardOverEmptyRepo().resolveTask("a task with no authoritative section anywhere")

        assertTrue(result is DloiLookupResult.NoMatch, "no nearest-title fallback is permitted")
        assertTrue((result as DloiLookupResult.NoMatch).reason.isNotBlank())
    }

    @Test
    fun the_guard_never_throws_for_any_input() {
        val guard = guardOverEmptyRepo()
        listOf("", "   ", "#", "@L1-2", "doc#S1", "a".repeat(500)).forEach { input ->
            // Each returns a verdict rather than raising; that is the contract
            // every call site used to re-implement with its own try/catch.
            assertTrue(guard.resolve(input) is DloiLookupResult.NoMatch, "resolve('$input')")
            assertTrue(guard.resolveTask(input) is DloiLookupResult.NoMatch, "resolveTask('$input')")
        }
    }

    // --- the reason is no longer discarded ------------------------------

    @Test
    fun unresolved_source_evidence_carries_its_reason() {
        val evidence = SourceEvidence.Unresolved("no section matched the task title")

        assertEquals(null, evidence.provenanceOrNull, "an unresolved source stores no provenance")
        assertTrue(
            evidence.describe().contains("no section matched the task title"),
            "the report must say why, not just that it is unresolved: ${evidence.describe()}"
        )
    }

    @Test
    fun resolved_source_evidence_reports_its_provenance_and_redacts() {
        val evidence = SourceEvidence.Resolved("SD1#S0008@L1-10")

        assertEquals("SD1#S0008@L1-10", evidence.provenanceOrNull)
        assertEquals("SD1#S0008@L1-10", evidence.describe())
        assertEquals("<hidden>", evidence.describe { "<hidden>" })
    }

    @Test
    fun source_evidence_cannot_be_constructed_empty_in_either_direction() {
        assertFailsWith<IllegalArgumentException> { SourceEvidence.Resolved("") }
        assertFailsWith<IllegalArgumentException> { SourceEvidence.Unresolved("  ") }
    }
}
