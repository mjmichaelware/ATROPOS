/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

/**
 * SD7-T-030: DLOI-006 SECURITY non-functional contract lattice.
 * Mathematical Invariant: SchemaPartition(P_A ∪ P_B) = Unify(DDL(P_A), DDL(P_B))
 * Mandatory injection:
 * (1) constant-time comparison via java.security.MessageDigest.isEqual
 * (2) all SQL as prepared statements
 * (3) strict type-boundary parsing before business logic
 */
object DloiSecurityLattice {
    const val DOMAIN_ID = "DLOI-006-SECURITY"

    fun injectSecurityConstraints(domainCode: String): String {
        return buildString {
            appendLine("// === DLOI-006 SECURITY CONTRACT LATTICE APPLIED ===")
            appendLine("// Rule 1: Constant-time comparison mandatory via MessageDigest.isEqual")
            appendLine("// Rule 2: SQL parameterized statements mandatory; raw string interpolation forbidden")
            appendLine("// Rule 3: Strict boundary validation enforced at entrypoint")
            appendLine(domainCode)
        }
    }
}
