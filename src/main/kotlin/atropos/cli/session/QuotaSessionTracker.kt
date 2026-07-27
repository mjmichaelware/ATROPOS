/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package atropos.cli.session

class QuotaSessionTracker {
    var promptCount: Int = 0
        private set
    var estimatedTokens: Int = 0
        private set
    var runningCost: Double = 0.0
        private set

    fun recordPrompt(prompt: String, inputUsdPerToken: Double = 0.0): Int {
        require(inputUsdPerToken.isFinite() && inputUsdPerToken >= 0.0) {
            "Input token rate must be finite and non-negative"
        }
        if (prompt.isEmpty()) return 0

        val tokens = ((prompt.length + 3L) / 4L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        promptCount = Math.addExact(promptCount, 1)
        estimatedTokens = Math.addExact(estimatedTokens, tokens)
        runningCost += tokens * inputUsdPerToken
        return tokens
    }

    fun estimatedCostUsd(): Double = runningCost
}
