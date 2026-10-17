/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.io.File
import java.nio.file.Path

data class FileLocViolation(
    val filePath: String,
    val observedLoc: Int,
    val maxAllowedLoc: Int
)

/**
 * ATOM-LOC-008: 500-LOC Decomposition & Information Bottleneck Partitioning.
 *
 * Enforces the strict complexity bound: |Lines(F)| <= maxLoc (500 lines)
 * for all source files in production directories.
 * Information Bottleneck projection eliminates monolithic context contamination.
 */
class LocDecompositionGate(
    private val maxLoc: Int = 500
) {
    fun checkDirectory(sourceRoot: File): List<FileLocViolation> {
        if (!sourceRoot.exists() || !sourceRoot.isDirectory) return emptyList()

        val violations = mutableListOf<FileLocViolation>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java", "kts") }
            .forEach { file ->
                val lines = countPhysicalLines(file)
                if (lines > maxLoc) {
                    violations.add(
                        FileLocViolation(
                            filePath = file.absolutePath,
                            observedLoc = lines,
                            maxAllowedLoc = maxLoc
                        )
                    )
                }
            }
        return violations
    }

    private fun countPhysicalLines(file: File): Int {
        var count = 0
        file.forEachLine { count++ }
        return count
    }
}
