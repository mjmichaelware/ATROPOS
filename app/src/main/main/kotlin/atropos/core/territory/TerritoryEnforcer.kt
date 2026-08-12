/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

/**
 * TerritoryEnforcer - Adapts territory assignments to bulk patch-path checks.
 *
 * Path normalization and prefix semantics remain owned by [TerritoryAssignment].
 * This adapter does not maintain a second territory policy.
 */
class TerritoryEnforcer(
    private val allowedTerritories: List<String> = emptyList()
) {
    /**
     * Check if a single relative path is within the allowed territory bounds.
     */
    fun isAllowed(relativePath: String): Boolean {
        if (allowedTerritories.isEmpty()) return false
        return allowedTerritories.any { territory ->
            TerritoryAssignment(
                ownerId = "worktree",
                ownerRole = "WORKTREE",
                allowedPrefix = territory
            ).allows(relativePath)
        }
    }

    /**
     * Finds the first path in the list that falls outside the allowed territories.
     * Returns null if all paths are allowed.
     */
    fun firstOutside(paths: List<String>): String? {
        if (paths.isEmpty()) return null
        if (allowedTerritories.isEmpty()) return paths.first()
        return paths.firstOrNull { !isAllowed(it) }
    }
}
