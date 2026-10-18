/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dopamine

import atropos.core.autonomy.RewardPenaltyStore
import atropos.core.provider.ProviderFailureMemoryStore
import atropos.core.provider.ProviderMistakeRecord
import atropos.core.thinking.Thinking
import java.io.File

/**
 * ATROPOS Dopamine Reward Tracking & Penalty System.
 *
 * Implements Mandate 1: Assigns explicit negative reward penalties (-1.0)
 * to providers that return imperfect code, syntax violations, or invalid patches,
 * accompanied by structured explanations of the exact mistake, and tracks positive
 * dopamine reinforcement upon verified AST syntax passes.
 */
class ProviderDopamineRewardTracker(
    private val rewardStore: RewardPenaltyStore = RewardPenaltyStore(
        storageDir = File(".atropos/autonomy"),
        storageFile = File(".atropos/autonomy/rewards.tsv")
    ),
    private val memoryStore: ProviderFailureMemoryStore = ProviderFailureMemoryStore.SHARED
) {

    fun recordFailure(
        provider: String,
        action: String = "provider.code_generation",
        errorType: String,
        exactMistake: String,
        penaltyValue: Double = PENALTY_DEFAULT
    ): ProviderMistakeRecord {
        val cleanProvider = provider.trim().lowercase()
        val structuredExplanation = "provider=$cleanProvider error_type=$errorType mistake=${exactMistake.replace('\t', ' ').take(200)}"

        // 1. Assign explicit negative reward penalty in Dopamine Reward System
        rewardStore.recordPenalty(
            agentId = cleanProvider,
            action = action,
            value = penaltyValue,
            reason = structuredExplanation
        )

        Thinking.step(
            "dopamine",
            "Assigned dopamine penalty ($penaltyValue) to provider [$cleanProvider] for $errorType: ${exactMistake.take(80)}"
        )

        // 2. Record immediately in session Short-Term Memory and check 2-strike rule
        val mistakeRecord = memoryStore.recordMistake(
            provider = cleanProvider,
            errorType = errorType,
            exactMistake = exactMistake
        )

        return mistakeRecord
    }

    fun recordSuccess(
        provider: String,
        action: String = "provider.code_generation",
        rewardValue: Double = REWARD_DEFAULT,
        details: String = "pre-flight AST verification passed"
    ) {
        val cleanProvider = provider.trim().lowercase()
        rewardStore.recordReward(
            agentId = cleanProvider,
            action = action,
            value = rewardValue,
            reason = "provider=$cleanProvider outcome=clean_code details=$details"
        )
        Thinking.detail(
            "dopamine",
            "Credited dopamine reward (+$rewardValue) to provider [$cleanProvider] on action $action"
        )
    }

    fun getNetRewardScore(provider: String): Double {
        val cleanProvider = provider.trim().lowercase()
        val signals = rewardStore.queryByAgent(cleanProvider)
        if (signals.isEmpty()) return 0.0

        var net = 0.0
        for (signal in signals) {
            when (signal.type) {
                RewardPenaltyStore.SignalType.REWARD -> net += signal.value
                RewardPenaltyStore.SignalType.PENALTY -> net -= kotlin.math.abs(signal.value)
                RewardPenaltyStore.SignalType.ZERO_REWARD_PENALTY -> net -= kotlin.math.abs(signal.value)
            }
        }
        return net
    }

    fun getRecentPenaltyCount(provider: String, windowSize: Int = 20): Int {
        val cleanProvider = provider.trim().lowercase()
        val signals = rewardStore.queryByAgent(cleanProvider).takeLast(windowSize)
        return signals.count { it.type != RewardPenaltyStore.SignalType.REWARD }
    }

    companion object {
        const val PENALTY_DEFAULT = -1.0
        const val REWARD_DEFAULT = 1.0

        val SHARED: ProviderDopamineRewardTracker by lazy {
            ProviderDopamineRewardTracker()
        }
    }
}
