/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory


/**
 * SD7-T-001: Sealed partition synthesis result hierarchy.
 * Mathematical Invariant: Cap(P_theta) ∩ Cap(A_write) = ∅
 * Distinguishes partition success from partition-local failure without aborting the global build.
 */
sealed class PartitionSynthesisResult {
    data class Success(val partitionId: String, val files: Set<String>) : PartitionSynthesisResult()
    data class Failure(val partitionId: String, val reason: String) : PartitionSynthesisResult()
}
