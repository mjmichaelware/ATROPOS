/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Provider API key registry managing multi-key JSON arrays, dead key tracking,
 * real-time rotation, and exhaustive pipeline retry.
 */
object ProviderKeyRegistry {

    private val configuredKeyArrays = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val activeKeyIndices = ConcurrentHashMap<String, AtomicInteger>()
    private val deadKeySet = ConcurrentHashMap.newKeySet<String>()

    /** Register an array of keys for a given provider. */
    fun registerKeyArray(providerId: String, keys: List<String>) {
        val cleanId = providerId.trim().lowercase()
        val validKeys = keys.map(String::trim).filter { it.isNotBlank() }
        if (validKeys.isEmpty()) return
        val list = configuredKeyArrays.computeIfAbsent(cleanId) { CopyOnWriteArrayList() }
        for (k in validKeys) {
            if (!list.contains(k)) list.add(k)
        }
        activeKeyIndices.putIfAbsent(cleanId, AtomicInteger(0))
    }

    /** Load configured key arrays from JSON content. */
    fun loadKeysFromJson(jsonString: String) {
        if (jsonString.isBlank()) return
        try {
            val json = JSONObject(jsonString)
            if (json.has("keys") && json.optJSONObject("keys") != null) {
                val keysObj = json.getJSONObject("keys")
                for (provider in keysObj.keySet()) {
                    extractKeysFromValue(provider, keysObj.opt(provider))
                }
            }
            for (key in json.keySet()) {
                val lower = key.lowercase()
                val providerName = when {
                    lower.endsWith("_api_keys") -> lower.substringBefore("_api_keys")
                    lower.endsWith("_keys") -> lower.substringBefore("_keys")
                    lower.endsWith("_api_key") -> lower.substringBefore("_api_key")
                    lower.endsWith("_key") -> lower.substringBefore("_key")
                    else -> null
                }
                if (providerName != null) {
                    extractKeysFromValue(providerName, json.get(key))
                }
            }
        } catch (_: Exception) {
            // Soft-fail: invalid JSON fragment does not crash key configuration loader
        }
    }

    private fun extractKeysFromValue(provider: String, value: Any?) {
        when (value) {
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until value.length()) {
                    val s = value.optString(i, "").trim()
                    if (s.isNotBlank()) list.add(s)
                }
                registerKeyArray(provider, list)
            }
            is String -> {
                val trimmed = value.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    try {
                        val arr = JSONArray(trimmed)
                        extractKeysFromValue(provider, arr)
                    } catch (_: Exception) {
                        registerKeyArray(provider, listOf(trimmed))
                    }
                } else if (trimmed.contains(",")) {
                    registerKeyArray(provider, trimmed.split(",").map(String::trim).filter(String::isNotBlank))
                } else if (trimmed.isNotBlank()) {
                    registerKeyArray(provider, listOf(trimmed))
                }
            }
        }
    }

    /** Load keys from environment and credential vault singleton. */
    fun loadFromEnvironmentAndVault() {
        val env = System.getenv()
        for ((k, v) in env) {
            val lower = k.lowercase()
            val providerName = when {
                lower.endsWith("_api_keys") -> lower.substringBefore("_api_keys")
                lower.endsWith("_keys") -> lower.substringBefore("_keys")
                lower.endsWith("_api_key") -> lower.substringBefore("_api_key")
                lower.endsWith("_key") -> lower.substringBefore("_key")
                else -> null
            }
            if (providerName != null && !v.isNullOrBlank()) {
                extractKeysFromValue(providerName, v)
            }
        }
        try {
            val config = atropos.core.AtroposConfig.load()
            if (config.keys.groq.isNotBlank()) extractKeysFromValue("groq", config.keys.groq)
            if (config.keys.openai.isNotBlank()) extractKeysFromValue("openai", config.keys.openai)
            if (config.keys.anthropic.isNotBlank()) extractKeysFromValue("anthropic", config.keys.anthropic)
            if (config.keys.xai.isNotBlank()) extractKeysFromValue("xai", config.keys.xai)
        } catch (_: Exception) {
            // Soft-fail: config file unreadable or not yet created
        }
        try {
            val vaultCreds = atropos.core.security.CredentialBootstrap.get().getAll()
            for ((k, v) in vaultCreds) {
                val lower = k.lowercase()
                val providerName = when {
                    lower.endsWith("_api_keys") -> lower.substringBefore("_api_keys")
                    lower.endsWith("_keys") -> lower.substringBefore("_keys")
                    lower.endsWith("_api_key") -> lower.substringBefore("_api_key")
                    lower.endsWith("_key") -> lower.substringBefore("_key")
                    else -> lower
                }
                if (v.isNotBlank()) extractKeysFromValue(providerName, v)
            }
        } catch (_: Exception) {
            // Soft-fail: vault loading if not yet initialized
        }
    }

    /** Get the currently active key for a provider, automatically advancing past dead keys. */
    fun getActiveKey(providerId: String): String? {
        val cleanId = providerId.trim().lowercase()
        val keys = configuredKeyArrays[cleanId] ?: return null
        if (keys.isEmpty()) return null
        val idx = activeKeyIndices.computeIfAbsent(cleanId) { AtomicInteger(0) }
        var currentIdx = idx.get()

        while (currentIdx < keys.size) {
            val candidate = keys[currentIdx]
            if (candidate !in deadKeySet) return candidate
            currentIdx = idx.incrementAndGet()
        }
        return null
    }

    /** Mark a key as dead in the registry. */
    fun markKeyDead(providerId: String, key: String) {
        val cleanKey = key.trim()
        if (cleanKey.isNotBlank()) {
            deadKeySet.add(cleanKey)
            deadKeySet.add("${providerId.trim().lowercase()}:$cleanKey")
        }
    }

    /** Shift to the next available non-dead key for a provider. */
    fun rotateKey(providerId: String): String? {
        val cleanId = providerId.trim().lowercase()
        val keys = configuredKeyArrays[cleanId] ?: return null
        val idx = activeKeyIndices.computeIfAbsent(cleanId) { AtomicInteger(0) }
        var next = idx.incrementAndGet()
        while (next < keys.size) {
            val candidate = keys[next]
            if (candidate !in deadKeySet) return candidate
            next = idx.incrementAndGet()
        }
        return null
    }

    /** Check if provider has another valid non-dead key available. */
    fun hasMoreKeys(providerId: String): Boolean {
        val cleanId = providerId.trim().lowercase()
        val keys = configuredKeyArrays[cleanId] ?: return false
        val idx = activeKeyIndices.computeIfAbsent(cleanId) { AtomicInteger(0) }.get()
        return (idx + 1 until keys.size).any { keys[it] !in deadKeySet }
    }

    /** Check if all configured keys for this provider are exhausted or dead. */
    fun areKeysExhausted(providerId: String): Boolean = getActiveKey(providerId) == null

    /** Check if all configured keys across all specified providers are exhausted. */
    fun isPipelineExhausted(providers: List<String>): Boolean =
        providers.all { areKeysExhausted(it) }

    /** Snapshot of all active keys. */
    fun getAllActiveKeys(): Map<String, String> = buildMap {
        for (providerId in configuredKeyArrays.keys) {
            getActiveKey(providerId)?.let { put(providerId, it) }
        }
    }

    /** Reset state for tests or fresh sessions. */
    fun resetState() {
        configuredKeyArrays.clear()
        activeKeyIndices.clear()
        deadKeySet.clear()
    }
}
