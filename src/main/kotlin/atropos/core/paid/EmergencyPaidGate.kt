package atropos.core.paid

import java.io.File
import java.util.Locale

data class PaidUnlock(
    val providerId: String,
    val reason: String,
    val unlockedAtEpochMs: Long,
    val expiresAtEpochMs: Long
) {
    fun isActive(now: Long): Boolean = now < expiresAtEpochMs
}

data class PaidGateStatus(
    val active: PaidUnlock?,
    val locked: Boolean,
    val knownPaidProviders: List<String>,
    val auditFile: File
)

class EmergencyPaidGate(
    private val root: File = File(".atropos/paid"),
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val stateFile = File(root, "paid-unlock.state")
    private val auditFile = File(root, "paid-audit.jsonl")
    private val paidProviders = listOf("anthropic", "openai", "xai", "mistral", "cohere", "deepseek_direct")

    fun status(): PaidGateStatus {
        root.mkdirs()
        val active = readActive()
        return PaidGateStatus(
            active = active,
            locked = active == null,
            knownPaidProviders = paidProviders,
            auditFile = auditFile
        )
    }

    fun unlock(providerId: String, durationText: String, reason: String): PaidUnlock {
        val provider = providerId.lowercase(Locale.US).trim()
        require(provider in paidProviders) { "unknown paid provider: $providerId" }
        val durationMs = parseDurationMs(durationText)
        require(durationMs in 60_000L..7_200_000L) { "duration must be between 1m and 120m" }
        val cleanReason = reason.trim().ifBlank { "no reason supplied" }.take(240)
        root.mkdirs()
        val unlock = PaidUnlock(
            providerId = provider,
            reason = cleanReason,
            unlockedAtEpochMs = now(),
            expiresAtEpochMs = now() + durationMs
        )
        stateFile.writeText(encode(unlock))
        audit("unlock", unlock)
        return unlock
    }

    fun lock(): Boolean {
        root.mkdirs()
        val prior = readActive()
        if (stateFile.exists()) stateFile.delete()
        audit("lock", prior)
        return prior != null
    }

    fun isProviderUnlocked(providerId: String): Boolean {
        val active = readActive() ?: return false
        return active.providerId == providerId.lowercase(Locale.US) && active.isActive(now())
    }

    private fun readActive(): PaidUnlock? {
        if (!stateFile.exists()) return null
        val unlock = decode(stateFile.readText()) ?: return null
        if (!unlock.isActive(now())) {
            audit("expired", unlock)
            stateFile.delete()
            return null
        }
        return unlock
    }

    private fun parseDurationMs(value: String): Long {
        val text = value.trim().lowercase(Locale.US)
        val amount = text.dropLast(1).toLongOrNull() ?: return 0L
        return when (text.lastOrNull()) {
            'm' -> amount * 60_000L
            'h' -> amount * 3_600_000L
            else -> 0L
        }
    }

    private fun audit(action: String, unlock: PaidUnlock?) {
        root.mkdirs()
        val provider = unlock?.providerId ?: "none"
        val expires = unlock?.expiresAtEpochMs ?: 0L
        val reason = escape(unlock?.reason ?: "")
        auditFile.appendText("""{"at":${now()},"action":"$action","provider":"$provider","expiresAt":$expires,"reason":"$reason"}""" + "\n")
    }

    private fun encode(unlock: PaidUnlock): String {
        return listOf(
            unlock.providerId,
            unlock.unlockedAtEpochMs.toString(),
            unlock.expiresAtEpochMs.toString(),
            escape(unlock.reason)
        ).joinToString("\t")
    }

    private fun decode(value: String): PaidUnlock? {
        val parts = value.trim().split("\t", limit = 4)
        if (parts.size < 4) return null
        return PaidUnlock(
            providerId = parts[0],
            unlockedAtEpochMs = parts[1].toLongOrNull() ?: return null,
            expiresAtEpochMs = parts[2].toLongOrNull() ?: return null,
            reason = unescape(parts[3])
        )
    }

    private fun escape(value: String): String {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\"", "\\\"")
    }

    private fun unescape(value: String): String {
        return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
