/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.memory

import java.util.Map

/**
 * SD7-T-004: Bounded observation record schema replacing raw transcripts under Storage Constitution Law 06.
 * Mathematical Invariant: C_real = min(C_impl, C_int, C_ver, C_ev)
 * Immutable record. Discard raw transcripts after compaction.
 */
data class ObservationRecord(
    val observationId: String,
    val timestamp: Long,
    val waveIndex: Int,
    val atomId: String,
    val predicateOutcomes: kotlin.collections.Map<String, Boolean>,
    val tokenCost: Int,
    val contextSnapshotHash: String
)
