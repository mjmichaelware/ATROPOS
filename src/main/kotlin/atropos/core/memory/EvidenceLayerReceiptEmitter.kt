/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.memory

import java.io.File
import java.security.MessageDigest

/**
 * ATOM-MEM-025: Authoritative Evidence Layer Receipt Emitter.
 *
 * Implements tamper-evident logging to cas_evidence.jsonl for:
 * - Configuration & settings mutations.
 * - Keybinding and macro updates.
 * - Usage-frequency ranking mutations.
 * - Shell and mutation policy evaluations.
 *
 * Schema: timestamp, sha256, action, actor, surface, before, after.
 */
class EvidenceLayerReceiptEmitter(
    private val memoryLattice: MemoryLayerLattice = MemoryLayerLattice()
) {
    data class EvidenceReceipt(
        val timestamp: Long,
        val sha256: String,
        val action: String,
        val actor: String,
        val surface: String,
        val before: String,
        val after: String
    ) {
        fun toJson(): String =
            "{\"timestamp\":$timestamp,\"sha256\":\"$sha256\",\"action\":\"$action\"," +
            "\"actor\":\"$actor\",\"surface\":\"$surface\",\"before\":\"$before\",\"after\":\"$after\"}"
    }

    companion object {
        fun sha256(payload: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(payload.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    fun emitReceipt(
        action: String,
        actor: String,
        surface: String,
        before: String,
        after: String
    ): EvidenceReceipt {
        val timestamp = System.currentTimeMillis()
        val payload = "$timestamp:$action:$actor:$surface:$before:$after"
        val hash = sha256(payload)
        val receipt = EvidenceReceipt(
            timestamp = timestamp,
            sha256 = hash,
            action = action,
            actor = actor,
            surface = surface,
            before = before,
            after = after
        )

        memoryLattice.put(
            layer = MemoryLayerLattice.LayerType.EVIDENCE_LAYER,
            key = "${action}_$timestamp",
            content = receipt.toJson()
        )
        return receipt
    }

    fun emitSettingReceipt(key: String, before: String, after: String, actor: String = "operator", surface: String = "CLI"): EvidenceReceipt =
        emitReceipt(
            action = "SETTING_MUTATION",
            actor = actor,
            surface = surface,
            before = "$key=$before",
            after = "$key=$after"
        )

    fun emitKeybindReceipt(macro: String, before: String, after: String, actor: String = "operator", surface: String = "CLI"): EvidenceReceipt =
        emitReceipt(
            action = "KEYBIND_MUTATION",
            actor = actor,
            surface = surface,
            before = "$macro=$before",
            after = "$macro=$after"
        )

    fun emitRankingReceipt(command: String, before: Int, after: Int, actor: String = "CommandFrequencyStore", surface: String = "CLI"): EvidenceReceipt =
        emitReceipt(
            action = "RANKING_MUTATION",
            actor = actor,
            surface = surface,
            before = "$command=$before",
            after = "$command=$after"
        )

    fun emitPolicyReceipt(command: String, decision: String, actor: String = "PolicyGate", surface: String = "CLI"): EvidenceReceipt =
        emitReceipt(
            action = "POLICY_EVALUATION",
            actor = actor,
            surface = surface,
            before = command,
            after = decision
        )

    fun auditReceipts(): List<EvidenceReceipt> {
        val baseDir = MemoryLayerLattice.defaultBaseDirectory()
        val evidenceFile = baseDir.resolve("evidence_layer").resolve("cas_evidence.jsonl").toFile()
        if (!evidenceFile.exists()) return emptyList()

        return evidenceFile.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseReceipt(line) }
    }

    private fun parseReceipt(line: String): EvidenceReceipt? {
        val ts = Regex("\"timestamp\":(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val hash = Regex("\"sha256\":\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return null
        val action = Regex("\"action\":\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return null
        val actor = Regex("\"actor\":\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "unknown"
        val surface = Regex("\"surface\":\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "CLI"
        val before = Regex("\"before\":\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
        val after = Regex("\"after\":\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""

        return EvidenceReceipt(ts, hash, action, actor, surface, before, after)
    }
}
