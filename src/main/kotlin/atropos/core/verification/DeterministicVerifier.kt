package atropos.core.verification

import atropos.ast.AstSymbolGraph
import atropos.ast.AstImportStatus
import atropos.cli.input.CommandRegistry
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentSmokeRunner
import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import atropos.core.verifier.ConstraintSolverEvaluator
import atropos.core.verifier.DeterministicConstraint
import atropos.dloi.DloiService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

enum class DeterministicClassification {
    DETERMINISTIC,
    UNDECIDABLE
}

data class DeterministicFinding(
    val invariantId: String,
    val severity: DiagnosticSeverity,
    val file: String?,
    val symbolOrLocation: String?,
    val evidence: String,
    val remediation: String,
    val classification: DeterministicClassification
)

data class DeterministicVerificationResult(
    val findings: List<DeterministicFinding>
) {
    val passed: Boolean
        get() = findings.none { it.severity == DiagnosticSeverity.ERROR }

    fun render(): String = buildString {
        appendLine("deterministic verifier:")
        appendLine("  passed: $passed")
        appendLine("  findings: ${findings.size}")
        findings.forEach { finding ->
            appendLine(
                "  ${finding.severity.name.lowercase()} ${finding.invariantId} " +
                    "file=${finding.file ?: "none"} location=${finding.symbolOrLocation ?: "none"} " +
                    "evidence=${finding.evidence} remediation=${finding.remediation} " +
                    "classification=${finding.classification.name.lowercase()}"
            )
        }
    }.trimEnd()
}

class DeterministicVerifier(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val dloiService: DloiService = DloiService(repoRoot),
    private val higZeroGuard: atropos.dloi.HigZeroGuard = atropos.dloi.HigZeroGuard(dloiService),
    private val astGraph: AstSymbolGraph = AstSymbolGraph(repoRoot),
    private val smokeRunner: AgentSmokeRunner = AgentSmokeRunner(repoRoot),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val constraintEvaluator: ConstraintSolverEvaluator = ConstraintSolverEvaluator(),
    private val architectureComplianceChecker: ArchitectureComplianceChecker = ArchitectureComplianceChecker(enforcing = true)
) {
    private val checks = DeterministicChecks(
        repoRoot = repoRoot,
        dloiService = dloiService,
        higZeroGuard = higZeroGuard,
        astGraph = astGraph,
        smokeRunner = smokeRunner,
        patchExtractor = patchExtractor,
        redactionFilter = redactionFilter,
        constraintEvaluator = constraintEvaluator,
        architectureComplianceChecker = architectureComplianceChecker
    )
    fun verify(
        sourcePaths: List<Path>,
        patchText: String? = null,
        shellCommand: String? = null,
        dloiAddress: String? = null
    ): DeterministicVerificationResult {
        val findings = mutableListOf<DeterministicFinding>()
        val inScopePaths = sourcePaths.mapNotNull { path ->
            val scopeFindings = checks.checkSourceScope(path)
            findings += scopeFindings
            if (scopeFindings.isEmpty()) path else null
        }
        inScopePaths.forEach { path ->
            if (path.fileName.toString().substringAfterLast('.', "") == "kt" && Files.isRegularFile(path)) {
                findings += checks.checkPackagePathInvariant(path)
                findings += checks.checkDuplicateImports(path)
                findings += checks.checkImportReconciliation(path)
            }
        }
        val kotlinPaths = inScopePaths.filter {
            it.fileName.toString().substringAfterLast('.', "") == "kt" && Files.isRegularFile(it)
        }
        if (kotlinPaths.isNotEmpty()) findings += checks.checkAstImpact(kotlinPaths)
        findings += checks.checkCommandRegistryIntegrity()
        findings += checks.checkRedactionInvariant()
        findings += checks.checkForbiddenPaths(inScopePaths)
        findings += checks.checkArchitectureCompliance(inScopePaths)
        // Tree-wide rather than per-path, so it runs once regardless of scope.
        findings += checks.checkStructuralInvariants()
        findings += checks.reportSideEffectPaths()
        patchText?.let { findings += checks.checkPatchStructure(it) }
        shellCommand?.let { findings += checks.checkShellSafety(it) }
        dloiAddress?.let { findings += checks.checkDloiAddress(it) }
        return DeterministicVerificationResult(findings.filterNotNull())
    }

    /**
     * The tree-wide structural rules on their own, with no source paths.
     *
     * `/verify structural` asks a question that does not depend on which files
     * changed, so it must not be forced to name any. Separate entry point
     * rather than a scope flag on [verify]: the per-path checks would all be
     * skipped anyway, and a call that silently does nothing for most of its
     * arguments is worse than one that never took them.
     */
    fun verifyStructure(): DeterministicVerificationResult =
        DeterministicVerificationResult(
            checks.checkStructuralInvariants() + checks.reportSideEffectPaths()
        )
}
