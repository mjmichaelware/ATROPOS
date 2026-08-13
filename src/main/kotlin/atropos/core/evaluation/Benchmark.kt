/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * A named external benchmark and what a run against it produced.
 *
 * Source Doc 3 item 56 names four: SWE-bench Verified, Terminal-Bench, Aider
 * Polyglot, and PR acceptance with time-to-accepted-PR. They are external
 * yardsticks rather than ATROPOS metrics — the §4.1 catalogue measures whether
 * ATROPOS is behaving, these measure whether it is any good at the job — so
 * they are modelled separately and scored separately.
 *
 * Modelled as *declared* rather than *executed*. Running SWE-bench needs a
 * harness, a dataset and hours of compute that a phone does not have and a CI
 * job may not either. What must exist here is the shape a result takes, the
 * fingerprints that make it reproducible, and the refusal to report a benchmark
 * as passed when it was never run — item 62's rule that every metric links to
 * raw immutable evidence applies with more force to a number that would
 * otherwise be a marketing claim.
 */
enum class BenchmarkId(
    val canonical: String,
    val displayName: String,
    val unit: MetricUnit,
    val direction: MetricDirection,
    /** What the competitive set achieves, as of the source document. */
    val competitiveReference: Double
) {
    SWE_BENCH_VERIFIED("swe_bench_verified", "SWE-bench Verified", MetricUnit.RATIO, MetricDirection.HIGHER, 0.65),
    TERMINAL_BENCH("terminal_bench", "Terminal-Bench", MetricUnit.RATIO, MetricDirection.HIGHER, 0.45),
    AIDER_POLYGLOT("aider_polyglot", "Aider Polyglot", MetricUnit.RATIO, MetricDirection.HIGHER, 0.70),
    PR_ACCEPTANCE("pr_acceptance", "PR acceptance rate", MetricUnit.RATIO, MetricDirection.HIGHER, 0.60),
    TIME_TO_ACCEPTED_PR("time_to_accepted_pr", "Time to accepted PR", MetricUnit.MILLIS, MetricDirection.LOWER, 86_400_000.0);

    companion object {
        private val BY_CANONICAL = entries.associateBy { it.canonical }
        fun of(canonical: String): BenchmarkId? = BY_CANONICAL[canonical.trim().lowercase()]
    }
}

/**
 * One benchmark run.
 *
 * @param environmentFingerprint what the run happened on. Item 63 requires
 *   retaining "benchmark versions, environment fingerprints, source
 *   fingerprints, and result history", and §4.4 requires reproducibility —
 *   neither of which survives a bare score. A result whose environment is
 *   unrecorded cannot be reproduced and therefore cannot be compared.
 * @param sourceFingerprint the commit the run was made against.
 */
data class BenchmarkResult(
    val id: BenchmarkId,
    val score: Double,
    val resolved: Int,
    val total: Int,
    val benchmarkVersion: String,
    val environmentFingerprint: String,
    val sourceFingerprint: String,
    val evidenceHashes: List<String> = emptyList(),
    val detail: String = ""
) {
    /** True when this result is reproducible in the §4.4 sense. */
    val reproducible: Boolean
        get() = benchmarkVersion.isNotBlank() &&
            environmentFingerprint.isNotBlank() &&
            sourceFingerprint.isNotBlank() &&
            evidenceHashes.isNotEmpty()

    /** What is missing before this result may be reported. */
    fun missingProvenance(): List<String> = buildList {
        if (benchmarkVersion.isBlank()) add("benchmarkVersion")
        if (environmentFingerprint.isBlank()) add("environmentFingerprint")
        if (sourceFingerprint.isBlank()) add("sourceFingerprint")
        if (evidenceHashes.isEmpty()) add("evidenceHashes")
    }

    /** True when the score meets or beats the competitive reference. */
    val competitive: Boolean
        get() = when (id.direction) {
            MetricDirection.HIGHER -> score >= id.competitiveReference
            MetricDirection.LOWER -> score <= id.competitiveReference
        }

    fun render(): String = buildString {
        append(id.displayName).append(": ")
        append(if (id.unit == MetricUnit.RATIO) "%.1f%%".format(score * 100) else "%.0f".format(score))
        if (total > 0) append(" ($resolved/$total)")
        append(" · ").append(if (competitive) "competitive" else "below competitive")
        append(" · v").append(benchmarkVersion.ifBlank { "unrecorded" })
        if (!reproducible) append(" · NOT REPRODUCIBLE: ").append(missingProvenance().joinToString(", "))
    }

    companion object {
        /**
         * A benchmark that has not been run.
         *
         * Distinct from a score of zero. Item 62 forbids unsupported numbers,
         * and "we have not run SWE-bench" is a true statement while "we scored
         * 0% on SWE-bench" is a false one.
         */
        fun notRun(id: BenchmarkId, why: String) = BenchmarkResult(
            id = id,
            score = Double.NaN,
            resolved = 0,
            total = 0,
            benchmarkVersion = "",
            environmentFingerprint = "",
            sourceFingerprint = "",
            detail = why
        )
    }
}

/** True when a benchmark was never run rather than run and scored zero. */
val BenchmarkResult.notRun: Boolean get() = score.isNaN()
