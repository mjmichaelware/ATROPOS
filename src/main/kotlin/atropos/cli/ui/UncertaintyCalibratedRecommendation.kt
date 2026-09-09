/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.security.RedactionFilter
import java.time.Instant

/**
 * Uncertainty-Calibrated Recommendation UI (F-X-07 / F-SUP-026).
 *
 * Renders recommendations with explicit uncertainty calibration:
 * - Percentage confidence (0-100%)
 * - Evidence IDs backing the recommendation
 * - "Not measured" state when confidence unavailable
 * - Calibration bands: HIGH (>80%), MEDIUM (50-80%), LOW (<50%)
 *
 * This implements the "uncertainty-calibrated recommendation" requirement:
 * every recommendation must carry its confidence level and evidence trail,
 * so the operator can distinguish "I'm sure" from "I'm guessing".
 */
class UncertaintyCalibratedRecommendation(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
) {
    data class Recommendation(
        val id: String,
        val text: String,
        val confidence: Confidence,
        val evidenceIds: List<String>,
        val category: Category,
        val timestamp: Instant = Instant.now(),
    ) {
        /** Renderable confidence display. */
        val confidenceDisplay: String
            get() = when (confidence) {
                is Confidence.Measured -> "${confidence.percent}% (${confidence.band.displayName})"
                is Confidence.NotMeasured -> "Not measured — treat as guess"
                is Confidence.Unavailable -> "Unavailable — no evidence"
            }

        /** Whether this recommendation should be acted on without review. */
        val actionable: Boolean
            get() = confidence is Confidence.Measured && confidence.percent >= 80 && evidenceIds.isNotEmpty()
    }

    sealed class Confidence {
        data class Measured(val percent: Int, val band: Band) : Confidence() {
            require(percent in 0..100) { "Percent must be 0-100" }
        }
        object NotMeasured : Confidence()
        object Unavailable : Confidence()

        enum class Band {
            HIGH { override fun displayName() = "High (≥80%)" },
            MEDIUM { override fun displayName() = "Medium (50-79%)" },
            LOW { override fun displayName() = "Low (<50%)" };

            fun displayName(): String = name
        }

        companion object {
            fun fromPercent(percent: Int): Measured {
                val band = when {
                    percent >= 80 -> Band.HIGH
                    percent >= 50 -> Band.MEDIUM
                    else -> Band.LOW
                }
                return Measured(percent, band)
            }
        }
    }

    enum class Category {
        PROVIDER,      // Provider selection/routing
        VERIFICATION,  // Verification gate
        TERRITORY,     // Territory/permission
        REPAIR,        // Repair strategy
        PLANNING,      // Planning/DAG
        GENERAL,       // General advice
    }

    private val recommendations = mutableListOf<Recommendation>()

    fun add(
        text: String,
        confidence: Confidence,
        evidenceIds: List<String> = emptyList(),
        category: Category = Category.GENERAL,
    ): Recommendation {
        val rec = Recommendation(
            id = java.util.UUID.randomUUID().toString(),
            text = redactionFilter.redact(text),
            confidence = confidence,
            evidenceIds = evidenceIds,
            category = category,
        )
        recommendations.add(rec)
        return rec
    }

    fun addMeasured(
        text: String,
        percent: Int,
        evidenceIds: List<String> = emptyList(),
        category: Category = Category.GENERAL,
    ): Recommendation {
        return add(text, Confidence.fromPercent(percent), evidenceIds, category)
    }

    fun addNotMeasured(text: String, category: Category = Category.GENERAL): Recommendation {
        return add(text, Confidence.NotMeasured, emptyList(), category)
    }

    fun addUnavailable(text: String, category: Category = Category.GENERAL): Recommendation {
        return add(text, Confidence.Unavailable, emptyList(), category)
    }

    fun all(): List<Recommendation> = recommendations.toList()

    fun byCategory(category: Category): List<Recommendation> =
        recommendations.filter { it.category == category }

    fun actionable(): List<Recommendation> =
        recommendations.filter { it.actionable }

    fun renderAll(): String {
        if (recommendations.isEmpty()) return "No recommendations."
        return recommendations.joinToString("\n") { rec ->
            val badge = when (rec.confidence) {
                is Confidence.Measured -> when (rec.confidence.band) {
                    Confidence.Band.HIGH -> "🟢 HIGH"
                    Confidence.Band.MEDIUM -> "🟡 MEDIUM"
                    Confidence.Band.LOW -> "🔴 LOW"
                }
                is Confidence.NotMeasured -> "⚪ NOT MEASURED"
                is Confidence.Unavailable -> "⚫ UNAVAILABLE"
            }
            val evidence = if (rec.evidenceIds.isNotEmpty()) " [evidence: ${rec.evidenceIds.joinToString(",")}]" else " [no evidence]"
            "$badge ${rec.text}$evidence"
        }
    }

    fun clear() { recommendations.clear() }
}

/**
 * Renderer for uncertainty-calibrated recommendations in the CLI.
 */
class UncertaintyCalibratedRenderer(
    private val theme: ThemePalette,
) {
    fun render(rec: UncertaintyCalibratedRecommendation.Recommendation): String {
        val sb = StringBuilder()
        val confidence = rec.confidence

        val (prefix, color) = when (confidence) {
            is UncertaintyCalibratedRecommendation.Confidence.Measured -> when (confidence.band) {
                UncertaintyCalibratedRecommendation.Confidence.Band.HIGH -> "✓ " to theme.success
                UncertaintyCalibratedRecommendation.Confidence.Band.MEDIUM -> "~ " to theme.warning
                UncertaintyCalibratedRecommendation.Confidence.Band.LOW -> "⚠ " to theme.error
            }
            is UncertaintyCalibratedRecommendation.Confidence.NotMeasured -> "?" to theme.metadata
            is UncertaintyCalibratedRecommendation.Confidence.Unavailable -> "×" to theme.metadata
        }

        sb.append(color("$prefix"))
        sb.append(rec.text)

        // Confidence display
        sb.append(" ")
        sb.append(theme.metadata("["))
        sb.append(color(confidenceDisplay(confidence)))
        sb.append(theme.metadata("]"))

        // Evidence IDs
        if (rec.evidenceIds.isNotEmpty()) {
            sb.append(" ")
            sb.append(theme.metadata("["))
            sb.append(theme.metadata("evidence: ${rec.evidenceIds.joinToString(",")}"))
            sb.append(theme.metadata("]"))
        } else {
            sb.append(" ")
            sb.append(theme.metadata("[no evidence]"))
        }

        return sb.toString()
    }

    private fun confidenceDisplay(confidence: UncertaintyCalibratedRecommendation.Confidence): String = when (confidence) {
        is UncertaintyCalibratedRecommendation.Confidence.Measured -> "${confidence.percent}% (${confidence.band.displayName})"
        is UncertaintyCalibratedRecommendation.Confidence.NotMeasured -> "Not measured"
        is UncertaintyCalibratedRecommendation.Confidence.Unavailable -> "Unavailable"
    }

    fun renderAll(recommendations: List<UncertaintyCalibratedRecommendation.Recommendation>): String {
        if (recommendations.isEmpty()) return theme.metadata("No recommendations.")
        return recommendations.map { render(it) }.joinToString("\n")
    }
}