package atropos.core
import java.io.File
data class ApiKeys(val groq: String, val openai: String, val anthropic: String, val xai: String)
data class LakehouseConfig(val mountPath: String, val dbPath: String)
data class RuntimeConfig(val defaultProvider: String, val temperature: Double)
class AtroposConfig(val keys: ApiKeys, val lakehouse: LakehouseConfig, val runtime: RuntimeConfig) {
    companion object {
        fun load(): AtroposConfig {
            val configPath = File(System.getProperty("user.home"), ".atropos/config.json")
            val content = if (configPath.exists()) configPath.readText() else "{}"
            val groqKey = extract(content, "groq_api_key") ?: ""
            val openAiKey = extract(content, "openai_api_key") ?: ""
            val anthropicKey = extract(content, "anthropic_api_key") ?: ""
            val xaiKey = extract(content, "xai_api_key") ?: ""
            val mount = extract(content, "lakehouse_mount_path") ?: "/data/data/com.termux/files/home/ATROPOS/lakehouse"
            val db = extract(content, "lakehouse_db_path") ?: "$mount/vector_storage.db"
            val provider = extract(content, "default_provider") ?: "groq"
            return AtroposConfig(ApiKeys(groqKey, openAiKey, anthropicKey, xaiKey), LakehouseConfig(mount, db), RuntimeConfig(provider, 0.2))
        }
        private fun extract(json: String, key: String): String? {
            return "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)?.groups?.get(1)?.value
        }
    }
    fun debugDump() {
        println("Groq Key:      ${if (keys.groq.isNotEmpty()) "ONLINE [●]" else "OFFLINE [○]"}")
        println("OpenAI Key:    ${if (keys.openai.isNotEmpty()) "ONLINE [●]" else "OFFLINE [○]"}")
        println("Anthropic Key: ${if (keys.anthropic.isNotEmpty()) "ONLINE [●]" else "OFFLINE [○]"}")
        println("xAI Key:       ${if (keys.xai.isNotEmpty()) "ONLINE [●]" else "OFFLINE [○]"}")
    }
}
