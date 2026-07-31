/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

/**
 * TerritoryEnforcer - Enforces territory bounds on file mutations and path checks.
 *
 * Implements the Phase 13 territory enforcement capability. Blocks any out-of-territory
 * modification before it reaches the file system or worktree git operations.
 */
class TerritoryEnforcer(
    private val allowedTerritories: List<String> = emptyList()
) {
    /**
     * Check if a single relative path is within the allowed territory bounds.
     */
    fun isAllowed(relativePath: String): Boolean {
        if (allowedTerritories.isEmpty()) return false
        val normalized = relativePath.replace('\\', '/').trim().trimStart('/')
        if (normalized.isEmpty() || normalized.split("/").any { it == ".." }) return false

        return allowedTerritories.any { territory ->
            val normT = territory.replace('\\', '/').trim().trim('/')
            normalized == normT || normalized.startsWith("$normT/")
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
