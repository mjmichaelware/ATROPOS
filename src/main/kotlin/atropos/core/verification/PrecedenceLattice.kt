/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

/**
 * SD5#C01: Enforces a 10-level priority lattice check for incoming intents/commands
 * based on operator roles and token privileges.
 */
class PrecedenceLattice {
    enum class Role(val level: Int) {
        GUEST(1),
        USER(2),
        MEMBER(3),
        DEVELOPER(4),
        CONTRIBUTOR(5),
        MAINTAINER(6),
        AUDITOR(7),
        ADMIN(8),
        SYSTEM(9),
        ROOT(10);

        companion object {
            fun parse(role: String): Role {
                return try {
                    valueOf(role.uppercase().trim())
                } catch (e: Exception) {
                    GUEST
                }
            }
        }
    }

    enum class Action(val requiredLevel: Int) {
        READ(1),
        WRITE(3),
        EXECUTE(5),
        PROPOSE(6),
        APPROVE(8),
        GRANT_ROLE(10);

        companion object {
            fun parse(action: String): Action {
                return try {
                    valueOf(action.uppercase().trim())
                } catch (e: Exception) {
                    READ
                }
            }
        }
    }

    fun checkPrecedence(operatorRole: String, tokenLevel: Int, targetAction: String): Boolean {
        val role = Role.parse(operatorRole)
        val action = Action.parse(targetAction)
        
        // Both operator role level and token level must satisfy the action's requirement
        return role.level >= action.requiredLevel && tokenLevel >= action.requiredLevel
    }
}
