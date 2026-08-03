/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The predicate that separates "a bundle was written" from "a bundle proves the
 * installed runtime". Every assertion here is a hole a bundle could otherwise
 * carry while still exporting cleanly.
 */
class SelfHostInstalledProofEvidenceTest {

    private val proof = SelfHostInstalledProofEvidence()

    private fun completeEvidence() = listOf(
        "candidate_jar_build ok=true proposal=p-1 candidate=ATROPOS.jar",
        "promotion_gate canComplete=true node=node-1",
        "git_status_short ok=true exit=0 output=?? src/main/kotlin/atropos/Marker.kt",
        "jar_swap promoted=true sha256=abc123"
    )

    @Test
    fun a_run_carrying_all_four_parts_is_complete() {
        val assessment = proof.assess(completeEvidence())

        assertTrue(assessment.complete, "missing: ${assessment.missing}")
        assertTrue(assessment.missing.isEmpty())
    }

    @Test
    fun an_empty_run_is_missing_every_part_rather_than_silently_passing() {
        val assessment = proof.assess(emptyList())

        assertFalse(assessment.complete)
        assertEquals(SelfHostInstalledProofPart.entries.toSet(), assessment.missing.toSet())
    }

    @Test
    fun a_failed_candidate_build_does_not_count_as_build_evidence() {
        // The line is present, so a substring check on the marker alone would pass
        // here. A recorded failure is evidence that the build did not work.
        val assessment = proof.assess(
            completeEvidence().map {
                if (it.startsWith("candidate_jar_build")) "candidate_jar_build ok=false failure=NONZERO_EXIT" else it
            }
        )

        assertFalse(assessment.complete)
        assertEquals(listOf(SelfHostInstalledProofPart.CANDIDATE_BUILD), assessment.missing)
    }

    @Test
    fun a_refused_swap_does_not_count_as_installed_evidence() {
        val assessment = proof.assess(
            completeEvidence().map {
                if (it.startsWith("jar_swap")) "jar_swap promoted=false terminal=UNCHANGED" else it
            }
        )

        assertFalse(assessment.complete)
        assertEquals(listOf(SelfHostInstalledProofPart.JAR_SWAP), assessment.missing)
    }

    @Test
    fun a_verified_mutation_without_git_status_cannot_prove_a_file_changed() {
        val assessment = proof.assess(completeEvidence().filterNot { it.startsWith("git_status_short") })

        assertFalse(assessment.complete)
        assertEquals(listOf(SelfHostInstalledProofPart.GIT_STATUS), assessment.missing)
    }

    @Test
    fun a_promotion_without_a_gate_report_is_self_approval() {
        val assessment = proof.assess(completeEvidence().filterNot { it.startsWith("promotion_gate") })

        assertFalse(assessment.complete)
        assertEquals(listOf(SelfHostInstalledProofPart.COMPLETION_GATE), assessment.missing)
    }

    @Test
    fun the_evidence_line_names_the_missing_parts_so_an_operator_knows_what_to_rerun() {
        val line = proof.evidenceLine(proof.assess(emptyList()))

        assertTrue(line.startsWith("installed_proof complete=false"), line)
        SelfHostInstalledProofPart.entries.forEach { part ->
            assertTrue(line.contains(part.name), "refusal must name $part: $line")
        }
    }

    @Test
    fun a_complete_assessment_reports_no_missing_parts_in_its_evidence_line() {
        val line = proof.evidenceLine(proof.assess(completeEvidence()))

        assertEquals("installed_proof complete=true missing=none", line)
    }
}
