/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.provider.adapter.OpenAiCompatibleProviderCatalog
import atropos.core.security.CredentialBootstrap
import atropos.core.security.TokenIsolationVault
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DiscoveredModel(
    val id: String,
    val ownedBy: String? = null,
    val active: Boolean = true
)

data class ModelDiscoveryResult(
    val providerId: String,
    val online: Boolean,
    val models: List<DiscoveredModel>,
    val failureReason: String? = null
)

/**
 * Dynamic Model Discovery Engine.
 *
 * Replaces hardcoded model strings with live /v1/models polling,
 * fuzzy resolution, alias mapping, and dead-link pruning across
 * the active provider cascade.
 */
object ModelDiscoveryEngine {
    private val discoveredModels = ConcurrentHashMap<String, List<DiscoveredModel>>()
    private val prunedProviders = ConcurrentHashMap.newKeySet<String>()
    private val pruneReasons = ConcurrentHashMap<String, String>()
    private val isPolled = AtomicBoolean(false)
    private val lastPollEpochMs = AtomicLong(0L)

    private val FAMILY_FALLBACKS: Map<String, List<String>> = mapOf(
        "llama-3.1-8b-instant" to listOf("llama-3.3-70b-versatile", "llama-3.1-8b-versatile", "llama-3.1-70b-versatile", "llama3-70b-8192", "llama3-8b-8192"),
        "llama-3" to listOf("llama-3.3-70b-versatile", "llama-3.1-8b-versatile", "llama-3.1-70b-versatile", "llama3-70b-8192", "llama3-8b-8192"),
        "llama-3.1" to listOf("llama-3.3-70b-versatile", "llama-3.1-8b-versatile", "llama-3.1-70b-versatile"),
        "llama-3.2" to listOf("llama-3.2-3b-preview", "llama-3.2-1b-preview"),
        "llama-3.3" to listOf("llama-3.3-70b-versatile", "llama-3.3-70b-specdec"),
        "gpt-4" to listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo"),
        "gpt-4o-mini" to listOf("gpt-4o-mini", "gpt-4o"),
        "claude-3-5-sonnet" to listOf("claude-3-5-sonnet-latest", "claude-3-5-sonnet-20241022", "claude-3-sonnet-20240229"),
        "claude" to listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-haiku-20240307"),
        "mistral" to listOf("mistral-large-latest", "mistral-small-latest", "open-mistral-7b"),
        "deepseek" to listOf("deepseek-chat", "deepseek-coder"),
        "qwen" to listOf("Qwen/Qwen2.5-72B-Instruct", "Qwen/Qwen2.5-7B-Instruct", "qwen-2.5-72b-instruct")
    )

    fun isPruned(providerId: String): Boolean {
        val clean = providerId.trim().lowercase(Locale.US)
        if (clean.isBlank()) return false
        if (!hasCredentials(clean)) return true
        return prunedProviders.contains(clean)
    }

    fun pruneReason(providerId: String): String? =
        pruneReasons[providerId.trim().lowercase(Locale.US)]

    fun prune(providerId: String, reason: String) {
        val clean = providerId.trim().lowercase(Locale.US)
        prunedProviders.add(clean)
        pruneReasons[clean] = reason
        discoveredModels.remove(clean)
    }

    fun unprune(providerId: String) {
        val clean = providerId.trim().lowercase(Locale.US)
        prunedProviders.remove(clean)
        pruneReasons.remove(clean)
    }

    fun hasDiscoveredModels(providerId: String): Boolean =
        !discoveredModels[providerId.trim().lowercase(Locale.US)].isNullOrEmpty()

    fun getLiveModels(providerId: String): List<String> =
        discoveredModels[providerId.trim().lowercase(Locale.US)]?.map { it.id }?.filterNot { isSpecializedModel(it) } ?: emptyList()

    fun filterActiveCascade(candidates: List<String>): List<String> {
        val unpruned = candidates.filterNot { isPruned(it) }
        return if (unpruned.isEmpty() && candidates.isNotEmpty()) candidates else unpruned
    }

    fun dropsModelFamily(providerId: String, family: String): Boolean {
        val clean = providerId.trim().lowercase(Locale.US)
        val live = discoveredModels[clean] ?: return false
        if (live.isEmpty()) return true
        val cleanFamily = family.trim().lowercase(Locale.US)
        return live.none { it.id.lowercase(Locale.US).contains(cleanFamily) }
    }

    fun pruneIfDropsFamily(providerId: String, family: String) {
        val clean = providerId.trim().lowercase(Locale.US)
        if (dropsModelFamily(clean, family)) {
            prune(clean, "drops model family: $family")
        }
    }

    fun hasCredentials(providerId: String): Boolean {
        val clean = providerId.trim().lowercase(Locale.US)
        if (clean == "ollama" || clean == "local" || clean == "localtoolchain") return true
        val spec = OpenAiCompatibleProviderCatalog.get(clean)
        val envKey = spec?.apiKeyEnv
        if (envKey != null && !resolveKey(clean, envKey).isNullOrBlank()) {
            return true
        }
        return FallbackChainRegistry.getActiveKey(clean) != null ||
            !resolveKey(clean, "${clean.uppercase(Locale.US)}_API_KEY").isNullOrBlank()
    }

    fun resolveActiveProvider(defaultProvider: String): String {
        val cleanDefault = defaultProvider.trim().lowercase(Locale.US)
        if (cleanDefault.isNotBlank() && cleanDefault != "default" && !isPruned(cleanDefault) && hasCredentials(cleanDefault)) {
            return cleanDefault
        }
        val healthy = discoveredModels.keys().toList().filterNot { prunedProviders.contains(it) }
        if (healthy.isNotEmpty()) {
            return healthy.first()
        }
        val preferred = listOf("groq", "openai", "anthropic", "together_ai", "cerebras", "deepseek", "mistral", "ollama")
        for (cand in preferred) {
            if (!isPruned(cand) && hasCredentials(cand)) return cand
        }
        for (cand in preferred) {
            if (hasCredentials(cand)) return cand
        }
        return if (cleanDefault.isNotBlank()) cleanDefault else "groq"
    }

    fun ensureDiscovered(timeoutMs: Long = 3000L) {
        val now = System.currentTimeMillis()
        if (!isPolled.get() || (now - lastPollEpochMs.get() > 300_000L)) {
            discoverAndPrune(timeoutMs)
        }
    }

    fun discoverAndPrune(timeoutMs: Long = 3000L): Map<String, ModelDiscoveryResult> {
        val results = mutableMapOf<String, ModelDiscoveryResult>()
        val specs = OpenAiCompatibleProviderCatalog.all()

        for (spec in specs) {
            val providerId = spec.providerId.lowercase(Locale.US)
            val key = resolveKey(providerId, spec.apiKeyEnv)
            if (key.isNullOrBlank()) {
                prune(providerId, "missing credentials (${spec.apiKeyEnv})")
                results[providerId] = ModelDiscoveryResult(
                    providerId = providerId,
                    online = false,
                    models = emptyList(),
                    failureReason = "missing credentials"
                )
                continue
            }

            val endpoint = computeModelsEndpoint(spec.baseUrl, spec.endpointEnv)
            val discovery = pollEndpoint(
                providerId = providerId,
                endpoint = endpoint,
                apiKey = key,
                apiKeyHeader = spec.apiKeyHeader,
                headers = spec.headers,
                timeoutMs = timeoutMs
            )
            results[providerId] = discovery
            if (discovery.online && discovery.models.isNotEmpty()) {
                discoveredModels[providerId] = discovery.models
                unprune(providerId)
            } else {
                prune(providerId, discovery.failureReason ?: "no active models found")
            }
        }

        // Also poll Anthropic if key present
        val anthropicKey = resolveKey("anthropic", "ANTHROPIC_API_KEY")
        if (!anthropicKey.isNullOrBlank()) {
            val anthropicResult = pollAnthropic(anthropicKey, timeoutMs)
            results["anthropic"] = anthropicResult
            if (anthropicResult.online && anthropicResult.models.isNotEmpty()) {
                discoveredModels["anthropic"] = anthropicResult.models
                unprune("anthropic")
            } else {
                prune("anthropic", anthropicResult.failureReason ?: "anthropic unreachable")
            }
        } else {
            prune("anthropic", "missing ANTHROPIC_API_KEY")
        }

        // Also poll Ollama
        val ollamaResult = pollOllama(timeoutMs)
        results["ollama"] = ollamaResult
        if (ollamaResult.online && ollamaResult.models.isNotEmpty()) {
            discoveredModels["ollama"] = ollamaResult.models
            unprune("ollama")
        } else {
            prune("ollama", ollamaResult.failureReason ?: "ollama offline")
        }

        isPolled.set(true)
        lastPollEpochMs.set(System.currentTimeMillis())
        return results
    }

    private fun computeModelsEndpoint(baseUrl: String, endpointEnv: String?): String {
        val override = endpointEnv?.let { System.getenv(it) }?.takeIf { it.isNotBlank() }
        val effective = override ?: baseUrl
        return if (effective.endsWith("/chat/completions")) {
            effective.removeSuffix("/chat/completions") + "/models"
        } else if (effective.endsWith("/chat/completions/")) {
            effective.removeSuffix("/chat/completions/") + "/models"
        } else if (effective.contains("/v1/")) {
            effective.substringBefore("/v1/") + "/v1/models"
        } else {
            effective.trimEnd('/') + "/models"
        }
    }

    fun pollEndpoint(
        providerId: String,
        endpoint: String,
        apiKey: String,
        apiKeyHeader: String = "Authorization",
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 3000L
    ): ModelDiscoveryResult {
        return try {
            val uri = URI.create(endpoint)
            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
                val headerValue = if (apiKeyHeader.equals("Authorization", ignoreCase = true)) "Bearer $apiKey" else apiKey
                setRequestProperty(apiKeyHeader, headerValue)
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return ModelDiscoveryResult(
                    providerId = providerId,
                    online = false,
                    models = emptyList(),
                    failureReason = "HTTP $code from $endpoint"
                )
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsedModels = parseOpenAiModelsJson(body)
            ModelDiscoveryResult(
                providerId = providerId,
                online = parsedModels.isNotEmpty(),
                models = parsedModels,
                failureReason = if (parsedModels.isEmpty()) "empty model list from $endpoint" else null
            )
        } catch (e: Exception) {
            ModelDiscoveryResult(
                providerId = providerId,
                online = false,
                models = emptyList(),
                failureReason = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun parseOpenAiModelsJson(body: String): List<DiscoveredModel> {
        val result = mutableListOf<DiscoveredModel>()
        try {
            val json = JSONObject(body)
            val dataArr = json.optJSONArray("data")
            if (dataArr != null) {
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.opt(i)
                    if (item is JSONObject) {
                        val id = item.optString("id", "").ifBlank { item.optString("name", "") }.trim()
                        if (id.isNotBlank()) {
                            val active = item.optBoolean("active", true)
                            val ownedBy = item.optString("owned_by", "").takeIf(String::isNotBlank)
                            result.add(DiscoveredModel(id, ownedBy, active))
                        }
                    } else if (item is String && item.isNotBlank()) {
                        result.add(DiscoveredModel(item.trim()))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun pollAnthropic(apiKey: String, timeoutMs: Long): ModelDiscoveryResult {
        return try {
            val uri = URI.create("https://api.anthropic.com/v1/models")
            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                return ModelDiscoveryResult("anthropic", false, emptyList(), "HTTP $code")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val models = parseOpenAiModelsJson(body)
            ModelDiscoveryResult("anthropic", models.isNotEmpty(), models)
        } catch (e: Exception) {
            ModelDiscoveryResult("anthropic", false, emptyList(), "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun pollOllama(timeoutMs: Long): ModelDiscoveryResult {
        return try {
            val uri = URI.create("http://localhost:11434/api/tags")
            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                return ModelDiscoveryResult("ollama", false, emptyList(), "HTTP $code")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val modelsArr = json.optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<DiscoveredModel>()
            for (i in 0 until modelsArr.length()) {
                val item = modelsArr.optJSONObject(i) ?: continue
                val name = item.optString("name", "").trim()
                if (name.isNotBlank()) list.add(DiscoveredModel(name))
            }
            ModelDiscoveryResult("ollama", list.isNotEmpty(), list)
        } catch (e: Exception) {
            ModelDiscoveryResult("ollama", false, emptyList(), "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun resolveKey(providerId: String, envName: String? = null): String? {
        val clean = providerId.trim().lowercase(Locale.US)
        FallbackChainRegistry.getActiveKey(clean)?.takeIf { it.isNotBlank() }?.let { return it }
        ProviderKeyRegistry.getActiveKey(clean)?.takeIf { it.isNotBlank() }?.let { return it }
        val targetEnv = envName ?: when (clean) {
            "groq" -> "GROQ_API_KEY"
            "openai" -> "OPENAI_API_KEY"
            "anthropic" -> "ANTHROPIC_API_KEY"
            "xai" -> "XAI_API_KEY"
            "openrouter" -> "OPENROUTER_API_KEY"
            "together" -> "TOGETHER_API_KEY"
            "fireworks" -> "FIREWORKS_API_KEY"
            "mistral" -> "MISTRAL_API_KEY"
            "deepseek_direct", "deepseek" -> "DEEPSEEK_API_KEY"
            "cerebras" -> "CEREBRAS_API_KEY"
            "sambanova" -> "SAMBANOVA_API_KEY"
            else -> "${clean.uppercase(Locale.US)}_API_KEY"
        }
        System.getenv(targetEnv)?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            CredentialBootstrap.get().get(targetEnv)
                ?: TokenIsolationVault(AtroposConfig.configRoot().resolve("secrets")).readSecret(targetEnv)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun resolveModel(providerId: String, requestedOrAlias: String): String {
        val (cleanProvider, cleanModel) = if (requestedOrAlias.contains(":")) {
            val p = requestedOrAlias.substringBefore(":").trim().lowercase(Locale.US)
            val m = requestedOrAlias.substringAfter(":").trim()
            p to m
        } else {
            providerId.trim().lowercase(Locale.US) to requestedOrAlias.trim()
        }

        val live = discoveredModels[cleanProvider].orEmpty().filter { it.active }.map { it.id }

        // 1. If exact live match exists, return it
        if (cleanModel.isNotBlank() && cleanModel != "default" && live.contains(cleanModel)) {
            return cleanModel
        }

        // 2. Check alias & family fallbacks
        val lowerReq = cleanModel.lowercase(Locale.US)
        val aliasCandidates = FAMILY_FALLBACKS[lowerReq]
            ?: FAMILY_FALLBACKS.entries.firstOrNull { lowerReq.contains(it.key) || it.key.contains(lowerReq) }?.value

        if (aliasCandidates != null && live.isNotEmpty()) {
            val matched = aliasCandidates.firstOrNull { cand -> live.any { it.equals(cand, ignoreCase = true) } }
            if (matched != null) return matched
        }

        // 3. Fuzzy search in live models
        if (live.isNotEmpty()) {
            val nonSpecialized = live.filterNot { isSpecializedModel(it) }
            val pool = if (nonSpecialized.isNotEmpty()) nonSpecialized else live

            if (lowerReq.isNotBlank() && lowerReq != "default") {
                val tokens = lowerReq.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
                val best = pool.maxByOrNull { cand ->
                    val candLower = cand.lowercase(Locale.US)
                    var score = 0
                    for (t in tokens) {
                        if (candLower.contains(t)) score += 2
                    }
                    if (candLower.contains("versatile") || candLower.contains("chat") || candLower.contains("instruct")) {
                        score += 1
                    }
                    score
                }
                if (best != null) return best
            }

            // Default to highest quality live chat model
            val preferred = pool.firstOrNull { it.contains("versatile") || it.contains("instruct") || it.contains("chat") }
            if (preferred != null) return preferred
            return pool.first()
        }

        // 4. If no live models were discovered (offline/mock), fallback to alias candidate or catalog default
        if (aliasCandidates != null && aliasCandidates.isNotEmpty()) {
            return aliasCandidates.first()
        }
        if (cleanProvider == "groq" && (lowerReq.contains("whisper") || lowerReq.contains("instant") || lowerReq == "default")) {
            return "llama-3.3-70b-versatile"
        }
        return if (cleanModel.isNotBlank() && cleanModel != "default") cleanModel else "llama-3.3-70b-versatile"
    }

    private fun isSpecializedModel(id: String): Boolean {
        val lower = id.lowercase(Locale.US)
        return lower.contains("guard") ||
            lower.contains("whisper") ||
            lower.contains("vision") ||
            lower.contains("embed") ||
            lower.contains("moderation") ||
            lower.contains("tts")
    }

    fun resetState() {
        discoveredModels.clear()
        prunedProviders.clear()
        pruneReasons.clear()
        isPolled.set(false)
        lastPollEpochMs.set(0L)
    }
}
