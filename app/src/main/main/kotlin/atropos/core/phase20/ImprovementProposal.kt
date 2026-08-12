/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.time.Instant

/**
 * A proposal the system makes about changing itself.
 *
 * Phase 20 §20.6 fixes the shape: "Every proposal declares baseline, target,
 * guardrails, territory, risk, rollback." All six are non-null here because the
 * document says the schema must "fail closed" — a proposal missing one of them
 * is not an incomplete proposal, it is not a proposal, and letting it exist in
 * a weaker form is how the requirement quietly becomes optional.
 *
 * §20.5 adds necessity: no proposal may request source mutation without a
 * necessity proof. [necessity] carries the evidence hashes that establish the
 * deficiency is real and reproducible, which is what separates a proposal from
 * a preference.
 */
data class ImprovementProposal(
    val id: String,
    /** Who proposed. Never equal to the approver — §20.7. */
    val proposedBy: String,
    val summary: String,
    /** §20.5: hashes of the evidence establishing the deficiency. */
    val necessity: List<String>,
    /** §20.6, all six mandatory. */
    val baseline: String,
    val target: String,
    val guardrails: List<String>,
    val territory: List<String>,
    val risk: String,
    val rollback: String,
    /** §20.13/§20.6: the metric declared *before* any code is written. */
    val metric: MetricDeclaration,
    val createdAt: Instant,
    val state: ProposalState = ProposalState.OPEN,
    val failureCount: Int = 0
) {
    /**
     * True when the proposal is structurally complete.
     *
     * Checked rather than assumed even though the type is non-null: strings can
     * be blank, and a proposal whose rollback field says "" has satisfied the
     * compiler and not the law.
     */
    fun isComplete(): Boolean =
        necessity.isNotEmpty() &&
            baseline.isNotBlank() &&
            target.isNotBlank() &&
            guardrails.isNotEmpty() &&
            risk.isNotBlank() &&
            rollback.isNotBlank() &&
            metric.isDeclared()

    fun missingFields(): List<String> = buildList {
        if (necessity.isEmpty()) add("necessity")
        if (baseline.isBlank()) add("baseline")
        if (target.isBlank()) add("target")
        if (guardrails.isEmpty()) add("guardrails")
        if (risk.isBlank()) add("risk")
        if (rollback.isBlank()) add("rollback")
        if (!metric.isDeclared()) add("metric")
    }
}

/**
 * The metric a proposal must declare before implementation.
 *
 * §20.13: "Every promoted change must prove measurable improvement against a
 * predeclared baseline." Predeclared is the operative word — a metric chosen
 * after the change is a metric chosen to make the change look good.
 */
data class MetricDeclaration(
    val name: String,
    val baselineValue: Double,
    val targetValue: Double,
    /** True when lower is better, e.g. false-VERIFIED rate. */
    val lowerIsBetter: Boolean
) {
    fun isDeclared(): Boolean = name.isNotBlank() && baselineValue != targetValue

    /** §20.13 evaluated: did the observed value actually move toward the target? */
    fun improvedBy(observed: Double): Boolean =
        if (lowerIsBetter) observed < baselineValue else observed > baselineValue
}

enum class ProposalState { OPEN, ACCEPTED, REJECTED, QUARANTINED }
