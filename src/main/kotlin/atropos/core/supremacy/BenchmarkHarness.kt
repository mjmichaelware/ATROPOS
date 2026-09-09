/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-034: Public Benchmark Harness
 *
 * Implements a public benchmark harness for comparing
 * ATROPOS against other agents.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object BenchmarkHarness {
    data class Benchmark(
        val id: String,
        val name: String,
        val description: String,
        val runner: () -> BenchmarkResult
    )

    data class BenchmarkResult(
        val benchmarkId: String,
        val score: Double,
        val metrics: Map<String, Double> = emptyMap(),
        val completedAt: Instant = Instant.now()
    )

    data class RunResult(
        val benchmarkId: String,
        val agent: String,
        val result: BenchmarkResult,
        val error: String? = null
    )

    private val benchmarks = mutableMapOf<String, Benchmark>()
    private val results = mutableListOf<RunResult>()

    /**
     * Registers a benchmark.
     */
    fun register(benchmark: Benchmark) {
        benchmarks[benchmark.id] = benchmark
    }

    /**
     * Runs a benchmark for an agent.
     */
    fun run(benchmarkId: String, agentName: String): RunResult {
        val benchmark = benchmarks[benchmarkId] ?: return RunResult(benchmarkId, agentName, BenchmarkResult(benchmarkId, 0.0), "Benchmark not found")
        return try {
            val result = benchmark.runner()
            RunResult(benchmarkId, agentName, result)
        } catch (e: Exception) {
            RunResult(benchmarkId, agentName, BenchmarkResult(benchmarkId, 0.0), e.message)
        }
    }

    /**
     * Runs all benchmarks for an agent.
     */
    fun runAll(agentName: String): List<RunResult> {
        return benchmarks.keys.map { run(it, agentName) }
    }

    /**
     * Gets leaderboard for a benchmark.
     */
    fun leaderboard(benchmarkId: String): List<RunResult> {
        return results
            .filter { it.benchmarkId == benchmarkId }
            .sortedByDescending { it.result.score }
    }

    /**
     * Persists benchmark results to CAS.
     */
    fun persistResults(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("benchmark-results.json")
        val json = results.map { r ->
            """
                {
                    "benchmarkId": "${r.benchmarkId}",
                    "agent": "${r.agent}",
                    "score": ${r.result.score},
                    "metrics": ${r.result.metrics.joinToString(",") { "\"$it.key\": $it.value" }},
                    "completedAt": "${r.result.completedAt}",
                    "error": "${r.error ?: ""}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to run benchmarks.
     */
    fun runAndShow(agentName: String): String {
        val results = runAll(agentName)
        return results.joinToString("\n") {
            "${it.benchmarkId}: ${if (it.error != null) "ERROR: ${it.error}" else "score=${"%.2f".format(it.result.score)}"}"
        }
    }
}