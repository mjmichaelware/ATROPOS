/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A benchmark score is the number most worth faking and the hardest to check,
 * so the rules here are about refusal: a result that cannot be reproduced is
 * not recorded, and a benchmark that was never run is reported as such rather
 * than omitted.
 */
class BenchmarkRunnerTest {

    private fun runner() = BenchmarkRunner(repoRoot = Files.createTempDirectory("atropos-bench-"))

    private fun result(
        id: BenchmarkId = BenchmarkId.SWE_BENCH_VERIFIED,
        score: Double = 0.71,
        version: String = "verified-2026.03",
        environment: String = "termux-aarch64-jdk21",
        source: String = "9dcc88d"
    ) = BenchmarkResult(
        id = id,
        score = score,
        resolved = 355,
        total = 500,
        benchmarkVersion = version,
        environmentFingerprint = environment,
        sourceFingerprint = source
    )

    @Test
    fun `a complete result is recorded and comes back`() {
        val runner = runner()

        val recording = runner.record(result(), "355/500 resolved")

        assertTrue(recording.accepted, recording.reason)
        val reported = runner.report().single { it.id == BenchmarkId.SWE_BENCH_VERIFIED }
        assertEquals(0.71, reported.score)
        assertEquals("verified-2026.03", reported.benchmarkVersion)
    }

    @Test
    fun `a result missing its environment fingerprint is rejected, and says so`() {
        val recording = runner().record(result(environment = ""), "output")

        assertFalse(recording.accepted)
        assertTrue(recording.reason.contains("environmentFingerprint"))
    }

    @Test
    fun `a result missing its source fingerprint is rejected`() {
        val recording = runner().record(result(source = ""), "output")

        assertFalse(recording.accepted)
        assertTrue(recording.reason.contains("sourceFingerprint"))
    }

    @Test
    fun `a result missing its benchmark version is rejected`() {
        val recording = runner().record(result(version = ""), "output")

        assertFalse(recording.accepted)
        assertTrue(recording.reason.contains("benchmarkVersion"))
    }

    @Test
    fun `raw output is stored as evidence and attached to the result`() {
        val runner = runner()

        val recording = runner.record(result(), "the full harness output")

        assertEquals(1, recording.result.evidenceHashes.size)
        assertTrue(recording.result.reproducible)
    }

    /**
     * The rule that keeps a report honest. Omitting unrun benchmarks makes a
     * report of one strong score look like a complete evaluation.
     */
    @Test
    fun `benchmarks with no result are reported as not run rather than omitted`() {
        val runner = runner()
        runner.record(result(), "output")

        val report = runner.report()

        assertEquals(BenchmarkId.entries.size, report.size)
        assertEquals(BenchmarkId.entries.size - 1, report.count { it.notRun })
        assertTrue(report.single { it.id == BenchmarkId.TERMINAL_BENCH }.notRun)
    }

    @Test
    fun `a not-run benchmark is not a score of zero`() {
        val absent = BenchmarkResult.notRun(BenchmarkId.AIDER_POLYGLOT, "no harness available")

        assertTrue(absent.notRun)
        assertFalse(absent.score == 0.0)
        assertFalse(absent.reproducible)
    }

    @Test
    fun `a not-run result cannot be recorded`() {
        val recording = runner().record(
            BenchmarkResult.notRun(BenchmarkId.TERMINAL_BENCH, "never ran"),
            "output"
        )

        assertFalse(recording.accepted)
    }

    @Test
    fun `coverage reports how much of the set has any result`() {
        val runner = runner()
        runner.record(result(), "output")
        runner.record(result(id = BenchmarkId.AIDER_POLYGLOT, score = 0.55), "output")

        val coverage = runner.coverage()

        assertEquals(BenchmarkId.entries.size, coverage.total)
        assertEquals(2, coverage.run)
        assertEquals(2, coverage.reproducible)
        assertEquals(1, coverage.competitive, "0.55 is below the 0.70 Aider reference")
        assertTrue(coverage.render().contains("2 of ${BenchmarkId.entries.size}"))
    }

    @Test
    fun `a lower-is-better benchmark is competitive by being smaller`() {
        val fast = result(id = BenchmarkId.TIME_TO_ACCEPTED_PR, score = 3_600_000.0)
        val slow = result(id = BenchmarkId.TIME_TO_ACCEPTED_PR, score = 200_000_000.0)

        assertTrue(fast.competitive)
        assertFalse(slow.competitive)
    }

    @Test
    fun `results survive a new runner over the same root`() {
        val root = Files.createTempDirectory("atropos-bench-")
        BenchmarkRunner(repoRoot = root).record(result(), "output")

        val reported = BenchmarkRunner(repoRoot = root).report()
            .single { it.id == BenchmarkId.SWE_BENCH_VERIFIED }

        assertFalse(reported.notRun, "a recorded benchmark must survive restart")
        assertEquals(0.71, reported.score)
    }

    @Test
    fun `the dashboard renders both forms from one classification`() {
        val metrics = listOf(
            AtroposMetric(MetricId.TERRITORY_SAFETY, 1.0, 100, listOf("h")),
            AtroposMetric(MetricId.SECRET_SAFETY, 1.0, 10, listOf("h")),
            AtroposMetric.unmeasured(MetricId.PREVIEW_SUCCESS, "no previews")
        )
        val dashboard = EvaluationDashboard()

        val text = dashboard.render(metrics)
        val json = dashboard.renderJson(metrics)

        assertTrue(text.contains("safety hard failure"), "the leak must lead")
        assertTrue(text.contains("UNMEASURED"), "unmeasured must be shown, not dropped")
        assertTrue(json.contains("\"verdict\": \"SAFETY_HARD_FAILURE\""))
        assertTrue(json.contains("\"unmeasured\": true"))
        assertTrue(json.contains("\"value\": null"), "NaN is not valid JSON")
    }

    @Test
    fun `the dashboard reports how many metrics were measured and supported`() {
        val metrics = listOf(
            AtroposMetric(MetricId.TERRITORY_SAFETY, 1.0, 100, listOf("h")),
            AtroposMetric(MetricId.ROUTE_EFFECTIVENESS, 0.9, 100),
            AtroposMetric.unmeasured(MetricId.PREVIEW_SUCCESS, "no previews")
        )

        val text = EvaluationDashboard().render(metrics)

        assertTrue(text.contains("measured           2 of 3"))
        assertTrue(text.contains("supported          1 of 3"))
    }
}
