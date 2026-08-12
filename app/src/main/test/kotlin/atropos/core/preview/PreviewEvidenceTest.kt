/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.preview

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewEvidenceTest {

    private val now = Instant.parse("2026-08-04T00:00:00Z")

    private fun evidence(
        outcome: PreviewOutcome = PreviewOutcome.RENDERED,
        sha: String? = "a".repeat(64),
        violations: List<String> = emptyList()
    ) = PreviewEvidence("prev-1", "req-1", outcome, now, sha, violations)

    @Test
    fun `a rendered capture with no violations supports acceptance`() {
        assertTrue(evidence().supportsAcceptance())
        assertNull(evidence().refusalReason())
    }

    @Test
    fun `a blank render is not a success`() {
        val blank = evidence(outcome = PreviewOutcome.BLANK)
        assertFalse(blank.supportsAcceptance(), "a screenshot of an empty page is still a screenshot")
        assertTrue(blank.refusalReason()!!.contains("rendered nothing"))
    }

    @Test
    fun `never run is distinguishable from failed`() {
        assertTrue(evidence(outcome = PreviewOutcome.NOT_RUN).refusalReason()!!.contains("never run"))
        assertTrue(evidence(outcome = PreviewOutcome.ERROR).refusalReason()!!.contains("errored"))
    }

    @Test
    fun `a rendered preview with no screenshot cannot support acceptance`() {
        val noCapture = evidence(sha = null)
        assertFalse(noCapture.supportsAcceptance())
        assertTrue(noCapture.refusalReason()!!.contains("no screenshot"))
    }

    @Test
    fun `accessibility violations block acceptance because F04 is release-blocking`() {
        val failing = evidence(violations = listOf("contrast 3.1:1 on primary action"))
        assertFalse(failing.supportsAcceptance())
        assertTrue(failing.refusalReason()!!.contains("contrast"))
    }

    @Test
    fun `evidence is always bound to a requirement`() {
        assertTrue(evidence().requirementId.isNotBlank())
    }

    @Test
    fun `a first capture has no baseline rather than matching`() {
        assertTrue(
            PreviewComparison.compare(null, evidence()) is ComparisonResult.NoBaseline,
            "an absent baseline must not read as unchanged"
        )
    }

    @Test
    fun `identical captures are unchanged`() {
        assertTrue(PreviewComparison.compare(evidence(), evidence()) is ComparisonResult.Unchanged)
    }

    @Test
    fun `a differing capture reports both hashes`() {
        val changed = PreviewComparison.compare(evidence(), evidence(sha = "b".repeat(64)))
        assertTrue(changed is ComparisonResult.Changed)
        assertTrue((changed as ComparisonResult.Changed).from != changed.to)
    }

    @Test
    fun `a missing image makes the comparison incomparable, not equal`() {
        assertTrue(
            PreviewComparison.compare(evidence(sha = null), evidence()) is ComparisonResult.Incomparable
        )
    }
}
