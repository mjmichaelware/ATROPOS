package atropos.core.custodian

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.UUID

data class CustodianAction(
    val id: String = "clean-${UUID.randomUUID().toString().take(12)}",
    val action: String,
    val target: String,
    val bytesFreed: Long = 0,
    val timestamp: Instant = Instant.now()
)

data class CustodianReport(
    val actions: List<CustodianAction>,
    val totalBytesFreed: Long,
    val summary: String
)

class CustodianService(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve()
) {
    private val protectedDirs = setOf(".atropos/agent", ".atropos/memory", ".atropos/policy", ".atropos/backups")
    private val tempPatterns = listOf("*.tmp", "*.temp", "*.swp", "*.tmp_*")

    fun cleanTempFiles(): CustodianReport {
        val actions = mutableListOf<CustodianAction>()
        val atroposDir = repoRoot.resolve(".atropos")
        if (!isSafeDirectory(atroposDir)) return CustodianReport(emptyList(), 0, "no safe .atropos dir")

        var totalFreed = 0L
        Files.walk(atroposDir).forEach { path ->
            if (protectedDirs.any { path.startsWith(repoRoot.resolve(it)) }) return@forEach
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                val name = path.fileName.toString()
                val isTemp = tempPatterns.any { pattern ->
                    val glob = pattern.replace("*", ".*").replace("?", ".")
                    name.matches(Regex(glob, RegexOption.IGNORE_CASE))
                }
                if (isTemp) {
                    val size = Files.size(path)
                    Files.deleteIfExists(path)
                    actions += CustodianAction(action = "delete-temp", target = repoRoot.relativize(path).toString(), bytesFreed = size)
                    totalFreed += size
                }
            }
        }
        return CustodianReport(actions, totalFreed, "cleaned ${actions.size} temp files, freed ${formatBytes(totalFreed)}")
    }

    fun pruneDeadSnapshots(maxAgeMinutes: Long = 60): CustodianReport {
        val actions = mutableListOf<CustodianAction>()
        val dirsToScan = listOf(".atropos/director", ".atropos/territory", ".atropos/agent/queue")
        var totalFreed = 0L

        if (maxAgeMinutes < 0) {
            return CustodianReport(emptyList(), 0, "invalid negative snapshot age")
        }

        for (dir in dirsToScan) {
            val path = repoRoot.resolve(dir)
            if (!isSafeDirectory(path)) continue
            val cutoff = Instant.now().minus(java.time.Duration.ofMinutes(maxAgeMinutes))
            Files.walk(path).forEach { file ->
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        val attrs = Files.readAttributes(file, BasicFileAttributes::class.java)
                        val lastModified = attrs.lastModifiedTime().toInstant()
                        if (lastModified.isBefore(cutoff) && file.fileName.toString().contains("snapshot")) {
                            val size = Files.size(file)
                            Files.deleteIfExists(file)
                            actions += CustodianAction(action = "prune-snapshot", target = repoRoot.relativize(file).toString(), bytesFreed = size)
                            totalFreed += size
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        return CustodianReport(actions, totalFreed, "pruned ${actions.size} snapshots, freed ${formatBytes(totalFreed)}")
    }

    private fun isSafeDirectory(path: Path): Boolean {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return false
        val normalized = path.toAbsolutePath().normalize()
        val root = repoRoot.toAbsolutePath().normalize()
        if (!normalized.startsWith(root)) return false
        var cursor: Path? = normalized
        while (cursor != null && cursor != root) {
            if (Files.isSymbolicLink(cursor)) return false
            cursor = cursor.parent
        }
        return cursor == root
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
