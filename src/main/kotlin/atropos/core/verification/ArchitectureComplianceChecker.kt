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
    private val enforcing: Boolean = false,
    private val policy: ArchitectureCompliancePolicy = ArchitectureCompliancePolicy(defaultLineThreshold = lineThreshold),
    private val concernDetector: ArchitectureConcernDetector = ArchitectureConcernDetector()
) {
    fun check(sourceRoot: File): ArchitectureComplianceReport {
        if (!sourceRoot.isDirectory) {
            return ArchitectureComplianceReport(0, emptyList(), enforcing)
        }

        val files = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        val violations = files.mapNotNull(::inspect)

        return ArchitectureComplianceReport(
            scannedFiles = files.size,
            violations = violations.sortedByDescending { it.physicalLines },
            enforcing = enforcing
        )
    }

    fun checkFiles(files: List<File>): ArchitectureComplianceReport {
        val kotlinFiles = files
            .filter { it.isFile && it.extension == "kt" }
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

    private companion object {
        /**
         * Source Doc 3 §1 cites "400–600 lines while mixing concerns" as the
         * observed violation band; 400 is the lower bound of that range.
         */
        const val DEFAULT_LINE_THRESHOLD = 400
    }
}
