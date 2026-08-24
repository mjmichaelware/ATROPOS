package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.paid.EmergencyPaidGate
import atropos.core.security.TokenIsolationVault
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

enum class CheapProviderHealth { HEALTHY, UNHEALTHY, UNTESTED }

data class DiscoveredProvider(
    val providerId: String,
    val health: CheapProviderHealth,
    val matchedEnvNames: List<String> = emptyList(),
    val disabled: Boolean = false,
    val preferred: Boolean = false,
    val preferenceRank: Int = Int.MAX_VALUE
)

/** Local-only provider inventory. It persists labels and state, never values. */
class ProviderOnboardingService(
    /** Null selects the installed user-local config root; tests/workspace callers may pin a root. */
    private val root: Path? = null,
    private val environment: Map<String, String> = System.getenv(),
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
) {
    private val configRoot = root?.resolve(".atropos") ?: AtroposConfig.configRoot()
    private val configDir = configRoot.resolve("provider").normalize()
    private val configFile = configDir.resolve("providers.json")
    private val localVault = TokenIsolationVault(configRoot.resolve("secrets"))
    private val aliases = linkedMapOf(
        "openai" to listOf("OPENAI_API_KEY", "OPENAI_KEY", "OPENAI_TOKEN", "OPENAI_API_BASE"),
        "anthropic" to listOf("ANTHROPIC_API_KEY", "ANTHROPIC_KEY", "CLAUDE_API_KEY", "CLAUDE_TOKEN"),
        "groq" to listOf("GROQ_API_KEY", "GROQ_KEY", "GROQ_TOKEN"),
        "xai" to listOf("XAI_API_KEY", "XAI_KEY", "GROK_API_KEY", "GROK_TOKEN"),
        "gemini" to listOf("GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_GEMINI_API_KEY"),
        "openrouter" to listOf("OPENROUTER_API_KEY", "OPENROUTER_KEY"),
        "together" to listOf("TOGETHER_API_KEY", "TOGETHERAI_API_KEY"),
        "deepseek_direct" to listOf("DEEPSEEK_API_KEY", "DEEPSEEK_KEY"),
        "mistral" to listOf("MISTRAL_API_KEY", "MISTRAL_TOKEN"),
        "fireworks" to listOf("FIREWORKS_API_KEY", "FIREWORKS_AI_API_KEY"),
        "azure_openai" to listOf("AZURE_OPENAI_API_KEY", "AZURE_API_KEY", "AZURE_OPENAI_ENDPOINT"),
        "aws_bedrock" to listOf("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_REGION", "AWS_DEFAULT_REGION", "AWS_PROFILE"),
        "ollama" to listOf("OLLAMA_HOST", "OLLAMA_MODEL")
    )
    private val aliasPrefixes = mapOf(
        "anthropic" to listOf("CLAUDE_"),
        "xai" to listOf("GROK_"),
        "openai" to listOf("OPENAI_"),
        "gemini" to listOf("GOOGLE_GEMINI_", "GOOGLE_"),
        "azure_openai" to listOf("AZURE_"),
        "aws_bedrock" to listOf("AWS_")
    )

    fun refresh(): List<DiscoveredProvider> {
        val prior = readConfig()
        val ids = (aliases.keys + registry.getAll().map { it.id }).distinct()
        val records = ids.map { id ->
            val names = (aliases[id].orEmpty().filter { !environment[it].isNullOrBlank() } +
                aliases[id].orEmpty().filter(::localSecretPresent) +
                environment.keys.filter { key ->
                    genericProviderKeyMatches(key, id) && !environment[key].isNullOrBlank()
                } + aliasPrefixes[id].orEmpty().flatMap { prefix ->
                    environment.keys.filter {
                        it.startsWith(prefix, ignoreCase = true) && !environment[it].isNullOrBlank()
                    }
                }).distinct()
            val descriptor = registry.getById(id)
            val hasCredential = names.any(::looksLikeCredentialName)
            val malformedCredential = names.any(::malformedEnvironmentCredential)
            val health = when {
                prior[id]?.disabled == true -> CheapProviderHealth.UNHEALTHY
                malformedCredential -> CheapProviderHealth.UNHEALTHY
                descriptor?.isLocal == true && id == "local" -> CheapProviderHealth.HEALTHY
                descriptor?.isLocal == true && names.isNotEmpty() -> CheapProviderHealth.HEALTHY
                hasCredential -> CheapProviderHealth.HEALTHY
                names.isNotEmpty() -> CheapProviderHealth.UNTESTED
                else -> CheapProviderHealth.UNTESTED
            }
            val priorRank = prior[id]?.preferenceRank ?: Int.MAX_VALUE
            DiscoveredProvider(
                id,
                health,
                names.distinct().sorted(),
                prior[id]?.disabled == true,
                prior[id]?.preferred == true,
                if (prior[id]?.preferred == true && priorRank == Int.MAX_VALUE) 0 else priorRank
            )
        }
        writeConfig(records)
        return records
    }

    fun list(): List<DiscoveredProvider> = readConfig().values.sortedWith(
        compareBy<DiscoveredProvider> { preferenceKey(it) }.thenBy { it.providerId }
    )
        .ifEmpty { refresh() }

    fun healthyProviderIds(): Set<String> = list().filter { it.health == CheapProviderHealth.HEALTHY && !it.disabled }
        .map { it.providerId }.toSet()

    /** Persisted operator preference, limited to configured healthy providers. */
    fun preferredProviderIds(): List<String> = list()
        .filter { it.health == CheapProviderHealth.HEALTHY && !it.disabled && it.preferred }
        .sortedWith(compareBy<DiscoveredProvider> { preferenceKey(it) }.thenBy { it.providerId })
        .map { it.providerId }

    fun prefer(providerId: String): List<DiscoveredProvider> {
        val current = list()
        require(providerId in current.map { it.providerId }) { "unknown provider: $providerId" }
        val priorPreferred = current
            .filter { it.preferred && it.providerId != providerId }
            .sortedWith(compareBy<DiscoveredProvider> { preferenceKey(it) }.thenBy { it.providerId })
            .map { it.providerId }
        val ranks = (listOf(providerId) + priorPreferred).distinct().withIndex().associate { it.value to it.index }
        val updated = current.map { record ->
            val rank = ranks[record.providerId] ?: Int.MAX_VALUE
            record.copy(preferred = rank != Int.MAX_VALUE, preferenceRank = rank)
        }
        writeConfig(updated)
        return updated
    }

    fun disable(providerId: String): List<DiscoveredProvider> {
        require(providerId in list().map { it.providerId }) { "unknown provider: $providerId" }
        val updated = list().map { if (it.providerId == providerId) it.copy(disabled = true) else it }
        writeConfig(updated)
        return updated
    }

    fun connectToVault(providerId: String, secret: String, envName: String = aliases[providerId]?.firstOrNull() ?: "${providerId.uppercase()}_API_KEY"): Path {
        require(providerId in aliases || registry.getById(providerId) != null) { "unknown provider: $providerId" }
        require(secret.isNotBlank()) { "provider secret must not be blank" }
        val path = localVault.writeSecret(envName, secret)
        refresh()
        return path
    }

    fun defaultEnvName(providerId: String): String =
        aliases[providerId]?.firstOrNull() ?: "${providerId.uppercase()}_API_KEY"

    fun render(): String = buildString {
        val rows = list()
        val healthy = rows.count { it.health == CheapProviderHealth.HEALTHY && !it.disabled }
        appendLine("PROVIDERS")
        appendLine("  discovered=${rows.size} healthy=$healthy disabled=${rows.count { it.disabled }}")
        rows.forEach { record ->
            appendLine("  ${record.providerId} health=${record.health.name.lowercase()} " +
                "keys=${record.matchedEnvNames.joinToString(",").ifBlank { "none" }} " +
                "${if (record.preferred) "preferred " else ""}${if (record.disabled) "disabled" else ""}".trim())
        }
        if (rows.none { it.health == CheapProviderHealth.HEALTHY && !it.disabled }) {
            appendLine("  no healthy providers; set one key, for example: export GROQ_API_KEY=…")
        }
    }.trimEnd()

    private fun readConfig(): Map<String, DiscoveredProvider> {
        if (!Files.isRegularFile(configFile)) return emptyMap()
        val objectPattern = Regex("\\{\\\"id\\\":\\\"([^\"]+)\\\",\\\"health\\\":\\\"([^\"]+)\\\",\\\"env\\\":\\\"([^\"]*)\\\",\\\"disabled\\\":(true|false),\\\"preferred\\\":(true|false)(?:,\\\"rank\\\":(-?\\d+))?\\}")
        return objectPattern.findAll(Files.readString(configFile)).mapNotNull { match ->
            val groups = match.groupValues
            val id = groups[1]
            val health = groups[2]
            val env = groups[3]
            val disabled = groups[4]
            val preferred = groups[5]
            val rank = groups.getOrNull(6)?.toIntOrNull() ?: Int.MAX_VALUE
            runCatching {
                id to DiscoveredProvider(
                    id,
                    CheapProviderHealth.valueOf(health),
                    env.split(',').filter { it.isNotBlank() },
                    disabled.toBoolean(),
                    preferred.toBoolean(),
                    rank
                )
            }.getOrNull()
        }.toMap()
    }

    private fun writeConfig(records: List<DiscoveredProvider>) {
        require(configFile.startsWith(configRoot.toAbsolutePath().normalize())) { "provider config escaped local root" }
        Files.createDirectories(configDir)
        val temp = Files.createTempFile(configDir, "providers", ".tmp")
        val content = records.sortedBy { it.providerId }.joinToString(",", prefix = "[", postfix = "]\n") { record ->
            "{\"id\":\"${json(record.providerId)}\",\"health\":\"${record.health.name}\",\"env\":\"${json(record.matchedEnvNames.joinToString(","))}\",\"disabled\":${record.disabled},\"preferred\":${record.preferred},\"rank\":${record.preferenceRank}}"
        } + "\n"
        Files.writeString(temp, content)
        try { Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: Exception) { Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun json(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun preferenceKey(record: DiscoveredProvider): Int =
        if (record.preferred && record.preferenceRank == Int.MAX_VALUE) 0 else record.preferenceRank

    private fun genericProviderKeyMatches(key: String, providerId: String): Boolean {
        if (!key.startsWith("ATROPOS_PROVIDER_", ignoreCase = true)) return false
        val suffix = key.substring("ATROPOS_PROVIDER_".length)
        val providerPrefix = providerId.substringBefore('_')
        return suffix.equals(providerId, ignoreCase = true) ||
            suffix.startsWith("${providerId}_", ignoreCase = true) ||
            suffix.startsWith("${providerPrefix}_", ignoreCase = true)
    }

    private fun localSecretPresent(name: String): Boolean =
        runCatching { localVault.readSecretResult(name) is atropos.core.security.VaultReadResult.Available }.getOrDefault(false)

    private fun looksLikeCredentialName(name: String): Boolean {
        val normalized = name.uppercase()
        return normalized.contains("API_KEY") ||
            normalized.endsWith("_KEY") ||
            normalized.endsWith("_TOKEN") ||
            normalized == "AWS_ACCESS_KEY_ID" ||
            normalized == "AWS_SECRET_ACCESS_KEY"
    }

    /**
     * Cheap shape validation only. It deliberately does not inspect prefixes,
     * lengths, or provider-specific formats: those rules change and would
     * misclassify valid tokens. Control characters cannot be valid shell/env
     * credential values and are safe to reject without logging the value.
     */
    private fun malformedEnvironmentCredential(name: String): Boolean {
        if (!looksLikeCredentialName(name)) return false
        val value = environment[name] ?: return false
        return value.any { it == '\n' || it == '\r' || it.isISOControl() }
    }
}

data class ProviderApprovalCard(val providerId: String, val model: String?, val reason: String, val estimatedRisk: String) {
    fun render(): String = "PAID APPROVAL REQUIRED provider=$providerId model=${model ?: "default"} reason=$reason risk=$estimatedRisk; approve explicitly before continuing"
}

class ProviderPolicyGate(
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val healthy: () -> Set<String>,
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val localOnly: Boolean = AtroposConfig.load().runtime.localOnly
) {
    fun freeCascade(capability: ApiCapability): List<ProviderDescriptor> = registry.getByCapability(capability)
        .filter { it.id in healthy() && it.billingClass() != BillingClass.PAID && (!localOnly || it.isLocal) }
        .sortedWith(compareBy({ if (it.isLocal) 0 else 1 }, { it.quotaTier }, { it.id }))

    fun paidApproval(capability: ApiCapability, reason: String): ProviderApprovalCard? = registry.getByCapability(capability)
        .firstOrNull { it.id in healthy() && !localOnly && it.billingClass() == BillingClass.PAID && !paidGate.isProviderUnlocked(it.id) }
        ?.let { ProviderApprovalCard(it.id, it.endpointId, reason, "paid request and quota risk") }

    fun requirePaidApproval(providerId: String) {
        check(paidGate.isProviderUnlocked(providerId)) { "paid provider blocked until explicit approval: $providerId" }
    }
}
