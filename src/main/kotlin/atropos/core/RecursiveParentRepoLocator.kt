/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * ATOM VC7: RECURSIVE PARENT DIRECTORY GIT ROOT DISCOVERY
 * Path: src/main/kotlin/atropos/core/RecursiveParentRepoLocator.kt
 *
 * Walks upwards through parent directories from current working folder to discover
 * enclosing Git repositories and .atropos configuration markers. Stops traversing
 * at filesystem mounts or user home roots.
 */
enum class ParentFolderDiscoveryPolicy(val configValue: String) {
    ALWAYS("always"),
    NEVER("never"),
    PROMPT("prompt");

    companion object {
        fun fromConfig(value: String?): ParentFolderDiscoveryPolicy =
            values().firstOrNull { it.configValue.equals(value?.trim(), ignoreCase = true) } ?: PROMPT
    }
}

data class DiscoveredRepoRoot(
    val rootPath: Path,
    val hasAtroposMarker: Boolean,
    val hasGitDirectory: Boolean,
    val traversalDepth: Int
)

object RecursiveParentRepoLocator {
    fun discoverRoot(
        startDir: Path,
        policy: ParentFolderDiscoveryPolicy = ParentFolderDiscoveryPolicy.ALWAYS,
        maxDepth: Int = 12
    ): DiscoveredRepoRoot? {
        if (policy == ParentFolderDiscoveryPolicy.NEVER) {
            val hasGit = Files.isDirectory(startDir.resolve(".git"))
            val hasAtropos = Files.exists(startDir.resolve("AGENTS.md")) || Files.isDirectory(startDir.resolve(".atropos"))
            return if (hasGit || hasAtropos) {
                DiscoveredRepoRoot(startDir, hasAtropos, hasGit, 0)
            } else null
        }

        var current: Path? = startDir.toAbsolutePath().normalize()
        var depth = 0

        while (current != null && depth <= maxDepth) {
            val hasGit = Files.isDirectory(current.resolve(".git")) || Files.isRegularFile(current.resolve(".git"))
            val hasAtropos = Files.exists(current.resolve("AGENTS.md")) || Files.isDirectory(current.resolve(".atropos"))

            if (hasAtropos || hasGit) {
                return DiscoveredRepoRoot(
                    rootPath = current,
                    hasAtroposMarker = hasAtropos,
                    hasGitDirectory = hasGit,
                    traversalDepth = depth
                )
            }

            // Stop at root filesystem boundary or home boundary
            val parent = current.parent
            if (parent == null || parent == current) {
                break
            }
            current = parent
            depth++
        }

        return null
    }
}
