/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

data class OperationalMetricsSnapshot(
    val totalIssuesAttempted: Int,
    val issuesResolved: Int,
    val passRate: Double,
    val totalTokensConsumed: Long,
    val averageTokensPerIssue: Long,
    val totalInventionsLogged: Int
)

class ZeroInventionMetricsCollector(val repoRoot: Path) {
    private val totalAttempts = AtomicInteger(0)
    private val totalResolved = AtomicInteger(0)
    private val totalTokens = AtomicLong(0)
    private val totalInventions = AtomicInteger(0)

    fun recordTurnMetrics(issueId: String, tokens: Int, passed: Boolean) {
        totalAttempts.incrementAndGet()
        if (passed) totalResolved.incrementAndGet()
        totalTokens.addAndGet(tokens.toLong())
    }

    fun recordInventionAnomaly(atomId: String, description: String) {
        totalInventions.incrementAndGet()
    }

    fun generateSnapshot(): OperationalMetricsSnapshot {
        val attempts = totalAttempts.get()
        val resolved = totalResolved.get()
        val tokens = totalTokens.get()
        val passRate = if (attempts > 0) resolved.toDouble() / attempts else 0.0
        val avgTokens = if (attempts > 0) tokens / attempts else 0L

        return OperationalMetricsSnapshot(
            totalIssuesAttempted = attempts,
            issuesResolved = resolved,
            passRate = passRate,
            totalTokensConsumed = tokens,
            averageTokensPerIssue = avgTokens,
            totalInventionsLogged = totalInventions.get()
        )
    }
}
