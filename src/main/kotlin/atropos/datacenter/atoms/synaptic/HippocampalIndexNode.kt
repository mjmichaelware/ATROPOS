/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.synaptic

/**
 * HippocampalIndexNode (DC-TEL-204)
 *
 * Lightweight indexing node binding an episodic execution trajectory fragment to
 * neocortical long-term atoms for fast contextual recall without traversing full edge graphs.
 */
data class HippocampalIndexNode(
    val indexId: String,
    val trajectoryId: String,
    val turnIndex: Int,
    val episodicFragmentHash: String,
    val linkedNeocorticalAtomIds: List<String>,
    val retrievalSalience: Double,
    val createdAtEpochMs: Long
)
