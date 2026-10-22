/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.queue

import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONArray
import org.json.JSONObject
import atropos.core.integration.McpEvidenceAndAuthorityPolicy

enum class InterruptMode {
    SOFT_TURN_HALT,
    HARD_PROCESS_KILL,
    DURABLE_QUEUE_FREEZE
}

data class QueueFreezeState(
    val isFrozen: Boolean,
    val frozenAtTimestamp: Long,
    val frozenReason: String?,
    val activeEntryIds: List<String>
)

class QueueInterruptCoordinator(
    val repoRoot: Path,
    val ledgerPolicy: McpEvidenceAndAuthorityPolicy = McpEvidenceAndAuthorityPolicy(repoRoot)
) {
    private val freezeFile = repoRoot.resolve(".atropos/queue/freeze_state.json")

    init {
        Files.createDirectories(freezeFile.parent)
    }

    fun executeSoftInterrupt(entryId: String, actorId: String): Boolean {
        ledgerPolicy.appendToLedger(
            toolName = "queue_soft_interrupt",
            callerId = actorId,
            payload = """{"action":"SOFT_TURN_HALT","entryId":"$entryId"}"""
        )
        return true
    }

    fun executeHardInterrupt(entryId: String, actorId: String): Boolean {
        ledgerPolicy.appendToLedger(
            toolName = "queue_hard_interrupt",
            callerId = actorId,
            payload = """{"action":"HARD_PROCESS_KILL","entryId":"$entryId"}"""
        )
        return true
    }

    fun setQueueFreeze(frozen: Boolean, reason: String?, actorId: String): QueueFreezeState {
        val current = readFreezeState()
        val newState = QueueFreezeState(
            isFrozen = frozen,
            frozenAtTimestamp = if (frozen) System.currentTimeMillis() else 0L,
            frozenReason = reason,
            activeEntryIds = current.activeEntryIds
        )
        val json = JSONObject().apply {
            put("isFrozen", newState.isFrozen)
            put("frozenAtTimestamp", newState.frozenAtTimestamp)
            put("frozenReason", newState.frozenReason ?: JSONObject.NULL)
            put("activeEntryIds", JSONArray(newState.activeEntryIds))
        }
        Files.writeString(freezeFile, json.toString(2))
        ledgerPolicy.appendToLedger(
            toolName = "queue_freeze_state",
            callerId = actorId,
            payload = json.toString()
        )
        return newState
    }

    fun readFreezeState(): QueueFreezeState {
        if (!Files.exists(freezeFile)) {
            return QueueFreezeState(false, 0L, null, emptyList())
        }
        return try {
            val raw = Files.readString(freezeFile)
            val json = JSONObject(raw)
            val entries = mutableListOf<String>()
            val arr = json.optJSONArray("activeEntryIds")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    entries.add(arr.getString(i))
                }
            }
            QueueFreezeState(
                isFrozen = json.optBoolean("isFrozen", false),
                frozenAtTimestamp = json.optLong("frozenAtTimestamp", 0L),
                frozenReason = if (json.isNull("frozenReason")) null else json.optString("frozenReason"),
                activeEntryIds = entries
            )
        } catch (_: Exception) {
            QueueFreezeState(false, 0L, null, emptyList())
        }
    }
}
