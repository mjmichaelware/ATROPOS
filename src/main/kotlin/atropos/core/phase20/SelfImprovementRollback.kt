/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * P20-20.19 and 20.20: Enforces rollback capability for any deployed amendment that violates runtime invariants post-deployment.
 */
class SelfImprovementRollback(
    private val activeAmendments: MutableList<String>
) {
    fun deployAmendment(amendmentId: String) {
        if (!activeAmendments.contains(amendmentId)) {
            activeAmendments.add(amendmentId)
        }
    }

    fun triggerRollback(amendmentId: String, reason: String): Boolean {
        require(reason.isNotBlank()) { "Rollback requires a justification reason" }
        return activeAmendments.remove(amendmentId)
    }

    fun getActiveAmendments(): List<String> = activeAmendments.toList()
}
