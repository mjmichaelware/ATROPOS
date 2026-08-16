/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvariantContractCatalogTest {
    @Test
    fun `catalog exposes all invariant contracts and fails closed`() {
        assertEquals(48, InvariantContractCatalog.contracts.size)
        val violations = InvariantContractCatalog.evaluate(InvariantEvidence(emptyMap()))
        assertEquals(48, violations.size)
        assertTrue(violations.any { it.id == "INV-024" })

        val ids = InvariantContractCatalog.contracts.map { it.id }.toSet()
        assertEquals((1..48).map { "INV-%03d".format(it) }.toSet(), ids)

        val context = GovernanceDetectorContext(
            projectId = "project",
            goalId = "goal",
            nodeId = "node",
            authorityFingerprint = "authority",
            environmentFingerprint = "environment",
            output = "bounded output",
            territory = listOf("src/main"),
            artifactHashes = listOf("artifact"),
            changes = emptyList(),
            failures = 0,
            exitCode = 0
        )
        val facts = InvariantContractCatalog.from(context).facts
        assertEquals(48, facts.size)
        assertFalse(facts.getValue("lakehouse_optional"))
        assertFalse(facts.getValue("web_data_only"))
        assertFalse(facts.getValue("storage_policy_declared"))
        assertFalse(facts.getValue("growth_visible"))
        assertFalse(facts.getValue("remote_physical_accounting"))
        assertFalse(facts.getValue("cas_byte_dedup_only"))
        assertFalse(facts.getValue("fallback_truth_parity"))
        assertFalse(facts.getValue("cause_falsifiable"))
        assertFalse(facts.getValue("human_escalation_minimal"))
        assertFalse(facts.getValue("delete_reference_proof"))
        assertFalse(facts.getValue("eviction_regenerability"))
        assertFalse(facts.getValue("archive_restore_tested"))
        assertFalse(facts.getValue("delete_reclaimable_verdict"))

        val explicit = context.copy(
            lakehouseOptional = true,
            webContentDataOnly = true,
            storagePolicyDeclared = true,
            growthObserved = true,
            remoteStorageAccounted = true,
            casByteDedupVerified = true,
            fallbackTruthParity = true,
            causeFalsifiable = true,
            humanEscalationReviewed = true,
            evictionRegenerable = true,
            archiveRestoreTested = true,
            deleteReferenceProven = true,
            deleteReclaimableVerdict = true
        )
        val explicitFacts = InvariantContractCatalog.from(explicit).facts
        assertTrue(explicitFacts.getValue("lakehouse_optional"))
        assertTrue(explicitFacts.getValue("web_data_only"))
        assertTrue(explicitFacts.getValue("storage_policy_declared"))
        assertTrue(explicitFacts.getValue("growth_visible"))
        assertTrue(explicitFacts.getValue("remote_physical_accounting"))
        assertTrue(explicitFacts.getValue("cas_byte_dedup_only"))
        assertTrue(explicitFacts.getValue("fallback_truth_parity"))
        assertTrue(explicitFacts.getValue("cause_falsifiable"))
        assertTrue(explicitFacts.getValue("human_escalation_minimal"))
        assertTrue(explicitFacts.getValue("delete_reference_proof"))
        assertTrue(explicitFacts.getValue("eviction_regenerability"))
        assertTrue(explicitFacts.getValue("archive_restore_tested"))
        assertTrue(explicitFacts.getValue("delete_reclaimable_verdict"))
    }
}
