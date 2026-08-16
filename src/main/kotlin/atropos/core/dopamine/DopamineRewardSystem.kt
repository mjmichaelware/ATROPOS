/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dopamine

import java.io.File
import java.util.concurrent.Executors

data class RewardLogEntry(
    val agentId: String,
    val action: String,
    val value: Double,
    val reason: String
)

class RewardPenaltyStore(private val logFile: File) {
    private val executor = Executors.newSingleThreadExecutor()
    private val canonicalStore = atropos.core.autonomy.RewardPenaltyStore(
        storageDir = logFile.parentFile ?: File("."),
        storageFile = logFile
    )

    fun recordReward(entry: RewardLogEntry) {
        executor.submit {
            runCatching {
                canonicalStore.recordReward(
                    agentId = entry.agentId,
                    action = entry.action,
                    value = entry.value,
                    reason = entry.reason
                )
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

data class Hyperparameters(
    val promptPrefix: String,
    val temperature: Double,
    val topP: Double,
    val fewShotExamples: List<String> = emptyList()
)

object AlignmentTuner {
    fun historyFrom(
        store: atropos.core.autonomy.RewardPenaltyStore,
        action: String = "provider.chat",
        limit: Int = DEFAULT_WINDOW
    ): List<RewardLogEntry> = store.queryByAction(action)
        .takeLast(limit.coerceAtLeast(1))
        .map { signal ->
            RewardLogEntry(
                agentId = signal.agentId,
                action = signal.action,
                value = if (signal.type == atropos.core.autonomy.RewardPenaltyStore.SignalType.REWARD) signal.value else -signal.value,
                reason = signal.reason
            )
        }

    /**
     * Tune only from a bounded recent window. Older outcomes remain durable
     * evidence, but cannot drown out the behaviour of the current route.
     * Examples are explanations, never raw provider transcripts.
     */
    fun tune(history: List<RewardLogEntry>, windowSize: Int = DEFAULT_WINDOW): Hyperparameters {
        require(windowSize > 0) { "alignment window must be positive" }
        val recent = history.takeLast(windowSize)
        val averageReward = recent.map { it.value }.averageOrNull() ?: 0.0
        val successful = recent.filter { it.value > 0.0 }
            .takeLast(MAX_FEW_SHOT_EXAMPLES)
            .map { "action=${it.action}; outcome=${it.reason.take(MAX_REASON_CHARS)}" }

        return if (averageReward < 0.5) {
            Hyperparameters("prefix-high-guidance", 0.2, 0.9, successful)
        } else {
            Hyperparameters("prefix-standard", 0.7, 0.95, successful)
        }
    }

    fun apply(prompt: String, parameters: Hyperparameters): String = buildString {
        append(parameters.promptPrefix)
        append("\nGeneration temperature=")
        append(parameters.temperature)
        append(" top_p=")
        append(parameters.topP)
        if (parameters.fewShotExamples.isNotEmpty()) {
            append("\nRelevant successful examples:\n")
            parameters.fewShotExamples.forEach { append("- ").append(it).append('\n') }
        }
        append("\nTask:\n")
        append(prompt.trim())
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private const val DEFAULT_WINDOW = 20
    private const val MAX_FEW_SHOT_EXAMPLES = 3
    private const val MAX_REASON_CHARS = 160
}
