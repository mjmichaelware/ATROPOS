/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.time.Instant

/**
 * `P20-L01` — the normalised shape a runtime observation must take before it can
 * become anything else.
 *
 * The Phase 20 gap map specifies the fields exactly: "observation carries ID,
 * timestamp, runtime/project/goal/node IDs, authority+env fingerprints, exit
 * code, bounded output, artifact hashes, frequency, severity", and the IMPL note
 * is "normalise observation schema; reject incomplete observations".
 *
 * Rejecting is the part that matters. Law 20.2 says runtime observations are
 * evidence *candidates*, not authority, and the whole chain from here to an
 * accepted amendment is a series of narrowings. If the first step admits
 * anything, every later gate is checking a shape it cannot rely on — and the
 * cheapest place to stop a malformed observation is before it has been counted,
 * correlated and cited.
 *
 * [frequency] and [severity] exist here rather than being derived later because
 * `P20-L04` advances a candidate only on "deterministic reproduce, frequency
 * threshold, single invariant break, safety-critical, or blocked requirement".
 * Frequency computed after the fact from a log is a guess about what was the
 * same event; frequency recorded at observation time is a count.
 */
data class RuntimeObservation(
    val id: String,
    val timestamp: Instant,
    val runtimeId: String,
    val projectId: String,
    val goalId: String?,
    val nodeId: String?,
    /** Hash of the authority documents in force when this was observed. */
    val authorityFingerprint: String,
    /** Hash of the environment: toolchain, platform, versions. */
    val environmentFingerprint: String,
    val exitCode: Int?,
    /** Bounded on capture. An observation is not a log. */
    val boundedOutput: String,
    val artifactHashes: List<String>,
    /** How many times this same deficiency has been seen. */
    val frequency: Int,
    val severity: ObservationSeverity,
    /** The invariant this observation breaks, when it breaks one. */
    val invariantBroken: String? = null,
    /** The requirement this observation blocks, when it blocks one. */
    val requirementBlocked: String? = null
) {
    /**
     * True when every field the gap map names is present.
     *
     * An observation missing fingerprints cannot support reproducibility,
     * because reproducibility means *under the same authority and environment*
     * — and without them there is no way to say whether two observations were
     * even of the same system.
     */
    val complete: Boolean get() = missing().isEmpty()

    fun missing(): List<String> = buildList {
        if (id.isBlank()) add("id")
        if (runtimeId.isBlank()) add("runtimeId")
        if (projectId.isBlank()) add("projectId")
        if (authorityFingerprint.isBlank()) add("authorityFingerprint")
        if (environmentFingerprint.isBlank()) add("environmentFingerprint")
        if (boundedOutput.isBlank()) add("boundedOutput")
        if (frequency < 1) add("frequency")
    }

    /**
     * True when this observation is of the same deficiency as [other].
     *
     * Same runtime, same authority, same environment, same exit code, same
     * broken invariant. Deliberately excludes timestamp and output text: two
     * occurrences of one bug differ in when and in incidental detail, and a
     * comparison that included them would count every recurrence as novel and
     * never reach a frequency threshold.
     */
    fun sameDeficiencyAs(other: RuntimeObservation): Boolean =
        runtimeId == other.runtimeId &&
            authorityFingerprint == other.authorityFingerprint &&
            environmentFingerprint == other.environmentFingerprint &&
            exitCode == other.exitCode &&
            invariantBroken == other.invariantBroken

    fun render(): String = buildString {
        append(id).append(' ').append(severity.name)
        append(" freq=").append(frequency)
        exitCode?.let { append(" exit=").append(it) }
        invariantBroken?.let { append(" invariant=").append(it) }
        requirementBlocked?.let { append(" blocks=").append(it) }
        append(" env=").append(environmentFingerprint.take(12))
    }
}

/**
 * How bad an observation is.
 *
 * [SAFETY_CRITICAL] is not the top of a scale but a separate class: the gap map
 * lists "safety-critical" as its own advancement route in `P20-L04`, alongside
 * frequency and invariant breaks, and law 20.12 puts safety invariants outside
 * what the loop may touch at all.
 */
enum class ObservationSeverity(val advancesAlone: Boolean) {
    /** Noise. Cannot advance without meeting a frequency threshold. */
    INFO(false),

    /** Degraded behaviour. Advances on frequency. */
    DEGRADED(false),

    /** A run broke. Advances on frequency or a broken invariant. */
    FAILURE(false),

    /** A safety invariant is implicated. Advances on its own, once. */
    SAFETY_CRITICAL(true)
}
