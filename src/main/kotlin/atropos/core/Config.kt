package atropos.core
import java.io.File
import java.nio.file.Path
data class ApiKeys(val groq: String, val openai: String, val anthropic: String, val xai: String)
data class LakehouseConfig(val mountPath: String, val dbPath: String)
data class RuntimeConfig(
    val defaultProvider: String,
    val temperature: Double,
    val localOnly: Boolean = false,
    val zeroRetentionResearch: Boolean = false
)

/** One process-wide runtime mode source shared by routing and side-effect policy. */
object RuntimeMode {
    fun localOnly(environment: Map<String, String> = System.getenv()): Boolean =
        environment["ATROPOS_LOCAL_ONLY"]?.trim()?.lowercase() in setOf("1", "true", "yes", "on")

    fun zeroRetentionResearch(environment: Map<String, String> = System.getenv()): Boolean =
        environment["ATROPOS_ZERO_RETENTION"]?.trim()?.lowercase() in setOf("1", "true", "yes", "on")
}

class AtroposConfig(val keys: ApiKeys, val lakehouse: LakehouseConfig, val runtime: RuntimeConfig) {
    companion object {
        fun configRoot(): Path = Path.of(System.getProperty("user.home")).resolve(".atropos")

        fun load(): AtroposConfig {
            val configPath = configRoot().resolve("config.json").toFile()
            val content = if (configPath.exists()) configPath.readText() else "{}"
            val groqKey = extract(content, "groq_api_key") ?: ""
            val openAiKey = extract(content, "openai_api_key") ?: ""
            val anthropicKey = extract(content, "anthropic_api_key") ?: ""
            val xaiKey = extract(content, "xai_api_key") ?: ""
            // Resolve from this device's working directory rather than one
            // developer's Termux home. A literal path here meant every other
            // machine silently pointed its lakehouse at a directory that does
            // not exist, and only the original device worked.
            val mount = extract(content, "lakehouse_mount_path")
                ?: AtroposRepoRootLocator.resolve().resolve(".atropos/lakehouse").toString()
            val db = extract(content, "lakehouse_db_path") ?: "$mount/vector_storage.db"
            val provider = extract(content, "default_provider") ?: "groq"
            val localOnly = RuntimeMode.localOnly() || extractBoolean(content, "local_only")
            val zeroRetention = RuntimeMode.zeroRetentionResearch() || extractBoolean(content, "zero_retention_research")
            return AtroposConfig(
                ApiKeys(groqKey, openAiKey, anthropicKey, xaiKey),
                LakehouseConfig(mount, db),
                RuntimeConfig(provider, 0.2, localOnly, zeroRetention)
            )
        }
        private fun extract(json: String, key: String): String? {
            return "\"$key\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)?.groups?.get(1)?.value
        }
        private fun extractBoolean(json: String, key: String): Boolean =
            "\"$key\"\\s*:\\s*(true|false)".toRegex().find(json)?.groups?.get(1)?.value?.toBoolean() == true
    }
    fun debugDump() {
        println("Groq Key:      ${if (keys.groq.isNotEmpty()) "ONLINE [●]" else "OFFLINE [○]"}")
        println("OpenAI Key:    ${if (keys.openai.isNotEmpty()) "ONLINE [●]" else "OFFLINE [○]"}")
        println("Anthropic Key: ${if (keys.anthropic.isNotEmpty()) "ONLINE [●]" else "OFFLINE [○]"}")
        println("xAI Key:       ${if (keys.xai.isNotEmpty()) "ONLINE [●]" else "OFFLINE [○]"}")
    }
}
