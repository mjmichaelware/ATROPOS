/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric

interface GovernanceDetector {
    val id: String
    val severity: ObservationSeverity
    fun detect(context: GovernanceDetectorContext): RuntimeObservation?
}

data class GovernanceDetectorContext(
    val runtimeId: String = "runtime-1",
    val projectId: String = "atropos",
    val goalId: String? = null,
    val nodeId: String? = null,
    val authorityFingerprint: String = "auth-default",
    val environmentFingerprint: String = "env-default",
    val exitCode: Int? = null,
    val output: String = "",
    val artifactHashes: List<String> = emptyList(),
    val metrics: List<AtroposMetric> = emptyList(),
    val changes: List<String> = emptyList(),
    val territory: List<String> = emptyList(),
    val stateVocabularyCollapsed: Boolean = false,
    val failures: Int = 0,
    /** Explicit runtime evidence for invariants that cannot be inferred from identity fields. */
    val lakehouseOptional: Boolean = false,
    val webContentDataOnly: Boolean = false,
    val storagePolicyDeclared: Boolean = false,
    val growthObserved: Boolean = false,
    val remoteStorageAccounted: Boolean = false,
    val casByteDedupVerified: Boolean = false,
    val causeFalsifiable: Boolean = false,
    val humanEscalationReviewed: Boolean = false,
    val evictionRegenerable: Boolean = false,
    val archiveRestoreTested: Boolean = false,
    val deleteReferenceProven: Boolean = false,
    val deleteReclaimableVerdict: Boolean = false
)
