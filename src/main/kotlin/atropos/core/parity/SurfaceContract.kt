/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parity

/**
 * What every surface must agree on.
 *
 * `HOE-F01`: "Same project identity + status vocabulary on CLI/Web/Android;
 * single source of truth in engine; surfaces are pure presentation."
 * `SUP.PROV.SURFACE-PARITY`: "Behavioural parity across surfaces is tested, not
 * assumed. Competitors diverge silently per surface."
 *
 * Silently is the operative word. Two surfaces drifting apart both look correct
 * in isolation, and the disagreement only surfaces when an operator compares
 * them — usually while trying to understand something else. This makes the
 * comparison a check rather than an accident.
 *
 * Rendering differences are explicitly allowed: ANSI versus HTML is a surface
 * concern. What may not differ is identity, vocabulary, and gate outcome.
 */
data class SurfaceObservation(
    val surface: String,
    val projectIds: List<String>,
    val statusTerms: List<String>,
    val completionTerms: List<String>,
    /** Gate verdicts keyed by node id. */
    val gateOutcomes: Map<String, Boolean>
)

class SurfaceContract(private val observations: List<SurfaceObservation>) {

    fun check(): ParityReport {
        if (observations.size < 2) {
            // One surface cannot disagree with itself, and reporting parity
            // from a single observation would be a green light nobody earned.
            return ParityReport(
                compared = observations.size,
                divergences = emptyList(),
                conclusive = false
            )
        }

        val reference = observations.first()
        val divergences = buildList {
            observations.drop(1).forEach { other ->
                compare("projectIds", reference, other, reference.projectIds, other.projectIds)?.let(::add)
                compare("statusTerms", reference, other, reference.statusTerms, other.statusTerms)?.let(::add)
                compare("completionTerms", reference, other, reference.completionTerms, other.completionTerms)?.let(::add)

                val shared = reference.gateOutcomes.keys intersect other.gateOutcomes.keys
                shared.filter { reference.gateOutcomes[it] != other.gateOutcomes[it] }
                    .forEach { node ->
                        add(
                            Divergence(
                                field = "gateOutcome:$node",
                                left = "${reference.surface}=${reference.gateOutcomes[node]}",
                                right = "${other.surface}=${other.gateOutcomes[node]}"
                            )
                        )
                    }
            }
        }

        return ParityReport(observations.size, divergences, conclusive = true)
    }

    private fun compare(
        field: String,
        left: SurfaceObservation,
        right: SurfaceObservation,
        leftValues: List<String>,
        rightValues: List<String>
    ): Divergence? =
        if (leftValues == rightValues) null
        else Divergence(
            field = field,
            left = "${left.surface}=${leftValues.joinToString(",")}",
            right = "${right.surface}=${rightValues.joinToString(",")}"
        )
}

data class Divergence(val field: String, val left: String, val right: String)

data class ParityReport(
    val compared: Int,
    val divergences: List<Divergence>,
    /** False when too few surfaces were observed to conclude anything. */
    val conclusive: Boolean
) {
    /** Parity holds only when it was actually testable and nothing diverged. */
    val holds: Boolean get() = conclusive && divergences.isEmpty()

    fun render(): String = when {
        !conclusive -> "parity inconclusive: $compared surface(s) observed"
        divergences.isEmpty() -> "parity holds across $compared surfaces"
        else -> "parity broken: " + divergences.joinToString("; ") { "${it.field} ${it.left} vs ${it.right}" }
    }
}
