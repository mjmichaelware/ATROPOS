/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

import atropos.core.memory.MemoryLayerLattice
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest

/**
 * ATOM-TAX-014: Persistent Usage-Frequency Ranking Store.
 *
 * Tracks execution counts per canonical command, resolves top 3-5 most-used
 * commands, provides alphabetical remainder, supports global and per-project
 * scoping, and emits cryptographic evidence receipts on mutation.
 */
class CommandFrequencyRankingStore(
    private val memoryLattice: MemoryLayerLattice = MemoryLayerLattice()
) {
    private var projectScoped: Boolean = false
    private val globalCounts = mutableMapOf<String, Int>()
    private val projectCounts = mutableMapOf<String, Int>()

    init {
        loadRankings()
    }

    @Synchronized
    fun setProjectScoped(enabled: Boolean) {
        this.projectScoped = enabled
    }

    fun isProjectScoped(): Boolean = projectScoped

    @Synchronized
    fun recordUsage(command: String, project: String? = null): Int {
        val canonical = command.trim().lowercase()
        val beforeCount = if (projectScoped && project != null) {
            projectCounts[canonical] ?: 0
        } else {
            globalCounts[canonical] ?: 0
        }

        val newCount = beforeCount + 1
        if (projectScoped && project != null) {
            projectCounts[canonical] = newCount
            persistProject(project)
        } else {
            globalCounts[canonical] = newCount
            persistGlobal()
        }

        emitEvidenceReceipt(
            action = "COMMAND_USAGE_RECORDED",
            canonical = canonical,
            before = beforeCount.toString(),
            after = newCount.toString()
        )
        return newCount
    }

    @Synchronized
    fun topMostUsed(limit: Int = 5): List<String> {
        val safeLimit = limit.coerceIn(3, 5)
        val active = if (projectScoped && projectCounts.isNotEmpty()) projectCounts else globalCounts
        return active.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(safeLimit)
            .map { it.key }
    }

    @Synchronized
    fun alphabeticalRemainder(allCommands: List<String>, limit: Int = 5): List<String> {
        val top = topMostUsed(limit).toSet()
        return allCommands
            .map { it.trim().lowercase() }
            .filterNot { it in top }
            .distinct()
            .sorted()
    }

    @Synchronized
    fun getCount(command: String): Int {
        val canonical = command.trim().lowercase()
        return if (projectScoped && projectCounts.isNotEmpty()) {
            projectCounts[canonical] ?: 0
        } else {
            globalCounts[canonical] ?: 0
        }
    }

    private fun persistGlobal() {
        val json = serializeMap(globalCounts)
        memoryLattice.put(
            layer = MemoryLayerLattice.LayerType.WORKSPACE_MEMORY,
            key = "workspace_rankings",
            content = json
        )
    }

    private fun persistProject(project: String) {
        val json = serializeMap(projectCounts)
        memoryLattice.put(
            layer = MemoryLayerLattice.LayerType.PROJECT_MEMORY,
            key = "project_rankings_${sanitize(project)}",
            content = json
        )
    }

    private fun loadRankings() {
        val globalJson = memoryLattice.get(
            layer = MemoryLayerLattice.LayerType.WORKSPACE_MEMORY,
            key = "workspace_rankings"
        )
        if (globalJson != null) {
            globalCounts.putAll(deserializeMap(globalJson))
        }
    }

    fun loadProject(project: String) {
        val projJson = memoryLattice.get(
            layer = MemoryLayerLattice.LayerType.PROJECT_MEMORY,
            key = "project_rankings_${sanitize(project)}"
        )
        if (projJson != null) {
            projectCounts.clear()
            projectCounts.putAll(deserializeMap(projJson))
        }
    }

    private fun emitEvidenceReceipt(action: String, canonical: String, before: String, after: String) {
        val timestamp = System.currentTimeMillis()
        val payload = "$timestamp:$action:$canonical:$before:$after"
        val sha256 = sha256Hex(payload)
        val receiptJson = "{\"timestamp\":$timestamp,\"sha256\":\"$sha256\",\"action\":\"$action\"," +
            "\"actor\":\"CommandFrequencyRankingStore\",\"surface\":\"CLI\",\"before\":$before,\"after\":$after}"
        memoryLattice.put(
            layer = MemoryLayerLattice.LayerType.EVIDENCE_LAYER,
            key = "ranking_mutation_$timestamp",
            content = receiptJson
        )
    }

    private fun serializeMap(map: Map<String, Int>): String = buildString {
        append("{\n")
        val entries = map.entries.toList()
        for (i in entries.indices) {
            val (k, v) = entries[i]
            val comma = if (i < entries.size - 1) "," else ""
            append("  \"$k\": $v$comma\n")
        }
        append("}")
    }

    private fun deserializeMap(json: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val regex = Regex("\"([^\"]+)\"\\s*:\\s*(\\d+)")
        regex.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val count = match.groupValues[2].toIntOrNull() ?: 0
            result[key] = count
        }
        return result
    }

    private fun sanitize(input: String): String =
        input.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
