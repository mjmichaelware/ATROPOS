/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * `R(d)` — `P20-L03` and `P20-NS02`, the formal reproducibility predicate.
 *
 * > P20-NS02 Formal reproducibility predicate R(d) machine-checkable; noise
 * > cannot generate proposals. IMPL: Pure function; unit-tested.
 *
 * Distinct from [ReproducibilityGate], which compares two file trees and
 * answers *did this build reproduce*. This answers a different question: *is
 * this deficiency reproducible*, over a set of runtime observations. Both are
 * called reproducibility and neither can do the other's job, which is why they
 * are separate files rather than one class with a flag.
 *
 * The reason it must be a pure function is stated in the gap map as "noise
 * cannot generate proposals". A self-improvement loop that acted on single
 * transient failures would spend its bounded budget chasing flakes, and worse,
 * would produce amendments to authority on the strength of a hiccup. Law 20.4
 * makes this gate mandatory before the proposal generator for exactly that
 * reason.
 *
 * No I/O, no clock, no randomness. Given the same observations it returns the
 * same verdict, which is what makes an amendment's necessity proof checkable
 * by someone who was not there.
 */
object ReproducibilityPredicate {

    /** Occurrences of one deficiency needed before it counts as reproducible. */
    const val FREQUENCY_THRESHOLD = 3

    /** Distinct environments a deficiency must appear in to be environment-independent. */
    const val ENVIRONMENT_THRESHOLD = 2

    /**
     * Evaluates `R(d)` over [observations].
     *
     * @param observations every observation believed to be of one deficiency.
     *   Observations that are not of the same deficiency are reported as
     *   [ReproducibilityVerdict.mixed] rather than silently partitioned —
     *   deciding which subset was meant is a judgement this function must not
     *   make on the caller's behalf.
     */
    fun evaluate(observations: List<RuntimeObservation>): ReproducibilityVerdict {
        if (observations.isEmpty()) {
            return ReproducibilityVerdict(false, "no observations", 0, 0)
        }
        val incomplete = observations.filterNot { it.complete }
        if (incomplete.isNotEmpty()) {
            return ReproducibilityVerdict(
                false,
                "${incomplete.size} observation(s) incomplete: " +
                    incomplete.first().missing().joinToString(", "),
                observations.size,
                0
            )
        }

        val first = observations.first()
        if (observations.any { !first.sameDeficiencyAs(it) }) {
            return ReproducibilityVerdict.mixed(observations.size)
        }

        val environments = observations.map { it.environmentFingerprint }.distinct().size
        val occurrences = observations.sumOf { it.frequency }

        // A safety-critical observation reproduces on sight. Waiting for a
        // second leak before believing the first is not caution, it is a
        // second leak.
        if (first.severity.advancesAlone) {
            return ReproducibilityVerdict(
                true,
                "safety-critical observation is reproducible on first occurrence",
                occurrences,
                environments
            )
        }

        // A broken invariant is deterministic by construction: the invariant
        // either holds or it does not, and it did not.
        if (first.invariantBroken != null) {
            return ReproducibilityVerdict(
                true,
                "invariant ${first.invariantBroken} broken; determinism is the invariant's own claim",
                occurrences,
                environments
            )
        }

        if (occurrences < FREQUENCY_THRESHOLD) {
            return ReproducibilityVerdict(
                false,
                "$occurrences occurrence(s) below the threshold of $FREQUENCY_THRESHOLD; treated as noise",
                occurrences,
                environments
            )
        }

        return ReproducibilityVerdict(
            true,
            "$occurrences occurrence(s) across $environments environment(s)",
            occurrences,
            environments
        )
    }

    /**
     * True when a deficiency reproduced independently of its environment.
     *
     * Not required for `R(d)` to hold — a deficiency confined to one
     * environment is still real — but recorded because a proposal to change
     * shared code on the strength of a single-environment failure is a
     * different risk from one supported everywhere, and the auditor should be
     * able to see which it is holding.
     */
    fun environmentIndependent(observations: List<RuntimeObservation>): Boolean =
        observations.map { it.environmentFingerprint }.distinct().size >= ENVIRONMENT_THRESHOLD
}

/** The verdict of `R(d)`, with the arithmetic that produced it. */
data class ReproducibilityVerdict(
    val holds: Boolean,
    val reason: String,
    val occurrences: Int,
    val environments: Int
) {
    fun render(): String = (if (holds) "R(d) holds" else "R(d) fails") + ": " + reason

    companion object {
        fun mixed(count: Int) = ReproducibilityVerdict(
            false,
            "observations describe more than one deficiency; $count supplied, partition them first",
            count,
            0
        )
    }
}
