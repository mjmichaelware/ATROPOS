/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.io.File

data class PathGrant(
    val taskId: String,
    val allowedPaths: List<String>,
    val readOnly: Boolean = false
)

object TerritoryGrant {
    private val activeGrants = mutableMapOf<String, PathGrant>()

    fun recordGrant(taskId: String, allowedPaths: List<String>, readOnly: Boolean = false) {
        activeGrants[taskId] = PathGrant(taskId, allowedPaths, readOnly)
    }

    fun getGrant(taskId: String): PathGrant? = activeGrants[taskId]

    fun detectDrift(taskId: String, modifiedFiles: List<String>): List<String> {
        val grant = activeGrants[taskId] ?: return modifiedFiles // If no grant, all modifications are considered drift
        val outOfTerritory = mutableListOf<String>()
        for (file in modifiedFiles) {
            val normalizedFile = File(file).canonicalPath
            val isAllowed = grant.allowedPaths.any { allowed ->
                val normalizedAllowed = File(allowed).canonicalPath
                normalizedFile.startsWith(normalizedAllowed)
            }
            if (!isAllowed || grant.readOnly) {
                outOfTerritory.add(file)
            }
        }
        return outOfTerritory
    }

    fun clearGrants() {
        activeGrants.clear()
    }
}
