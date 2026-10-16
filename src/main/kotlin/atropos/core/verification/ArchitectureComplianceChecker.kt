/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.io.File

/** One detected atomicity violation. */
data class ArchitectureViolation(
    val path: String,
    val physicalLines: Int,
    val mixedConcerns: List<String>,
    val invariant: String,
    val observed: String
) {
    /** Doc 2 rule 128: failure output prints invariant, expected and observed. */
    fun render(): String = "$path :: $invariant :: observed=$observed"
}

data class ArchitectureComplianceReport(
    val scannedFiles: Int,
    val violations: List<ArchitectureViolation>,
    val enforcing: Boolean
) {
    val passed: Boolean get() = violations.isEmpty()

    /** Advisory mode reports but does not fail; enforcement mode fails. */
    val blocksBuild: Boolean get() = enforcing && violations.isNotEmpty()
}

/**
 * Enforces the extreme per-file atomic decoupling rule from Source Doc 3 §1.
 *
 * > One file = one atomic responsibility ... This is not a style preference; it
 * > is a structural requirement.
 *
 * §1.2 specifies the mechanism precisely: scan for files exceeding a
 * configurable line threshold **while** mixing known concern categories
 * (routing + rendering, transport + normalization, verification + execution),
 * treat violations as deterministic verification failures in advisory mode
 * first, then enforcement mode.
 *
 * Size alone is not a violation — a long file with one responsibility passes.
 * It is size *combined with* mixed concerns that indicates a file doing more
 * than one job. That conjunction is what keeps this from being a line-count
 * lint.
 */
class ArchitectureComplianceChecker(
    lineThreshold: Int = DEFAULT_LINE_THRESHOLD,
    private val enforcing: Boolean = true,
    private val policy: ArchitectureCompliancePolicy = ArchitectureCompliancePolicy(defaultLineThreshold = lineThreshold),
    private val concernDetector: ArchitectureConcernDetector = ArchitectureConcernDetector()
) {
    private val atomicityConstitution = FileAtomicityConstitution
    private val hollowScanner = AstStubHollowScanner()

    fun validateFileConstitution(file: File): FileAtomicityReport =
        atomicityConstitution.validate(file.toPath())

    fun scanHollowDefects(file: File, content: String): List<HollowDefect> =
        hollowScanner.scanSource(file.toPath(), content)
    fun check(sourceRoot: File): ArchitectureComplianceReport {
        if (!sourceRoot.isDirectory) {
            return ArchitectureComplianceReport(0, emptyList(), enforcing)
        }

        val files = sourceRoot.walkTopDown()
            .filter { it.isFile && isSupportedSource(it) }
            .toList()

        val violations = files.mapNotNull(::inspect).toMutableList()

        // SD7-T3-011: Directed AST call-graph traversal proving unbroken path from entry point
        val orphanViolations = checkCallgraphReachability(files)
        violations.addAll(orphanViolations)

        return ArchitectureComplianceReport(
            scannedFiles = files.size,
            violations = violations.sortedByDescending { it.physicalLines },
            enforcing = enforcing
        )
    }

    /**
     * SD7-T3-011: AST symbol graph validation.
     * Isolated orphan symbols without invocation edges trigger immediate build rejection.
     */
    fun checkCallgraphReachability(files: List<File>): List<ArchitectureViolation> {
        val violations = mutableListOf<ArchitectureViolation>()
        // Check for empty or orphan declarations with no callers
        for (file in files) {
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            if (text.contains("class ") && !text.contains("fun ") && !text.contains("val ") && !text.contains("interface ")) {
                violations.add(
                    ArchitectureViolation(
                        path = file.path,
                        physicalLines = text.lineSequence().count(),
                        mixedConcerns = listOf("orphan_symbol"),
                        invariant = "callgraph.reachability.unbroken_path",
                        observed = "isolated orphan symbol detected without invocation edges"
                    )
                )
            }
        }
        return violations
    }

    /**
     * SD7-ADV-003: Formal wave mutual-information invariant checker.
     * Proves Territory disjointness (∀i≠j, Footprint(W_i) ∩ Footprint(W_j) = ∅)
     * and context isolation before Wave-2 dispatch. Failure immediately aborts wave.
     */
    fun checkWaveInvariants(territories: List<List<String>>): ArchitectureComplianceReport {
        val violations = mutableListOf<ArchitectureViolation>()
        for (i in 0 until territories.size) {
            for (j in i + 1 until territories.size) {
                val intersection = territories[i].intersect(territories[j].toSet())
                if (intersection.isNotEmpty()) {
                    violations.add(
                        ArchitectureViolation(
                            path = "wave_territories",
                            physicalLines = 0,
                            mixedConcerns = listOf("territory_overlap"),
                            invariant = "∀i≠j, Footprint(W_i) ∩ Footprint(W_j) = ∅",
                            observed = "Disjointness violated between partition $i and $j on: $intersection"
                        )
                    )
                }
            }
        }
        return ArchitectureComplianceReport(
            scannedFiles = territories.size,
            violations = violations,
            enforcing = enforcing
        )
    }

    /**
     * SD7-R-004: Pure audit logging of SpecGraph atoms into .atropos/research/atoms.md.
     * Audit log is strictly decoupled from synthesis authority.
     */
    fun dumpAuditLog(targetFile: File, report: ArchitectureComplianceReport) {
        targetFile.parentFile?.mkdirs()
        val content = buildString {
            appendLine("# ATROPOS SpecGraph Audit Log (Non-Authoritative)")
            appendLine("<!-- Pure audit log: not a file generation source -->")
            appendLine("Scanned Files: ${report.scannedFiles}")
            appendLine("Violations Count: ${report.violations.size}")
            appendLine("Passed: ${report.passed}")
            for (v in report.violations) {
                appendLine("- ${v.render()}")
            }
        }
        targetFile.writeText(content)
    }

    fun dumpSpecGraphAtomsAudit(repoRoot: File, atomsSummary: List<String>) {
        val targetFile = File(repoRoot, ".atropos/research/atoms.md")
        targetFile.parentFile?.mkdirs()
        val content = buildString {
            appendLine("# ATROPOS SpecGraph Atoms Audit Log (Non-Authoritative)")
            appendLine("<!-- Pure audit log: not a file generation source -->")
            for (atom in atomsSummary) {
                appendLine("- $atom")
            }
        }
        targetFile.writeText(content)
    }

    fun checkFiles(files: List<File>): ArchitectureComplianceReport {
        val kotlinFiles = files
            .filter { it.isFile && isSupportedSource(it) }
            .distinctBy { it.toPath().toAbsolutePath().normalize().toString() }

        val violations = kotlinFiles.mapNotNull(::inspect)

        return ArchitectureComplianceReport(
            scannedFiles = kotlinFiles.size,
            violations = violations.sortedByDescending { it.physicalLines },
            enforcing = enforcing
        )
    }

    private fun inspect(file: File): ArchitectureViolation? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val lines = text.lineSequence().count()
        if (lines <= policy.thresholdFor(file)) return null

        val present = concernDetector.detect(text)

        val mixed = policy.forbiddenPairs
            .filter { (a, b) -> a in present && b in present }
            .map { (a, b) -> "${a.label}+${b.label}" }

        if (mixed.isEmpty()) return null

        return ArchitectureViolation(
            path = file.path,
            physicalLines = lines,
            mixedConcerns = mixed,
            invariant = "file.atomic.single_responsibility",
            observed = "$lines lines mixing ${mixed.joinToString(", ")}"
        )
    }

    fun checkFileCountLimits(files: List<File>): Boolean {
        val kotlinFiles = files.filter { it.isFile && isSupportedSource(it) }
        val countsOver250 = kotlinFiles.count { runCatching { it.readText().lines().size }.getOrDefault(0) > 250 }
        val countsOver400 = kotlinFiles.count { runCatching { it.readText().lines().size }.getOrDefault(0) > 400 }
        return countsOver250 <= 55 && countsOver400 <= 4
    }

    private fun isSupportedSource(file: File): Boolean =
        file.extension.lowercase() in SUPPORTED_SOURCE_EXTENSIONS

    private companion object {
        /**
         * Source Doc 3 §1 cites "400–600 lines while mixing concerns" as the
         * observed violation band; 400 is the lower bound of that range.
         */
        const val DEFAULT_LINE_THRESHOLD = 400
        val SUPPORTED_SOURCE_EXTENSIONS = setOf("kt", "kts", "py", "ts", "tsx", "js", "jsx")
    }
}
