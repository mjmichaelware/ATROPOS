/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

/**
 * SD7-T-006: CAS-addressable prohibition clauses that permanently record human vetoes.
 * Mathematical Invariant: Cap(P_theta) ∩ Cap(A_write) = ∅
 * Append-only. Mutable vetoes prohibited.
 */
data class ProhibitionClause(
    val vetoId: String,
    val promptHash: String,
    val forbiddenAstPattern: String,
    val targetTerritory: String,
    val authorSignature: String,
    val casHash: String
)
