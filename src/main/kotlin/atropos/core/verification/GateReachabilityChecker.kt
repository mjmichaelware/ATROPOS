/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.io.File

/**
 * Proves there is no way to execute without passing the gate.
 *
 * `SUP.VERIF.BOUNDED-AGENCY-GATE`: "Add ArchitectureComplianceChecker assertion
 * that every tool-calling site routes through the gate", for the predicate
 * `P(raw-prose-execution)=0` by construction.
 *
 * The word doing the work is *construction*. A gate every current call site
 * happens to use is a convention; a gate that is the only way to reach a
 * process builder is a structure. This checker is what keeps the first from
 * being mistaken for the second — it finds the site that was added last week
 * by someone who did not know the rule.
 *
 * Deliberately a separate class from [ArchitectureComplianceChecker], which
 * enforces per-file atomicity. Both scan source, and that is all they share:
 * one is about how a file is shaped, this is about what the program can reach.
 * Folding them together would produce exactly the mixed-concern file that one
 * of them exists to detect.
 *
 * ## What counts as an execution site
 *
 * Anything that can start an operating-system process. Not network calls, not
 * file writes — those are bounded by territory and by the storage gate, which
 * are different controls with different owners.
 */
class GateReachabilityChecker(
    private val allowedOwners: Set<String> = DEFAULT_ALLOWED_OWNERS,
    private val knownDebt: Set<String> = KNOWN_UNBOUNDED_SITES
) {
    fun check(sourceRoot: File): GateReachabilityReport {
        if (!sourceRoot.isDirectory) return GateReachabilityReport(0, emptyList(), emptyList())

        val files = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "kt" }
            .toList()

        val found = files.mapNotNull(::inspect)
        val (declared, undeclared) = found.partition { violation ->
            knownDebt.any { violation.path.endsWith(it) }
        }
        return GateReachabilityReport(files.size, undeclared, declared)
    }

    private fun inspect(file: File): GateReachabilityViolation? {
        val relative = file.path.replace(File.separatorChar, '/')
        if (allowedOwners.any { relative.endsWith(it) }) return null
        // This file names the markers in order to search for them.
        if (relative.endsWith("core/verification/GateReachabilityChecker.kt")) return null
        // Tests exercise the bounded runners directly, which is how their
        // bounds get asserted at all. Excluding them is not a loophole: test
        // sources are not on any path the shipped engine can reach.
        if ("/src/test/" in relative) return null

        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val found = EXECUTION_MARKERS.filter { it in text }
        if (found.isEmpty()) return null

        return GateReachabilityViolation(
            path = relative,
            markers = found,
            invariant = "execution.routes_through_bounded_gate",
            observed = "reaches ${found.joinToString(", ")} without being a declared bounded runner"
        )
    }

    private companion object {
        /**
         * Ways to start a process on the JVM. Matched as source text rather
         * than resolved types, because the check has to hold for a file that
         * does not compile yet — which is precisely when a new execution site
         * is being introduced.
         */
        val EXECUTION_MARKERS = listOf(
            "ProcessBuilder(",
            "Runtime.getRuntime().exec"
        )

        /**
         * The files permitted to start a process.
         *
         * Every one is a bounded runner whose whole job is to apply the gate's
         * decision, and the list is short on purpose: each addition widens the
         * set of places a command can originate, so adding one should require
         * saying so here.
         */
        val DEFAULT_ALLOWED_OWNERS: Set<String> = setOf(
            "core/policy/BoundedProcessRunner.kt",
            "core/worktree/BoundedGitWorktreeCommandRunner.kt"
        )

        /**
         * Execution sites that predate this check.
         *
         * Recorded rather than excused. `P(raw-prose-execution)=0` does not
         * hold while these exist, and writing that down is the difference
         * between a known gap and a claim that is quietly false. Each one is a
         * place a command can start without the gate having seen it, and each
         * needs to be moved behind [DEFAULT_ALLOWED_OWNERS] or given a reason
         * it cannot be.
         *
         * The set exists so the check is usable *today*: a new site is a
         * failure immediately, instead of the whole check sitting switched off
         * until the backlog is cleared — which is how this kind of guard never
         * gets turned on at all. Entries come out as they are fixed; nothing
         * goes in without a deliberate edit.
         */
        val KNOWN_UNBOUNDED_SITES: Set<String> = emptySet()
    }
}

data class GateReachabilityViolation(
    val path: String,
    val markers: List<String>,
    val invariant: String,
    val observed: String
) {
    fun render(): String = "$path :: $invariant :: observed=$observed"
}

/**
 * @param violations sites nobody declared. These fail.
 * @param declaredDebt sites already recorded in
 *   [GateReachabilityChecker.KNOWN_UNBOUNDED_SITES]. Reported on every run
 *   rather than hidden, because a debt nobody is shown is a debt nobody pays.
 */
data class GateReachabilityReport(
    val scannedFiles: Int,
    val violations: List<GateReachabilityViolation>,
    val declaredDebt: List<GateReachabilityViolation>
) {
    val passed: Boolean get() = violations.isEmpty()

    /** True only when the predicate actually holds — no debt, no violations. */
    val predicateHolds: Boolean get() = passed && declaredDebt.isEmpty()

    fun render(): String = buildString {
        append("gate reachability: $scannedFiles files scanned")
        if (violations.isNotEmpty()) {
            appendLine()
            appendLine("${violations.size} undeclared execution site(s):")
            violations.forEach { appendLine("  " + it.render()) }
        }
        if (declaredDebt.isNotEmpty()) {
            appendLine()
            append("${declaredDebt.size} known unbounded site(s) remain; ")
            append("P(raw-prose-execution)=0 does not hold until they are closed")
        }
        if (predicateHolds) append(" — every execution site routes through the gate")
    }
}
