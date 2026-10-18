/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.memory.MemoryLayerLattice
import atropos.core.thinking.Thinking
import org.json.JSONObject
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class ProviderMistakeRecord(
    val provider: String,
    val errorSignature: String,
    val exactMistake: String,
    val strikeCount: Int = 1,
    val firstSeenEpochMs: Long = System.currentTimeMillis(),
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val promotedToLongTerm: Boolean = false
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("provider", provider)
        obj.put("errorSignature", errorSignature)
        obj.put("exactMistake", exactMistake)
        obj.put("strikeCount", strikeCount)
        obj.put("firstSeenEpochMs", firstSeenEpochMs)
        obj.put("lastSeenEpochMs", lastSeenEpochMs)
        obj.put("promotedToLongTerm", promotedToLongTerm)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ProviderMistakeRecord? {
            return try {
                val obj = JSONObject(jsonStr)
                ProviderMistakeRecord(
                    provider = obj.getString("provider"),
                    errorSignature = obj.getString("errorSignature"),
                    exactMistake = obj.getString("exactMistake"),
                    strikeCount = obj.optInt("strikeCount", 1),
                    firstSeenEpochMs = obj.optLong("firstSeenEpochMs", System.currentTimeMillis()),
                    lastSeenEpochMs = obj.optLong("lastSeenEpochMs", System.currentTimeMillis()),
                    promotedToLongTerm = obj.optBoolean("promotedToLongTerm", false)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Short-Term Memory & Long-Term Memory Escalation Engine (2-Strike Rule).
 *
 * Tracks provider imperfections, syntax violations, and patch failures immediately
 * in session Short-Term Memory (TEMPORARY_MEMORY). When the exact same mistake occurs
 * twice for a provider, automatically escalates the record into permanent Long-Term
 * Memory (KNOWLEDGE_LAYER & AUTHORITY_AND_POLICY) to enforce durable constraints.
 */
class ProviderFailureMemoryStore(
    private val lattice: MemoryLayerLattice = MemoryLayerLattice()
) {
    private val sessionShortTermMemory = ConcurrentHashMap<String, ProviderMistakeRecord>()

    fun recordMistake(
        provider: String,
        errorType: String,
        exactMistake: String
    ): ProviderMistakeRecord {
        val cleanProvider = provider.trim().lowercase()
        val signature = computeErrorSignature(errorType, exactMistake)
        val stmKey = "stm:provider:$cleanProvider:$signature"

        val existing = sessionShortTermMemory[stmKey]

        return if (existing != null) {
            // Second strike of the exact same mistake: escalate to Long-Term Memory
            val escalated = existing.copy(
                strikeCount = existing.strikeCount + 1,
                lastSeenEpochMs = System.currentTimeMillis(),
                promotedToLongTerm = true
            )
            sessionShortTermMemory[stmKey] = escalated

            // Update in lattice Short-Term Memory
            lattice.put(MemoryLayerLattice.LayerType.TEMPORARY_MEMORY, stmKey, escalated.toJson())

            // 2-Strike Rule Promotion into Long-Term Memory
            val ltmKey = "ltm:constraint:$cleanProvider:$signature"
            val constraintDirective = buildConstraintDirective(cleanProvider, signature, exactMistake)
            val ltmPayload = JSONObject().apply {
                put("provider", cleanProvider)
                put("errorSignature", signature)
                put("exactMistake", exactMistake)
                put("strikeCount", escalated.strikeCount)
                put("promotedAtEpochMs", System.currentTimeMillis())
                put("constraintDirective", constraintDirective)
            }.toString()

            lattice.put(MemoryLayerLattice.LayerType.KNOWLEDGE_LAYER, ltmKey, ltmPayload)
            lattice.put(MemoryLayerLattice.LayerType.AUTHORITY_AND_POLICY, ltmKey, ltmPayload)

            Thinking.outline("memory", "2-STRIKE PROMOTION: Provider [$cleanProvider] repeated mistake [$signature]")
            Thinking.step("memory", "Escalated mistake to permanent Long-Term Memory constraint: $constraintDirective")

            escalated
        } else {
            // First strike: record in session Short-Term Memory
            val newRecord = ProviderMistakeRecord(
                provider = cleanProvider,
                errorSignature = signature,
                exactMistake = exactMistake,
                strikeCount = 1,
                firstSeenEpochMs = System.currentTimeMillis(),
                lastSeenEpochMs = System.currentTimeMillis(),
                promotedToLongTerm = false
            )
            sessionShortTermMemory[stmKey] = newRecord
            lattice.put(MemoryLayerLattice.LayerType.TEMPORARY_MEMORY, stmKey, newRecord.toJson())

            Thinking.detail("memory", "Recorded failure in session Short-Term Memory for [$cleanProvider]: $signature (strike 1/2)")
            newRecord
        }
    }

    fun getPermanentConstraints(provider: String): List<String> {
        val cleanProvider = provider.trim().lowercase()
        val constraints = mutableListOf<String>()

        // 1. From active promoted in-memory set
        sessionShortTermMemory.values
            .filter { it.provider == cleanProvider && it.promotedToLongTerm }
            .forEach { mistake ->
                constraints.add(buildConstraintDirective(cleanProvider, mistake.errorSignature, mistake.exactMistake))
            }

        // 2. From persistent Knowledge/Authority directory
        val knowledgeDir = MemoryLayerLattice.defaultBaseDirectory().resolve("knowledge").toFile()
        if (knowledgeDir.exists() && knowledgeDir.isDirectory) {
            knowledgeDir.listFiles { _, name -> name.contains(cleanProvider) }?.forEach { file ->
                runCatching {
                    val content = file.readText()
                    if (content.contains("constraintDirective")) {
                        val obj = JSONObject(content)
                        val directive = obj.optString("constraintDirective")
                        if (directive.isNotBlank() && directive !in constraints) {
                            constraints.add(directive)
                        }
                    }
                }
            }
        }

        return constraints
    }

    fun getAllPermanentConstraints(): List<String> {
        val all = mutableListOf<String>()
        sessionShortTermMemory.values
            .filter { it.promotedToLongTerm }
            .forEach { mistake ->
                all.add(buildConstraintDirective(mistake.provider, mistake.errorSignature, mistake.exactMistake))
            }
        return all.distinct()
    }

    fun getShortTermMistakes(provider: String): List<ProviderMistakeRecord> {
        val cleanProvider = provider.trim().lowercase()
        return sessionShortTermMemory.values.filter { it.provider == cleanProvider }.toList()
    }

    fun clearSessionShortTermMemory() {
        sessionShortTermMemory.clear()
    }

    private fun computeErrorSignature(errorType: String, exactMistake: String): String {
        val cleanType = errorType.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
        val cleanMistake = exactMistake.trim()
            .replace(Regex("\\s+"), " ")
            .take(60)
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .lowercase()
        return "${cleanType}_$cleanMistake"
    }

    private fun buildConstraintDirective(provider: String, signature: String, mistake: String): String {
        return "CRITICAL LONG-TERM CONSTRAINT ($provider): Do not repeat failure '$signature'. Exact violation: ${mistake.take(160)}. Enforce strict syntax and complete executable code blocks."
    }

    companion object {
        val SHARED: ProviderFailureMemoryStore by lazy {
            ProviderFailureMemoryStore()
        }
    }
}
