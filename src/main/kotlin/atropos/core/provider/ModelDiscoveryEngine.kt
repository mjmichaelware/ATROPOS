/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.provider.adapter.OpenAiCompatibleProviderCatalog
import atropos.core.security.CredentialBootstrap
import atropos.core.security.TokenIsolationVault
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DiscoveredModel(val id: String, val ownedBy: String? = null, val active: Boolean = true)
data class ModelDiscoveryResult(val providerId: String, val online: Boolean, val models: List<DiscoveredModel>, val failureReason: String? = null)

/**
 * Dynamic Model Discovery Engine.
 * Queries live /v1/models, Gemini REST, and Ollama tags on boot.
 * Eliminates 404s and deprecated models, storing live IDs in atropos.config.json.
 */
object ModelDiscoveryEngine {
    private val discoveredModels = ConcurrentHashMap<String, List<DiscoveredModel>>()
    private val activePrimaryModel = ConcurrentHashMap<String, String>()
    private val prunedProviders = ConcurrentHashMap.newKeySet<String>()
    private val pruneReasons = ConcurrentHashMap<String, String>()
    private val isPolled = AtomicBoolean(false)
    private val lastPollEpochMs = AtomicLong(0L)

    val NON_LLM_TOKENS = setOf(
        "npm_token", "fal_ai", "fal", "serp", "serpapi", "github_token",
        "vercel_token", "atropos_bridge_port", "npm", "vercel", "bridge"
    )

    fun isPruned(providerId: String): Boolean {
        val clean = providerId.trim().lowercase(Locale.US)
        if (clean.isBlank() || isNonLlm(clean)) return true
        if (!hasCredentials(clean)) return true
        return prunedProviders.contains(clean)
    }

    fun isNonLlm(providerId: String): Boolean {
        val clean = providerId.trim().lowercase(Locale.US)
        return NON_LLM_TOKENS.contains(clean) || NON_LLM_TOKENS.any { clean.contains(it) }
    }

    fun pruneReason(providerId: String): String? = pruneReasons[providerId.trim().lowercase(Locale.US)]

    fun prune(providerId: String, reason: String) {
        val clean = providerId.trim().lowercase(Locale.US)
        prunedProviders.add(clean)
        pruneReasons[clean] = reason
        discoveredModels.remove(clean)
        activePrimaryModel.remove(clean)
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

    fun getCachedModel(providerId: String): String? {
        val clean = providerId.trim().lowercase(Locale.US)
        activePrimaryModel[clean]?.let { return it }
        return readDiscoveredModelsConfig()[clean]
    }

    fun filterActiveCascade(candidates: List<String>): List<String> {
        val unpruned = candidates.filterNot { isPruned(it) || isNonLlm(it) }
        return if (unpruned.isEmpty() && candidates.isNotEmpty()) candidates else unpruned
    }

    fun hasCredentials(providerId: String): Boolean {
        val clean = providerId.trim().lowercase(Locale.US)
        if (isNonLlm(clean)) return false
        if (clean in setOf("ollama", "local", "localtoolchain")) return true
        val spec = OpenAiCompatibleProviderCatalog.get(clean)
        val envKey = spec?.apiKeyEnv
        if (envKey != null && !resolveKey(clean, envKey).isNullOrBlank()) return true
        return FallbackChainRegistry.getActiveKey(clean) != null || !resolveKey(clean, "${clean.uppercase(Locale.US)}_API_KEY").isNullOrBlank()
    }

    fun resolveActiveProvider(defaultProvider: String): String {
        val cleanDefault = defaultProvider.trim().lowercase(Locale.US)
        if (cleanDefault.isNotBlank() && cleanDefault != "default" && !isPruned(cleanDefault) && hasCredentials(cleanDefault)) return cleanDefault
        val healthy = discoveredModels.keys().toList().filterNot { prunedProviders.contains(it) || isNonLlm(it) }
        if (healthy.isNotEmpty()) return healthy.first()
        val preferred = listOf("groq", "cerebras", "sambanova", "deepseek", "gemini", "openrouter", "openai", "anthropic", "ollama")
        for (cand in preferred) if (!isPruned(cand) && hasCredentials(cand)) return cand
        for (cand in preferred) if (hasCredentials(cand) && !isNonLlm(cand)) return cand
        return if (cleanDefault.isNotBlank() && !isNonLlm(cleanDefault)) cleanDefault else "groq"
    }

    fun ensureDiscovered(timeoutMs: Long = 3000L) {
        val now = System.currentTimeMillis()
        if (!isPolled.get() || (now - lastPollEpochMs.get() > 300_000L)) discoverAndPrune(timeoutMs)
    }

    fun discoverAndPrune(timeoutMs: Long = 3000L): Map<String, ModelDiscoveryResult> {
        val results = mutableMapOf<String, ModelDiscoveryResult>()
        val discoveredCache = mutableMapOf<String, String>()

        for (spec in OpenAiCompatibleProviderCatalog.all()) {
            val providerId = spec.providerId.lowercase(Locale.US)
            if (isNonLlm(providerId)) continue
            val key = resolveKey(providerId, spec.apiKeyEnv)
            if (key.isNullOrBlank()) {
                prune(providerId, "missing credentials (${spec.apiKeyEnv})")
                results[providerId] = ModelDiscoveryResult(providerId, false, emptyList(), "missing credentials")
                continue
            }

            val endpoint = computeModelsEndpoint(spec.baseUrl, spec.endpointEnv)
            val discovery = pollEndpoint(providerId, endpoint, key, spec.apiKeyHeader, spec.headers, timeoutMs)
            results[providerId] = discovery
            if (discovery.online && discovery.models.isNotEmpty()) {
                val usable = discovery.models.filterNot { isSpecializedModel(it.id) }
                discoveredModels[providerId] = if (usable.isNotEmpty()) usable else discovery.models
                unprune(providerId)
                val primary = rankChatModel(providerId, discovery.models.map { it.id })
                if (primary != null) {
                    activePrimaryModel[providerId] = primary
                    discoveredCache[providerId] = primary
                }
            } else {
                prune(providerId, discovery.failureReason ?: "no active models found")
            }
        }

        // Poll Gemini REST
        val geminiKey = resolveKey("gemini", "GEMINI_API_KEY")
        if (!geminiKey.isNullOrBlank()) {
            val geminiResult = pollGemini(geminiKey, timeoutMs)
            results["gemini"] = geminiResult
            if (geminiResult.online && geminiResult.models.isNotEmpty()) {
                discoveredModels["gemini"] = geminiResult.models
                unprune("gemini")
                val selectedFlash = selectActiveGeminiFlashModel(geminiResult.models.map { it.id })
                activePrimaryModel["gemini"] = selectedFlash
                discoveredCache["gemini"] = selectedFlash
            } else {
                prune("gemini", geminiResult.failureReason ?: "gemini REST unreachable")
            }
        } else {
            prune("gemini", "missing GEMINI_API_KEY")
        }

        // Poll Anthropic
        val anthropicKey = resolveKey("anthropic", "ANTHROPIC_API_KEY")
        if (!anthropicKey.isNullOrBlank()) {
            val anthropicResult = pollAnthropic(anthropicKey, timeoutMs)
            results["anthropic"] = anthropicResult
            if (anthropicResult.online && anthropicResult.models.isNotEmpty()) {
                discoveredModels["anthropic"] = anthropicResult.models
                unprune("anthropic")
                rankChatModel("anthropic", anthropicResult.models.map { it.id })?.let {
                    activePrimaryModel["anthropic"] = it
                    discoveredCache["anthropic"] = it
                }
            } else {
                prune("anthropic", anthropicResult.failureReason ?: "anthropic unreachable")
            }
        } else {
            prune("anthropic", "missing ANTHROPIC_API_KEY")
        }

        // Poll Ollama
        val ollamaResult = pollOllama(timeoutMs)
        results["ollama"] = ollamaResult
        if (ollamaResult.online && ollamaResult.models.isNotEmpty()) {
            discoveredModels["ollama"] = ollamaResult.models
            unprune("ollama")
            val primaryOllama = rankChatModel("ollama", ollamaResult.models.map { it.id }) ?: ollamaResult.models.first().id
            activePrimaryModel["ollama"] = primaryOllama
            discoveredCache["ollama"] = primaryOllama
        } else {
            prune("ollama", ollamaResult.failureReason ?: "ollama offline")
        }

        if (discoveredCache.isNotEmpty()) writeDiscoveredModelsConfig(discoveredCache)
        isPolled.set(true)
        lastPollEpochMs.set(System.currentTimeMillis())
        return results
    }

    private fun computeModelsEndpoint(baseUrl: String, endpointEnv: String?): String {
        val override = endpointEnv?.let { System.getenv(it) }?.takeIf { it.isNotBlank() }
        val effective = (override ?: baseUrl).trim()
        return when {
            effective.endsWith("/chat/completions") -> effective.removeSuffix("/chat/completions") + "/models"
            effective.endsWith("/chat/completions/") -> effective.removeSuffix("/chat/completions/") + "/models"
            effective.contains("/v1/") -> effective.substringBefore("/v1/") + "/v1/models"
            effective.endsWith("/v1") -> "$effective/models"
            else -> effective.trimEnd('/') + "/models"
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
            if (code !in 200..299) return ModelDiscoveryResult(providerId, false, emptyList(), "HTTP $code from $endpoint")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsedModels = parseOpenAiModelsJson(body)
            ModelDiscoveryResult(providerId, parsedModels.isNotEmpty(), parsedModels, if (parsedModels.isEmpty()) "empty models from $endpoint" else null)
        } catch (e: Exception) {
            ModelDiscoveryResult(providerId, false, emptyList(), "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun parseOpenAiModelsJson(body: String): List<DiscoveredModel> {
        val result = mutableListOf<DiscoveredModel>()
        try {
            val dataArr = JSONObject(body).optJSONArray("data") ?: return emptyList()
            for (i in 0 until dataArr.length()) {
                val item = dataArr.opt(i)
                if (item is JSONObject) {
                    val id = item.optString("id", "").ifBlank { item.optString("name", "") }.trim()
                    if (id.isNotBlank() && !isSpecializedModel(id)) {
                        result.add(DiscoveredModel(id, item.optString("owned_by", "").takeIf(String::isNotBlank), item.optBoolean("active", true)))
                    }
                } else if (item is String && item.isNotBlank() && !isSpecializedModel(item)) {
                    result.add(DiscoveredModel(item.trim()))
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun pollGemini(apiKey: String, timeoutMs: Long): ModelDiscoveryResult {
        return try {
            val connection = (URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey").toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code !in 200..299) return ModelDiscoveryResult("gemini", false, emptyList(), "HTTP $code from Gemini REST")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val modelsArr = JSONObject(body).optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<DiscoveredModel>()
            for (i in 0 until modelsArr.length()) {
                val item = modelsArr.optJSONObject(i) ?: continue
                val name = item.optString("name", "").removePrefix("models/").trim()
                val methods = item.optJSONArray("supportedGenerationMethods")
                val supportsGen = methods != null && (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                if (name.isNotBlank() && supportsGen && !isSpecializedModel(name)) list.add(DiscoveredModel(name))
            }
            ModelDiscoveryResult("gemini", list.isNotEmpty(), list)
        } catch (e: Exception) {
            ModelDiscoveryResult("gemini", false, emptyList(), "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun selectActiveGeminiFlashModel(models: List<String>): String {
        val flashCandidates = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
        for (cand in flashCandidates) if (models.any { it.equals(cand, ignoreCase = true) }) return cand
        return models.firstOrNull { it.contains("flash", ignoreCase = true) } ?: "gemini-2.5-flash"
    }

    private fun pollOllama(timeoutMs: Long): ModelDiscoveryResult {
        return try {
            val base = (System.getenv("OLLAMA_HOST") ?: "http://127.0.0.1:11434").trimEnd('/')
            val connection = (URI.create("$base/api/tags").toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
                connectTimeout = timeoutMs.toInt()
                readTimeout = 180_000
            }
            val code = connection.responseCode
            if (code !in 200..299) return ModelDiscoveryResult("ollama", false, emptyList(), "HTTP $code")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val modelsArr = JSONObject(body).optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<DiscoveredModel>()
            for (i in 0 until modelsArr.length()) {
                val item = modelsArr.optJSONObject(i) ?: continue
                val name = item.optString("name", "").trim()
                if (name.isNotBlank() && !isSpecializedModel(name)) list.add(DiscoveredModel(name))
            }
            ModelDiscoveryResult("ollama", list.isNotEmpty(), list)
        } catch (e: Exception) {
            ModelDiscoveryResult("ollama", false, emptyList(), "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun pollAnthropic(apiKey: String, timeoutMs: Long): ModelDiscoveryResult {
        return try {
            val connection = (URI.create("https://api.anthropic.com/v1/models").toURL().openConnection() as HttpURLConnection).apply {
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
            if (code !in 200..299) return ModelDiscoveryResult("anthropic", false, emptyList(), "HTTP $code")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val models = parseOpenAiModelsJson(body)
            ModelDiscoveryResult("anthropic", models.isNotEmpty(), models)
        } catch (e: Exception) {
            ModelDiscoveryResult("anthropic", false, emptyList(), "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun rankChatModel(providerId: String, modelIds: List<String>): String? {
        val nonSpecialized = modelIds.filterNot { isSpecializedModel(it) }
        val pool = if (nonSpecialized.isNotEmpty()) nonSpecialized else modelIds
        if (pool.isEmpty()) return null
        return pool.maxByOrNull { id ->
            val lower = id.lowercase(Locale.US)
            var score = 0
            if (lower.contains("coder") || lower.contains("code")) score += 50
            if (lower.contains("70b")) score += 30
            if (lower.contains("r1") || lower.contains("reasoner")) score += 25
            if (lower.contains("32b") || lower.contains("versatile")) score += 20
            if (lower.contains("instruct") || lower.contains("chat")) score += 15
            if (lower.contains("3.3") || lower.contains("2.5")) score += 10
            if (lower.contains("8b")) score += 5
            score
        }
    }

    fun resolveModel(providerId: String, requestedOrAlias: String): String {
        val clean = providerId.trim().lowercase(Locale.US)
        if (requestedOrAlias.isNotBlank() && requestedOrAlias != "default" && !isSpecializedModel(requestedOrAlias)) {
            val live = discoveredModels[clean].orEmpty().map { it.id }
            if (live.contains(requestedOrAlias)) return requestedOrAlias
        }
        getCachedModel(clean)?.let { return it }
        val live = discoveredModels[clean].orEmpty().map { it.id }
        if (live.isNotEmpty()) return rankChatModel(clean, live) ?: live.first()
        return when (clean) {
            "gemini" -> "gemini-2.5-flash"
            "groq" -> "llama-3.3-70b-versatile"
            "cerebras" -> "llama3.1-70b"
            "sambanova" -> "Meta-Llama-3.1-70B-Instruct"
            "deepseek" -> "deepseek-chat"
            "openrouter" -> "meta-llama/llama-3.3-70b-instruct"
            "ollama" -> System.getenv("OLLAMA_MODEL") ?: "llama3.2:1b"
            else -> if (requestedOrAlias.isNotBlank() && requestedOrAlias != "default") requestedOrAlias else "llama-3.3-70b-versatile"
        }
    }

    fun resolveKey(providerId: String, envName: String? = null): String? {
        val clean = providerId.trim().lowercase(Locale.US)
        if (isNonLlm(clean)) return null
        FallbackChainRegistry.getActiveKey(clean)?.takeIf { it.isNotBlank() }?.let { return it }
        ProviderKeyRegistry.getActiveKey(clean)?.takeIf { it.isNotBlank() }?.let { return it }
        val targetEnv = envName ?: when (clean) {
            "groq" -> "GROQ_API_KEY"
            "cerebras" -> "CEREBRAS_API_KEY"
            "sambanova" -> "SAMBANOVA_API_KEY"
            "gemini" -> "GEMINI_API_KEY"
            "openai" -> "OPENAI_API_KEY"
            "anthropic" -> "ANTHROPIC_API_KEY"
            "xai" -> "XAI_API_KEY"
            "openrouter" -> "OPENROUTER_API_KEY"
            "deepseek_direct", "deepseek" -> "DEEPSEEK_API_KEY"
            else -> "${clean.uppercase(Locale.US)}_API_KEY"
        }
        System.getenv(targetEnv)?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            CredentialBootstrap.get().get(targetEnv)
                ?: TokenIsolationVault(AtroposConfig.configRoot().resolve("secrets")).readSecret(targetEnv)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun isSpecializedModel(id: String): Boolean {
        val lower = id.lowercase(Locale.US)
        return lower.contains("guard") || lower.contains("whisper") ||
            lower.contains("embed") || lower.contains("moderation") ||
            lower.contains("tts") || lower.contains("audio") || lower.contains("stt")
    }

    private fun configFile(): File {
        val root = runCatching { AtroposRepoRootLocator.resolve() }.getOrNull()
            ?: Path.of(System.getProperty("user.home")).resolve(".atropos")
        val dir = root.resolve(".atropos").toFile()
        if (!dir.exists()) dir.mkdirs()
        return dir.resolve("atropos.config.json")
    }

    private fun readDiscoveredModelsConfig(): Map<String, String> {
        val file = configFile()
        if (!file.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(file.readText())
            val disc = root.optJSONObject("discovered_models") ?: return emptyMap()
            val result = mutableMapOf<String, String>()
            disc.keys().forEach { k -> result[k] = disc.optString(k) }
            result
        }.getOrDefault(emptyMap())
    }

    private fun writeDiscoveredModelsConfig(models: Map<String, String>) {
        val file = configFile()
        runCatching {
            val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
            val existing = root.optJSONObject("discovered_models") ?: JSONObject()
            models.forEach { (k, v) -> existing.put(k, v) }
            root.put("discovered_models", existing)
            file.writeText(root.toString(2))
        }
    }

    fun resetState() {
        discoveredModels.clear()
        activePrimaryModel.clear()
        prunedProviders.clear()
        pruneReasons.clear()
        isPolled.set(false)
        lastPollEpochMs.set(0L)
    }
}
