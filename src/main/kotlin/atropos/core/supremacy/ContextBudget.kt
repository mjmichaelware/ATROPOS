/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-003: Information-Theoretic Context Budget
 *
 * Computes and enforces the maximum context budget for agent operations
 * based on information-theoretic principles (Shannon entropy).
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path

object ContextBudget {
    /**
     * Default maximum context tokens (can be overridden via env).
     */
    private const val DEFAULT_MAX_TOKENS = 128000

    /**
     * Gets the configured context budget.
     */
    fun getBudget(environment: Map<String, String> = System.getenv()): Long {
        return environment["ATROPOS_CONTEXT_BUDGET"]
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_MAX_TOKENS.toLong()
    }

    /**
     * Computes the information-theoretic entropy of a context window.
     */
    fun computeEntropy(text: String): Double {
        if (text.isBlank()) return 0.0
        val freq = text.groupingBy { it }.eachCount()
        val total = text.length.toDouble()
        return freq.values.sumOf { count ->
            val p = count / total
            -p * Math.log(p) / Math.log(2.0)
        }
    }

    /**
     * Computes the optimal context budget based on entropy.
     */
    fun computeOptimalBudget(text: String, maxTokens: Long = getBudget()): Long {
        val entropy = computeEntropy(text)
        val estimatedTokens = (text.length / 4.0).toLong() // Rough estimate
        return (estimatedTokens * (1.0 + entropy / 8.0)).toLong().coerceAtMost(maxTokens)
    }

    /**
     * Persists the context budget to CAS.
     */
    fun persistBudget(casDir: Path, budget: Long): Path {
        Files.createDirectories(casDir)
        val budgetFile = casDir.resolve("context-budget.txt")
        Files.writeString(budgetFile, budget.toString())
        return budgetFile
    }

    /**
     * Reads the persisted context budget.
     */
    fun readBudget(casDir: Path): Long {
        val budgetFile = casDir.resolve("context-budget.txt")
        if (!Files.isRegularFile(budgetFile)) return getBudget()
        return Files.readString(budgetFile).trim().toLongOrNull() ?: getBudget()
    }

    /**
     * Verifies that a context fits within the budget.
     */
    fun verifyFit(text: String, budget: Long): Boolean {
        val estimatedTokens = (text.length / 4.0).toLong()
        return estimatedTokens <= budget
    }

    /**
     * CLI command to show current budget.
     */
    fun showBudget(environment: Map<String, String> = System.getenv()): String {
        return "Context budget: ${getBudget(environment)} tokens"
    }
}