/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

/**
 * SD5#C01: the 10-level precedence lattice for incoming intents and commands.
 *
 * Two levels must both clear an action's requirement: the operator's role, and
 * the level carried by the token they presented. They are separate on purpose —
 * a maintainer holding a read-only token may not write, and a root token in the
 * hands of a guest does not make them root.
 *
 * ## Unknown names deny
 *
 * Both [Role.parse] and [Action.parse] used to fall back to the *lowest* entry
 * — an unrecognised role became `GUEST` and, far worse, an unrecognised action
 * became `READ`. That made the lattice fail-open: any action name the enum did
 * not know was treated as the least privileged operation in the system and
 * permitted for everyone. A typo in an action name, or a new capability added
 * without a lattice entry, was silently granted rather than refused.
 *
 * The defaults now run the other way. An unknown role gets [Role.UNKNOWN] at
 * level 0, which clears nothing; an unknown action gets [Action.UNKNOWN] at
 * level 10, which only `ROOT` with a root token clears. The lattice's job is to
 * refuse what it cannot vouch for, and a name it does not recognise is exactly
 * that.
 */
class PrecedenceLattice {

    enum class Role(val level: Int) {
        /** A name the lattice does not recognise. Clears nothing. */
        UNKNOWN(0),
        GUEST(1),
        USER(2),
        MEMBER(3),
        DEVELOPER(4),
        CONTRIBUTOR(5),
        MAINTAINER(6),
        DIRECTOR(7),
        ADMIN(8),
        SYSTEM(9),
        ROOT(10);

        companion object {
            fun parse(role: String): Role =
                entries.firstOrNull { it.name == role.uppercase().trim() } ?: UNKNOWN
        }
    }

    enum class Action(val requiredLevel: Int) {
        READ(1),
        WRITE(3),
        EXECUTE(5),
        VERIFY(6),
        PROPOSE(6),
        APPROVE(7),
        RESTART(9),
        GRANT_ROLE(10),

        /**
         * A name the lattice does not recognise.
         *
         * Requires the top of the lattice rather than the bottom. An action
         * nobody declared is not a safe action; it is an action whose cost is
         * unknown, and the only honest requirement for that is the highest one.
         */
        UNKNOWN(10);

        companion object {
            fun parse(action: String): Action =
                entries.firstOrNull { it.name == action.uppercase().trim() } ?: UNKNOWN
        }
    }

    fun checkPrecedence(operatorRole: String, tokenLevel: Int, targetAction: String): Boolean {
        val role = Role.parse(operatorRole)
        val action = Action.parse(targetAction)

        // Both must clear independently. Neither substitutes for the other.
        return role.level >= action.requiredLevel && tokenLevel >= action.requiredLevel
    }
}
