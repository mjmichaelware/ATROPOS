/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.confidence

import atropos.core.research.PromptArtifact
import atropos.core.research.ResearchChannelReceipt
import atropos.core.research.SpanType
import atropos.core.toolchain.NativeToolchainRegistry
import kotlin.math.ln
import kotlin.math.max

data class ParetoConfidence(
    val clarity: Double,
    val surfaceCoverage: Double,
    val knownOwnerDensity: Double,
    val openCriticalGaps: Double,
    val score: Double
)

object ConfidenceCalculator {
    private const val W1 = 0.25
    private const val W2 = 0.25
    private const val W3 = 0.25
    private const val W4 = 0.25

    fun calculate(prompt: PromptArtifact, receipts: List<ResearchChannelReceipt>): ParetoConfidence {
        val clarity = computeClarity(prompt.rawPromptText)
        val surfaceCoverage = computeSurfaceCoverage(prompt)
        val knownOwnerDensity = computeKnownOwnerDensity()
        val openCriticalGaps = computeOpenCriticalGaps(prompt, receipts)

        val rawScore = (W1 * clarity) + (W2 * surfaceCoverage) + (W3 * knownOwnerDensity) - (W4 * openCriticalGaps)
        val score = rawScore.coerceIn(0.0, 1.0)

        return ParetoConfidence(
            clarity = clarity,
            surfaceCoverage = surfaceCoverage,
            knownOwnerDensity = knownOwnerDensity,
            openCriticalGaps = openCriticalGaps,
            score = score
        )
    }

    private fun computeClarity(text: String): Double {
        val tokens = text.lowercase()
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-z0-9]"), "") }
            .filter { it.isNotBlank() }

        if (tokens.size <= 1) return 1.0

        val n = tokens.size.toDouble()
        val freqs = tokens.groupingBy { it }.eachCount()

        var entropy = 0.0
        for ((_, count) in freqs) {
            val p = count / n
            entropy -= p * (ln(p) / ln(2.0))
        }

        val maxEntropy = ln(n) / ln(2.0)
        return if (maxEntropy > 0.0) {
            (1.0 - (entropy / maxEntropy)).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
    }

    private fun computeSurfaceCoverage(prompt: PromptArtifact): Double {
        if (prompt.spans.isEmpty()) return 0.0
        val nonProseCount = prompt.spans.count { it.type != SpanType.PROSE }
        return (nonProseCount.toDouble() / prompt.spans.size.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun computeKnownOwnerDensity(): Double {
        val targets = listOf("rust", "typescript", "go", "python", "kotlin")
        var availableCount = 0
        var registeredCount = 0

        for (target in targets) {
            val driver = NativeToolchainRegistry.getDriver(target)
            if (driver != null) {
                registeredCount++
                if (driver.isAvailable()) {
                    availableCount++
                }
            }
        }

        return if (availableCount > 0) {
            (availableCount.toDouble() / targets.size.toDouble()).coerceIn(0.0, 1.0)
        } else if (registeredCount > 0) {
            (0.5 * (registeredCount.toDouble() / targets.size.toDouble())).coerceIn(0.0, 1.0)
        } else {
            0.2
        }
    }

    private fun computeOpenCriticalGaps(prompt: PromptArtifact, receipts: List<ResearchChannelReceipt>): Double {
        val constraintSpans = prompt.spans.filter { it.type == SpanType.CONSTRAINT }
        if (constraintSpans.isEmpty()) return 0.0

        val allFindings = receipts.flatMap { it.findings }
        val unmapped = constraintSpans.count { span ->
            val sample = span.text.take(24).trim()
            allFindings.none { finding -> finding.contains(sample, ignoreCase = true) }
        }

        return (unmapped.toDouble() / constraintSpans.size.toDouble()).coerceIn(0.0, 1.0)
    }
}
