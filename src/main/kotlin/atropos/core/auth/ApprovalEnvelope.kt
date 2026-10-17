/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import java.util.Set

/**
 * SD7-T-007: Ed25519-signed approval envelope for irreversible L1 actions.
 * Mathematical Invariant: Cap(P_theta) ∩ Cap(A_write) = ∅
 * Verify signature before any governance mutation. Unsigned approvals rejected.
 */
data class ApprovalEnvelope(
    val proposalId: String,
    val timestamp: Long,
    val nonce: Long,
    val ed25519Signature: String,
    val explicitOverrides: kotlin.collections.Set<String>
)
