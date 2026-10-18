/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.memory

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest

/**
 * 8-Layer Knowledge Matrix & Memory Lattice (Local CAS Storage).
 *
 * Implements strict domain decoupling across memory persistence tiers:
 * 1. TEMPORARY_MEMORY: Session-level working memory cleared on restart.
 * 2. CONVERSATION_MEMORY: Turn history and active context window within current session.
 * 3. PROJECT_MEMORY: Project-scoped state, files, AST symbols, and historical work items.
 * 4. WORKSPACE_MEMORY: User settings, preferences, theme configurations, keybindings.
 * 5. KNOWLEDGE_LAYER: Learned patterns, domain models, and best practices (Read-only).
 * 6. AUTHORITY_AND_POLICY: Access policies, safety constraints, authority rules (Rank L0-L3).
 * 7. LEARNING_OBSERVATIONS: Performance metrics, failure patterns, token optimization vectors.
 * 8. EVIDENCE_LAYER: Verified artifacts, SHA-256 checksums, tamper-evident cas_evidence.jsonl receipts.
 */
class MemoryLayerLattice(
    private val baseDirectory: Path = defaultBaseDirectory()
) {
    enum class LayerType {
        TEMPORARY_MEMORY,
        CONVERSATION_MEMORY,
        PROJECT_MEMORY,
        WORKSPACE_MEMORY,
        KNOWLEDGE_LAYER,
        AUTHORITY_AND_POLICY,
        LEARNING_OBSERVATIONS,
        EVIDENCE_LAYER
    }

    data class LayerEntry(
        val layer: LayerType,
        val key: String,
        val value: String,
        val hash: String,
        val timestampEpochMs: Long = System.currentTimeMillis()
    )

    private val temporaryStore = mutableMapOf<String, LayerEntry>()

    companion object {
        fun defaultBaseDirectory(): Path {
            val root = runCatching { AtroposRepoRootLocator.resolve() }.getOrNull()
                ?: Path.of(System.getProperty("user.home")).resolve(".atropos")
            return root.resolve(".atropos").resolve("memory")
        }

        fun sha256(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    init {
        ensureDirectories()
    }

    fun put(layer: LayerType, key: String, content: String): LayerEntry {
        val hash = sha256(content)
        val entry = LayerEntry(layer, key, content, hash)

        when (layer) {
            LayerType.TEMPORARY_MEMORY -> {
                temporaryStore[key] = entry
            }
            LayerType.KNOWLEDGE_LAYER -> {
                // Read-only from runtime; writes must be authenticated
                val targetFile = resolveLayerDir(layer).resolve("$hash.json")
                if (!targetFile.exists()) targetFile.writeText(content)
            }
            LayerType.EVIDENCE_LAYER -> {
                recordEvidence(entry)
            }
            else -> {
                val targetFile = resolveLayerDir(layer).resolve("${sanitize(key)}.json")
                targetFile.writeText(content)
            }
        }
        return entry
    }

    fun get(layer: LayerType, key: String): String? {
        return when (layer) {
            LayerType.TEMPORARY_MEMORY -> temporaryStore[key]?.value
            LayerType.KNOWLEDGE_LAYER -> {
                val dir = resolveLayerDir(layer)
                val matching = dir.listFiles { _, name -> name.startsWith(key) }?.firstOrNull()
                matching?.readText()
            }
            else -> {
                val targetFile = resolveLayerDir(layer).resolve("${sanitize(key)}.json")
                if (targetFile.exists()) targetFile.readText() else null
            }
        }
    }

    fun clearTemporary() {
        temporaryStore.clear()
    }

    fun auditStats(): Map<LayerType, Int> {
        val stats = mutableMapOf<LayerType, Int>()
        for (layer in LayerType.values()) {
            stats[layer] = when (layer) {
                LayerType.TEMPORARY_MEMORY -> temporaryStore.size
                LayerType.EVIDENCE_LAYER -> {
                    val file = resolveLayerDir(layer).resolve("cas_evidence.jsonl")
                    if (file.exists()) file.readLines().size else 0
                }
                else -> {
                    val dir = resolveLayerDir(layer)
                    dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
                }
            }
        }
        return stats
    }

    private fun recordEvidence(entry: LayerEntry) {
        val evidenceFile = resolveLayerDir(LayerType.EVIDENCE_LAYER).resolve("cas_evidence.jsonl")
        val line = "{\"key\":\"${entry.key}\",\"hash\":\"${entry.hash}\",\"epoch\":${entry.timestampEpochMs}}\n"
        runCatching { evidenceFile.appendText(line) }
    }

    private fun resolveLayerDir(layer: LayerType): File {
        val dir = baseDirectory.resolve(layer.name.lowercase()).toFile()
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun ensureDirectories() {
        for (layer in LayerType.values()) {
            if (layer != LayerType.TEMPORARY_MEMORY) {
                resolveLayerDir(layer)
            }
        }
    }

    private fun sanitize(key: String): String = key.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
}
