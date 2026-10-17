/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.recovery

/**
 * SD7-T-008: Unified 13-element checkpoint vector fusing SD6 restore vectors.
 * Mathematical Invariant: Exact-hash restart continuity
 * All 13 fields mandatory. Partial vector = blocking halt.
 */
data class UnifiedRestoreVector(
    val rootHumanAuthorityHash: String,
    val activeSourceSpecHash: String,
    val goalInvariantFingerprint: String,
    val derivationGraphVersion: String,
    val activeWaveIndex: Int,
    val currentAtomId: String,
    val activeTerritory: List<String>,
    val completedPartitions: List<String>,
    val casEvidenceMapHash: String,
    val activeContextManifestHash: String,
    val remainingBudgetTokens: Int,
    val acceptedFindings: List<String>,
    val lastVerifiedGate: String
)
