package atropos.core.phase20

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProposalGeneratorTest {
    @Test
    fun deficiency_becomes_a_complete_deterministic_proposal() {
        val deficiency = deficiency()
        val first = ProposalGenerator().generate(deficiency)
        val second = ProposalGenerator().generate(deficiency)

        assertEquals(first.id, second.id)
        assertTrue(first.isComplete())
        assertEquals(listOf("evidence-hash"), first.necessity)
    }

    @Test
    fun missing_necessity_or_metric_is_refused_before_a_proposal_exists() {
        assertFailsWith<IllegalArgumentException> {
            ProposalGenerator().generate(deficiency().copy(necessity = emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            ProposalGenerator().generate(
                deficiency().copy(metric = MetricDeclaration("", 1.0, 1.0, lowerIsBetter = false))
            )
        }
    }

    private fun deficiency() = ProposalDeficiency(
        proposedBy = "worker",
        summary = "reduce verification failures",
        necessity = listOf("evidence-hash"),
        baseline = "three failures",
        target = "one failure",
        guardrails = listOf("no authority edits"),
        territory = listOf("src/main/kotlin/atropos/core/verification"),
        risk = "bounded",
        rollback = "restore prior snapshot",
        metric = MetricDeclaration("verification failures", 3.0, 1.0, lowerIsBetter = true),
        observedAt = Instant.parse("2026-01-01T00:00:00Z")
    )
}
