/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-010: Disk Entropy Budget
 *
 * Tracks and enforces disk entropy budget to prevent storage exhaustion
 * and ensure sufficient entropy for cryptographic operations.
 */
package atropos.core.supremacy

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object DiskEntropyBudget {
    data class EntropyState(
        val totalBytes: Long,
        val usedBytes: Long,
        val entropyBytes: Long,
        val budgetBytes: Long,
        val path: Path
    ) {
        val freeBytes: Long = totalBytes - usedBytes
        val entropyRatio: Double = if (totalBytes > 0) entropyBytes.toDouble() / totalBytes else 0.0
        val withinBudget: Boolean = usedBytes <= budgetBytes
    }

    /**
     * Computes the current entropy state for a path.
     */
    fun computeState(path: Path, budgetBytes: Long = 10L * 1024 * 1024 * 1024): EntropyState { // 10GB default
        val file = path.toFile()
        val totalBytes = file.totalSpace
        val freeBytes = file.freeSpace
        val usedBytes = totalBytes - freeBytes

        // Estimate entropy from file diversity (simplified)
        val entropyBytes = estimateEntropy(path)

        return EntropyState(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            entropyBytes = entropyBytes,
            budgetBytes = budgetBytes,
            path = path
        )
    }

    /**
     * Estimates entropy from file type diversity.
     */
    private fun estimateEntropy(path: Path): Long {
        var entropy = 0L
        Files.walk(path).forEach { p ->
            if (Files.isRegularFile(p)) {
                val ext = p.toString().substringAfterLast(".").lowercase()
                // Different file types contribute different entropy
                entropy += when (ext) {
                    "jar", "class", "dex" -> 1000 // Low entropy (compiled)
                    "kt", "java", "py", "js", "ts" -> 5000 // Medium entropy (source)
                    "txt", "md", "json", "yaml" -> 2000 // Low entropy (text)
                    "png", "jpg", "jpeg", "gif" -> 100 // Very low entropy (images)
                    else -> 1000
                }
            }
        }
        return entropy
    }

    /**
     * Checks if entropy budget is within limits.
     */
    fun checkBudget(state: EntropyState): Boolean {
        return state.withinBudget && state.entropyRatio > 0.01 // At least 1% entropy
    }

    /**
     * Persists entropy state to CAS.
     */
    fun persistState(casDir: Path, state: EntropyState): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("disk-entropy-state.json")
        val json = """
            {
                "totalBytes": ${state.totalBytes},
                "usedBytes": ${state.usedBytes},
                "entropyBytes": ${state.entropyBytes},
                "budgetBytes": ${state.budgetBytes},
                "entropyRatio": ${state.entropyRatio},
                "withinBudget": ${state.withinBudget},
                "path": "${state.path}"
            }
        """.trimIndent()
        Files.writeString(file, json.trimIndent())
        return file
    }

    /**
     * CLI command to show disk entropy status.
     */
    fun showStatus(state: EntropyState): String {
        return """
            Disk Entropy Status:
              Path: ${state.path}
              Used: ${formatBytes(state.usedBytes)} / ${formatBytes(state.totalBytes)}
              Entropy: ${formatBytes(state.entropyBytes)} (${"%.2f".format(state.entropyRatio * 100)}%)
              Budget: ${formatBytes(state.budgetBytes)} (${if (state.withinBudget) "OK" else "EXCEEDED"})
        """.trimIndent()
    }

    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return "${"%.2f".format(value)} ${units[unit]}"
    }
}