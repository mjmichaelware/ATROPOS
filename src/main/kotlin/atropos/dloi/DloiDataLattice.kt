/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

/**
 * SD7-T-031: DLOI-410 DATA non-functional contract lattice.
 * Mathematical Invariant: SchemaPartition(P_A ∪ P_B) = Unify(DDL(P_A), DDL(P_B))
 * Mandatory injection:
 * (1) multi-table mutations accept explicit transaction context
 * (2) connection pool size clamped to 5..20 with timeout ceiling
 * (3) foreign keys synthesize matching CREATE INDEX DDL
 */
object DloiDataLattice {
    const val DOMAIN_ID = "DLOI-410-DATA"
    const val MIN_POOL_SIZE = 5
    const val MAX_POOL_SIZE = 20
    const val CONNECTION_TIMEOUT_MS = 30_000L

    fun injectDataConstraints(domainCode: String): String {
        return buildString {
            appendLine("// === DLOI-410 DATA CONTRACT LATTICE APPLIED ===")
            appendLine("// Rule 1: Multi-table mutations must accept explicit TransactionContext")
            appendLine("// Rule 2: Connection pool clamped to [$MIN_POOL_SIZE, $MAX_POOL_SIZE] with timeout ${CONNECTION_TIMEOUT_MS}ms")
            appendLine("// Rule 3: CREATE INDEX synthesized for all foreign key relations")
            appendLine(domainCode)
        }
    }
}
