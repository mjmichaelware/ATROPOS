package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SelfHostEvidenceProvenanceTest {
    @Test
    fun chain_is_stable_for_same_sanitized_inputs() {
        val provenance = SelfHostEvidenceProvenance()
        val artifacts = listOf(SelfHostProvenanceArtifact("src/a.kt", "a"))

        val first = provenance.chainSha256(listOf("attestation=ok"), artifacts, "snapshot-1")
        val second = provenance.chainSha256(listOf("attestation=ok"), artifacts, "snapshot-1")

        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun chain_changes_when_evidence_or_snapshot_changes() {
        val provenance = SelfHostEvidenceProvenance()
        val artifacts = listOf(SelfHostProvenanceArtifact("src/a.kt", "a"))

        val baseline = provenance.chainSha256(listOf("attestation=ok"), artifacts, "snapshot-1")
        val evidenceChange = provenance.chainSha256(listOf("attestation=refused"), artifacts, "snapshot-1")
        val snapshotChange = provenance.chainSha256(listOf("attestation=ok"), artifacts, "snapshot-2")

        assertNotEquals(baseline, evidenceChange)
        assertNotEquals(baseline, snapshotChange)
    }
}
