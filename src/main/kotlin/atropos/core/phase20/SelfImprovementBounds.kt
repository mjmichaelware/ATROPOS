/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.time.Duration
import java.time.Instant

/**
 * `P20-H03`, `P20-H04` and `P20-H05` — the numbers that stop the loop running
 * away.
 *
 * > P20-H03 Rate / depth / budget limits: max depth, max proposals/period, max
 * > files/LOC, max retries, token/compute budget. IMPL: Enforce numeric bounds;
 * > fail closed when exceeded.
 *
 * A self-improving loop with no bounds does not fail loudly; it fails by
 * consuming everything. It proposes, the proposal fails, it proposes a fix to
 * the fix, and each layer looks locally reasonable. Depth is what stops that.
 * The others stop the flatter failures: too many proposals in a window, a
 * proposal touching half the tree, a budget spent on retries.
 *
 * Fail closed is stated in the IMPL note and is the only safe reading. A bound
 * that is checked and then ignored on ambiguity is not a bound; when this
 * cannot tell whether a limit was exceeded, it says it was.
 *
 * All values are data with defaults rather than constants, so an operator can
 * tighten them for a device and so a test can exercise a boundary without
 * waiting for a real one.
 */
data class SelfImprovementBounds(
    /** How many proposals may depend on a proposal, transitively. */
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    /** How many proposals may be opened within [period]. */
    val maxProposalsPerPeriod: Int = DEFAULT_MAX_PROPOSALS,
    val period: Duration = DEFAULT_PERIOD,
    /** How many files one proposal's territory may span. */
    val maxFiles: Int = DEFAULT_MAX_FILES,
    /** How many lines one proposal may change. */
    val maxLines: Int = DEFAULT_MAX_LINES,
    /** How many times one proposal may be retried before quarantine. */
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    /** Tokens the loop may spend in [period]. */
    val tokenBudget: Long = DEFAULT_TOKEN_BUDGET,
    /** How long a subsystem must be observed after a promotion. */
    val observationPeriod: Duration = DEFAULT_OBSERVATION
) {
    init {
        require(maxDepth >= 1) { "max depth must be at least 1" }
        require(maxProposalsPerPeriod >= 1) { "max proposals per period must be at least 1" }
        require(maxFiles >= 1 && maxLines >= 1) { "file and line bounds must be positive" }
        require(maxRetries >= 0) { "max retries cannot be negative" }
        require(tokenBudget >= 0) { "token budget cannot be negative" }
    }

    /**
     * Checks one proposal against every bound.
     *
     * Returns all violations rather than the first, because an operator
     * tightening a proposal wants to know everything wrong with it — fixing one
     * bound to discover the next is how a bounded loop still burns a day.
     */
    fun check(request: BoundsRequest): BoundsVerdict {
        val violations = buildList {
            if (request.depth > maxDepth) {
                add("depth ${request.depth} exceeds max $maxDepth")
            }
            if (request.proposalsInPeriod >= maxProposalsPerPeriod) {
                add("${request.proposalsInPeriod} proposals already opened in ${period.toMinutes()}m, max $maxProposalsPerPeriod")
            }
            if (request.files > maxFiles) {
                add("territory spans ${request.files} files, max $maxFiles")
            }
            if (request.lines > maxLines) {
                add("proposal changes ${request.lines} lines, max $maxLines")
            }
            if (request.retries > maxRetries) {
                add("${request.retries} retries exceeds max $maxRetries")
            }
            if (request.tokensSpentInPeriod > tokenBudget) {
                add("${request.tokensSpentInPeriod} tokens spent, budget $tokenBudget")
            }
            if (request.subsystemUnderObservationUntil?.isAfter(request.now) == true) {
                add(
                    "subsystem is under observation until ${request.subsystemUnderObservationUntil}; " +
                        "law 20.14 requires a promoted change to survive an observation period before the next"
                )
            }
        }
        return BoundsVerdict(violations.isEmpty(), violations)
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 3
        const val DEFAULT_MAX_PROPOSALS = 5
        const val DEFAULT_MAX_FILES = 12
        const val DEFAULT_MAX_LINES = 1_200
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_TOKEN_BUDGET = 2_000_000L

        val DEFAULT_PERIOD: Duration = Duration.ofHours(24)
        val DEFAULT_OBSERVATION: Duration = Duration.ofHours(6)

        /**
         * Bounds tight enough for a phone running unattended.
         *
         * Not a different policy, the same policy with smaller numbers: a
         * device that must remain usable cannot spend a day's tokens in an hour
         * or hold twelve worktrees.
         */
        fun phone() = SelfImprovementBounds(
            maxDepth = 2,
            maxProposalsPerPeriod = 2,
            maxFiles = 6,
            maxLines = 400,
            maxRetries = 1,
            tokenBudget = 200_000L
        )
    }
}

/** What is being asked, in the terms the bounds are expressed in. */
data class BoundsRequest(
    val depth: Int,
    val proposalsInPeriod: Int,
    val files: Int,
    val lines: Int,
    val retries: Int,
    val tokensSpentInPeriod: Long,
    val now: Instant,
    val subsystemUnderObservationUntil: Instant? = null
)

/** Whether the loop may proceed, and everything stopping it if not. */
data class BoundsVerdict(val allowed: Boolean, val violations: List<String>) {
    fun render(): String =
        if (allowed) "within bounds" else "refused: " + violations.joinToString("; ")
}
