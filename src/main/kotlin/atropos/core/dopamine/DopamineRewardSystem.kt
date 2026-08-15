/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dopamine

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

data class RewardLogEntry(
    val agentId: String,
    val action: String,
    val value: Double,
    val reason: String
)

class RewardPenaltyStore(private val logFile: File) {
    private val executor = Executors.newSingleThreadExecutor()

    fun recordReward(entry: RewardLogEntry) {
        executor.submit {
            runCatching {
                val line = "${entry.agentId}\t${entry.action}\t${entry.value}\t${entry.reason.replace("\n", " ")}\n"
                logFile.parentFile?.mkdirs()
                logFile.appendText(line, StandardCharsets.UTF_8)
            }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }
}

object RewardCalculator {
    fun computeReward(successRate: Double, latencyMs: Double, cost: Double): Double {
        val latencySec = latencyMs / 1000.0
        val divisor = latencySec * cost
        if (divisor <= 0.0) return 0.0
        return successRate / divisor
    }
}

data class Hyperparameters(val promptPrefix: String, val temperature: Double, val topP: Double)

object AlignmentTuner {
    fun tune(history: List<RewardLogEntry>): Hyperparameters {
        val averageReward = if (history.isEmpty()) 0.0 else history.map { it.value }.average()
        return if (averageReward < 0.5) {
            Hyperparameters("prefix-high-guidance", 0.2, 0.9)
        } else {
            Hyperparameters("prefix-standard", 0.7, 0.95)
        }
    }
}
