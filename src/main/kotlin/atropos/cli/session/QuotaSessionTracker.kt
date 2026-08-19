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

    /**
     * What each request cost, oldest first.
     *
     * The running total answers "what has this session cost" and hides "what
     * is it doing" -- a spend that doubled over the last three calls and one
     * that has been flat are the same number and completely different
     * situations. Bounded, because a long session makes thousands of these and
     * only the recent shape is readable in six cells anyway.
     */
    private val costs = ArrayDeque<Double>()

    fun costHistory(): List<Double> = synchronized(costs) { costs.toList() }

    fun recordPrompt(prompt: String, inputUsdPerToken: Double = 0.0): Int {
        require(inputUsdPerToken.isFinite() && inputUsdPerToken >= 0.0) {
            "Input token rate must be finite and non-negative"
        }
        if (prompt.isEmpty()) return 0

        val tokens = ((prompt.length + 3L) / 4L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        promptCount = Math.addExact(promptCount, 1)
        estimatedTokens = Math.addExact(estimatedTokens, tokens)
        val cost = tokens * inputUsdPerToken
        runningCost += cost
        synchronized(costs) {
            costs.addLast(cost)
            while (costs.size > COST_HISTORY_BOUND) costs.removeFirst()
        }
        return tokens
    }

    fun estimatedCostUsd(): Double = runningCost

    private companion object {
        /** More than any footer can draw, few enough to hold forever. */
        const val COST_HISTORY_BOUND = 64
    }
}
