/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-002: Termination Bound
 *
 * Computes and enforces the maximum number of self-improvement loop iterations
 * before mandatory termination. This prevents infinite loops in the
 * self-improvement process.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path

object TerminationBound {
    /**
     * The default maximum number of self-improvement iterations.
     * Can be overridden via ATROPOS_TERMINATION_BOUND env var.
     */
    private const val DEFAULT_MAX_ITERATIONS = 100

    /**
     * Gets the configured termination bound.
     */
    fun getBound(environment: Map<String, String> = System.getenv()): Int {
        return environment["ATROPOS_TERMINATION_BOUND"]
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it > 0 && it <= 1000 }
            ?: DEFAULT_MAX_ITERATIONS
    }

    /**
     * Persists the termination bound to the CAS.
     */
    fun persistBound(casDir: Path, bound: Int): Path {
        Files.createDirectories(casDir)
        val boundFile = casDir.resolve("termination-bound.txt")
        Files.writeString(boundFile, bound.toString())
        return boundFile
    }

    /**
     * Reads the persisted termination bound.
     */
    fun readBound(casDir: Path): Int {
        val boundFile = casDir.resolve("termination-bound.txt")
        if (!Files.isRegularFile(boundFile)) return getBound()
        return Files.readString(boundFile).trim().toIntOrNull() ?: getBound()
    }

    /**
     * Verifies that the current iteration count hasn't exceeded the bound.
     */
    fun checkIteration(currentIteration: Int, bound: Int): Boolean {
        return currentIteration < bound
    }

    /**
     * CLI command to show the current termination bound.
     */
    fun showBound(environment: Map<String, String> = System.getenv()): String {
        return "Termination bound: ${getBound(environment)} iterations"
    }
}