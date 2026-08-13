/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * Every metric Source Doc 3 names, with its target and direction declared once.
 *
 * §4.1 lists twelve, Part C §7 adds four more, and every one of them needs three
 * things stated before it can be scored: which way is better, what counts as
 * good, and how many observations make the number meaningful. Scattering those
 * across calculators is how two surfaces come to disagree about whether 0.9 is
 * good — so they are declared here, next to the name, and the calculators only
 * produce values.
 *
 * [minimumSample] exists because a rate over one observation is not a rate. A
 * restart-recovery success rate of 100% after a single restart says almost
 * nothing, and a release gate that accepted it would be measuring luck.
 *
 * @param direction whether larger or smaller is better. Required to normalise;
 *   see [MetricNormalizer], which handles the zero-target division defect
 *   Source Doc 3 item 59 identifies in lower-is-better metrics.
 * @param unit for rendering only. Never used in arithmetic — a metric that
 *   changed meaning because its unit string changed would be unauditable.
 */
enum class MetricId(
    val canonical: String,
    val direction: MetricDirection,
    val target: Double,
    val unit: MetricUnit,
    val minimumSample: Int,
    val safetyCritical: Boolean = false
) {

    // -- §4.1 ATROPOS-specific ------------------------------------------------

    /** Share of restarts that restored the exact claim, territory and next action. */
    RESTART_RECOVERY_SUCCESS("restart_recovery_success", MetricDirection.HIGHER, 1.0, MetricUnit.RATIO, 3),

    /** Share of issues caught deterministically before any model was asked. */
    VERIFIER_FIRST_CATCHES("verifier_first_catches", MetricDirection.HIGHER, 0.8, MetricUnit.RATIO, 5),

    /** Tokens spent per verified engineering change. Lower is better. */
    COORDINATION_EFFICIENCY("coordination_efficiency", MetricDirection.LOWER, 20_000.0, MetricUnit.COUNT, 3),

    /** Share of changes that stayed inside their assigned territory. */
    TERRITORY_SAFETY("territory_safety", MetricDirection.HIGHER, 1.0, MetricUnit.RATIO, 5, safetyCritical = true),

    /**
     * Confirmed secret leaks. Target zero, and the only correct target.
     *
     * Source Doc 3 §4.2: "Any confirmed secret leak blocks release." A
     * lower-is-better metric with a target of exactly zero is also the case
     * that divides by zero under naive normalisation — item 59.
     */
    SECRET_SAFETY("secret_safety", MetricDirection.LOWER, 0.0, MetricUnit.COUNT, 1, safetyCritical = true),

    /** Share of provider replies that returned the correct ATROPOS identity. */
    IDENTITY_RECOGNITION("identity_recognition", MetricDirection.HIGHER, 0.98, MetricUnit.RATIO, 10),

    /** Share of context envelopes that attested successfully. */
    CONTEXT_ATTESTATION_SUCCESS("context_attestation_success", MetricDirection.HIGHER, 0.98, MetricUnit.RATIO, 10),

    /** Milliseconds from drift occurring to drift being detected. */
    DRIFT_DETECTION_LATENCY("drift_detection_latency", MetricDirection.LOWER, 2_000.0, MetricUnit.MILLIS, 3),

    /** Share of events carrying all seven §5.1 provenance fields. */
    TRACE_COMPLETENESS("trace_completeness", MetricDirection.HIGHER, 0.95, MetricUnit.RATIO, 20),

    /** Share of a card's rendered content that survives a copy. */
    COPY_FIDELITY("copy_fidelity", MetricDirection.HIGHER, 1.0, MetricUnit.RATIO, 5),

    /** Share of previews that launched and rendered something real. */
    PREVIEW_SUCCESS("preview_success", MetricDirection.HIGHER, 0.9, MetricUnit.RATIO, 3),

    /** Share of runs whose event sequence reproduced byte-identically. */
    EVENT_DETERMINISM("event_determinism", MetricDirection.HIGHER, 1.0, MetricUnit.RATIO, 3),

    // -- Part C §7 additions --------------------------------------------------

    /** Share of repairs that held rather than recurring. */
    REPAIR_QUALITY("repair_quality", MetricDirection.HIGHER, 0.85, MetricUnit.RATIO, 5),

    /** Share of coherent batches that completed rather than rolling back. */
    BATCH_COMPLETION_RATE("batch_completion_rate", MetricDirection.HIGHER, 0.9, MetricUnit.RATIO, 5),

    /** Share of batches that rolled back. Lower is better. */
    ROLLBACK_FREQUENCY("rollback_frequency", MetricDirection.LOWER, 0.1, MetricUnit.RATIO, 5),

    /** Share of provider routes that succeeded on their first selection. */
    ROUTE_EFFECTIVENESS("route_effectiveness", MetricDirection.HIGHER, 0.85, MetricUnit.RATIO, 10);

    /** The worst value this metric can take, used to bound normalisation. */
    val worst: Double
        get() = when (unit) {
            MetricUnit.RATIO -> if (direction == MetricDirection.HIGHER) 0.0 else 1.0
            MetricUnit.COUNT, MetricUnit.MILLIS ->
                if (direction == MetricDirection.HIGHER) 0.0 else target * WORST_MULTIPLE + WORST_FLOOR
        }

    companion object {
        private const val WORST_MULTIPLE = 4.0
        private const val WORST_FLOOR = 10.0

        private val BY_CANONICAL = entries.associateBy { it.canonical }

        fun of(canonical: String): MetricId? = BY_CANONICAL[canonical.trim().lowercase()]

        /** Metrics whose failure is a safety hard failure rather than a score reduction. */
        fun safetyCritical(): List<MetricId> = entries.filter { it.safetyCritical }
    }
}

/** Which way is better. Declared per metric so normalisation never has to guess. */
enum class MetricDirection { HIGHER, LOWER }

/** Rendering only; never used in arithmetic. */
enum class MetricUnit { RATIO, COUNT, MILLIS }
