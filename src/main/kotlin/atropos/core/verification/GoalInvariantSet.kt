/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

data class InvariantClause(
    val clauseId: String,
    val textHash: String,
    val prohibition: Boolean,
    val targetPathPattern: String? = null
)

class GoalInvariantSet(
    val rootAuthorityHash: String,
    val clauses: List<InvariantClause>
) {
    fun validateMutation(path: String, isProhibitedAction: Boolean): Boolean {
        for (clause in clauses) {
            if (clause.prohibition && isProhibitedAction) {
                clause.targetPathPattern?.let { pattern ->
                    if (path.contains(pattern)) {
                        return false // violation of prohibition
                    }
                }
            }
        }
        return true
    }
}
