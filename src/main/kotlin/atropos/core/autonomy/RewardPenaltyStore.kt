package atropos.core.autonomy

import java.io.File
import java.time.Instant

/**
 * Records reward and penalty signals for agent actions to drive self-improvement.
 *
 * Implements E002: RewardPenaltyStore
 */
class RewardPenaltyStore(
    private val storageDir: File = File(".atropos/autonomy"),
    private val storageFile: File = File(storageDir, "rewards.tsv")
) {
    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        if (!storageFile.exists()) {
            storageFile.writeText("Timestamp\tAgentID\tAction\tType\tValue\tReason\n")
        }
    }

    enum class SignalType { REWARD, PENALTY, ZERO_REWARD_PENALTY }

    companion object {
        const val ZERO_REWARD_PENALTY_ACTION = "ZERO_REWARD_PENALTY"
        const val FRAUD_PENALTY_DEFAULT_VALUE = -100.0
    }

    data class Signal(
        val timestamp: Instant,
        val agentId: String,
        val action: String,
        val type: SignalType,
        val value: Double,
        val reason: String
    )

    fun recordReward(agentId: String, action: String, value: Double, reason: String) {
        recordSignal(agentId, action, SignalType.REWARD, value, reason)
    }

    fun recordPenalty(agentId: String, action: String, value: Double, reason: String) {
        val signalType = if (action == ZERO_REWARD_PENALTY_ACTION) SignalType.ZERO_REWARD_PENALTY else SignalType.PENALTY
        recordSignal(agentId, action, signalType, value, reason)
    }

    /**
     * SD7-R-085: Log ZERO_REWARD_PENALTY on fraudulent AST synthesis attempts.
     */
    fun recordZeroRewardFraudPenalty(agentId: String, reason: String = "fraudulent AST synthesis attempt detected") {
        recordPenalty(agentId, ZERO_REWARD_PENALTY_ACTION, FRAUD_PENALTY_DEFAULT_VALUE, reason)
    }

    /**
     * SD7-T-022: Log ZERO_REWARD_PENALTY on deployment secret leak attempts.
     */
    fun recordZeroRewardPenalty(agentId: String, reason: String, violationClass: String = ZERO_REWARD_PENALTY_ACTION) {
        recordPenalty(agentId, violationClass, FRAUD_PENALTY_DEFAULT_VALUE, reason)
    }

    private fun recordSignal(agentId: String, action: String, type: SignalType, value: Double, reason: String) {
        val timestamp = Instant.now()
        val sanitizedReason = reason.replace("\t", " ").replace("\n", " ")
        val row = "${timestamp}\t${agentId}\t${action}\t${type}\t${value}\t${sanitizedReason}\n"
        storageFile.appendText(row)
    }

    fun queryByAgent(agentId: String): List<Signal> {
        return readAllSignals().filter { it.agentId == agentId }
    }

    fun queryByAction(action: String): List<Signal> {
        return readAllSignals().filter { it.action == action }
    }

    private fun readAllSignals(): List<Signal> {
        if (!storageFile.exists()) return emptyList()
        val lines = storageFile.readLines().drop(1) // Drop header
        return lines.mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size >= 6) {
                try {
                    Signal(
                        timestamp = Instant.parse(parts[0]),
                        agentId = parts[1],
                        action = parts[2],
                        type = SignalType.valueOf(parts[3]),
                        value = parts[4].toDouble(),
                        reason = parts[5]
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }
}
