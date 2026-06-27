package atropos.cli.ui

import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemorySearchHit

class StatusMemoryRenderer(
    private val store: LocalMemoryStore = LocalMemoryStore()
) {
    fun render(): String {
        val status = store.status()
        return buildString {
            appendLine("memory:")
            appendLine("  local root: ready")
            appendLine("  records: ${status.totalRecords}")
            appendLine("  jsonl: ${status.jsonlFile.path}")
            appendLine("  sqlite: ${if (status.sqliteAvailable) "available" else "unavailable"}")
            appendLine("  sqlite-vec: ${if (status.sqliteVecAvailable) "available" else "optional/unavailable"}")
            appendLine("  pinecone: ${if (status.pineconeConfigured) "configured optional" else "optional/off"}")
            appendLine("  supabase: ${if (status.supabaseConfigured) "configured optional" else "optional/off"}")
            appendLine("  google metadata: ${if (status.googleMetadataConfigured) "configured optional" else "optional/off"}")
            appendLine("  policy: local SQLite/JSONL first; remote failure never blocks local progress")
        }
    }

    fun renderSearch(hits: List<MemorySearchHit>): String {
        return buildString {
            appendLine("memory search:")
            if (hits.isEmpty()) {
                appendLine("  no hits")
            } else {
                hits.forEach { hit ->
                    appendLine("  ${hit.score.toString().padStart(3)} ${hit.record.kind.name.lowercase()} ${hit.record.id} ${hit.record.title}")
                }
            }
        }
    }
}
