/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.dopamine.ProviderDopamineRewardTracker
import atropos.core.provider.ProviderFailureMemoryStore
import atropos.core.thinking.Thinking

data class AdaptedHyperparameters(
    val temperature: Double,
    val topP: Double,
    val strictZeroProse: Boolean,
    val guidanceLevel: String
)

/**
 * Phase 20 Structural Adaptation Engine & Meta-Adaptation Loop.
 *
 * Dynamically alters how ATROPOS formats requests, structures prompts, and queries
 * code from specific AI providers based on their dopamine reward record, Short-Term
 * Memory failures, and permanent Long-Term Memory escalated constraints.
 */
class Phase20StructuralMetaAdapter(
    private val rewardTracker: ProviderDopamineRewardTracker = ProviderDopamineRewardTracker.SHARED,
    private val memoryStore: ProviderFailureMemoryStore = ProviderFailureMemoryStore.SHARED
) {

    fun adaptPrompt(provider: String, originalPrompt: String): String {
        val cleanProvider = provider.trim().lowercase()
        val netReward = rewardTracker.getNetRewardScore(cleanProvider)
        val recentPenalties = rewardTracker.getRecentPenaltyCount(cleanProvider)
        val stmMistakes = memoryStore.getShortTermMistakes(cleanProvider)
        val ltmConstraints = memoryStore.getPermanentConstraints(cleanProvider)

        val isOffending = netReward < 0.0 || recentPenalties > 0 || ltmConstraints.isNotEmpty() || stmMistakes.isNotEmpty()

        if (!isOffending) {
            return originalPrompt
        }

        Thinking.outline(
            "phase20",
            "Phase 20 Meta-Adaptation Loop engaged for offending provider [$cleanProvider] (reward=$netReward, penalties=$recentPenalties)"
        )

        return buildString {
            appendLine("[ATROPOS PHASE 20 META-ADAPTATION: HIGH-GUIDANCE STRICT DIRECTIVE]")
            appendLine("Target Provider: $cleanProvider")
            appendLine("Provider Reliability Metric: Dopamine Score = $netReward | Recent Penalty Count = $recentPenalties")
            appendLine()
            appendLine("MANDATORY SYNTAX & AST COMPLIANCE RULES:")
            appendLine("1. ZERO PROSE: Do NOT output conversational chatter, explanations, markdown commentary, or apologies.")
            appendLine("2. FULL CODE BLOCKS: Output only complete, un-truncated file contents with all delimiters strictly closed.")
            appendLine("3. ZERO STUBS: Do NOT generate hollow stubs, TODO(), NotImplementedError, or empty catch blocks.")
            appendLine("4. FORMAT ENFORCEMENT: Enclose every modified or created file in:")
            appendLine("--- FILE: <relative_path> ---")
            appendLine("<complete code>")
            appendLine("--- END FILE ---")

            if (ltmConstraints.isNotEmpty()) {
                appendLine()
                appendLine("[PERMANENT LONG-TERM MEMORY CONSTRAINTS (2-STRIKE ESCALATION)]")
                ltmConstraints.forEach { constraint ->
                    appendLine("• $constraint")
                }
            }

            if (stmMistakes.isNotEmpty()) {
                appendLine()
                appendLine("[RECENT SHORT-TERM MEMORY FAILURE WARNINGS]")
                stmMistakes.takeLast(3).forEach { failure ->
                    appendLine("• Prior Failure [${failure.errorSignature}]: ${failure.exactMistake.take(120)}")
                }
            }

            appendLine()
            appendLine("[PROMPT PAYLOAD]")
            appendLine(originalPrompt.trim())
        }
    }

    fun adaptHyperparameters(provider: String): AdaptedHyperparameters {
        val cleanProvider = provider.trim().lowercase()
        val netReward = rewardTracker.getNetRewardScore(cleanProvider)
        val recentPenalties = rewardTracker.getRecentPenaltyCount(cleanProvider)

        return if (netReward < 0.0 || recentPenalties > 0) {
            AdaptedHyperparameters(
                temperature = 0.15,
                topP = 0.85,
                strictZeroProse = true,
                guidanceLevel = "HIGH_GUIDANCE"
            )
        } else {
            AdaptedHyperparameters(
                temperature = 0.70,
                topP = 0.95,
                strictZeroProse = false,
                guidanceLevel = "STANDARD"
            )
        }
    }

    /**
     * Used when Atropos builds itself during Phase 20 self-build operations.
     * Evaluates candidate mutations and source patches against all permanent
     * Long-Term Memory constraints.
     */
    fun validateCandidateAgainstPermanentConstraints(provider: String, code: String): List<String> {
        val violations = mutableListOf<String>()
        val ltmConstraints = memoryStore.getPermanentConstraints(provider)

        for (constraint in ltmConstraints) {
            val lowerCode = code.lowercase()
            if (constraint.contains("todo", ignoreCase = true) && code.contains("TODO()")) {
                violations.add("Violates permanent constraint: contains forbidden placeholder TODO()")
            }
            if (constraint.contains("empty catch", ignoreCase = true) && code.contains("catch (") && code.contains("{}")) {
                violations.add("Violates permanent constraint: contains empty catch block")
            }
            if (constraint.contains("unclosed", ignoreCase = true)) {
                val openBraces = code.count { it == '{' }
                val closeBraces = code.count { it == '}' }
                if (openBraces != closeBraces) {
                    violations.add("Violates permanent constraint: unclosed braces (open=$openBraces, close=$closeBraces)")
                }
            }
        }

        return violations
    }

    companion object {
        val SHARED: Phase20StructuralMetaAdapter by lazy {
            Phase20StructuralMetaAdapter()
        }
    }
}
