package atropos.core.verification

import atropos.ast.AstSymbolGraph
import atropos.ast.AstImportStatus
import atropos.cli.input.CommandRegistry
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentSmokeRunner
import atropos.core.security.RedactionFilter
import atropos.core.verifier.ConstraintSolverEvaluator
import atropos.core.verifier.DeterministicConstraint
import atropos.dloi.DloiService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readLines

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
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize(),
    private val dloiService: DloiService = DloiService(repoRoot),
    /** The only way this verifier reaches DLOI: failures arrive typed, not thrown. */
    private val higZeroGuard: atropos.dloi.HigZeroGuard = atropos.dloi.HigZeroGuard(dloiService),
    private val astGraph: AstSymbolGraph = AstSymbolGraph(repoRoot),
    private val smokeRunner: AgentSmokeRunner = AgentSmokeRunner(repoRoot),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val constraintEvaluator: ConstraintSolverEvaluator = ConstraintSolverEvaluator(),
    private val architectureComplianceChecker: ArchitectureComplianceChecker = ArchitectureComplianceChecker(enforcing = true)
) {
    fun verify(
        sourcePaths: List<Path>,
        patchText: String? = null,
        shellCommand: String? = null,
        dloiAddress: String? = null
    ): DeterministicVerificationResult {
        val findings = mutableListOf<DeterministicFinding>()
        sourcePaths.forEach { path ->
            val scopeFindings = checkSourceScope(path)
            findings += scopeFindings

            // A file outside the repository is not in the symbol graph, so the
            // analyses below have nothing to reason over — `reconcileImports`
            // would raise rather than report, turning the finding just recorded
            // into a crash. The out-of-scope finding stands and still fails the
            // run; skipping analysis withholds no verdict.
            if (scopeFindings.isNotEmpty()) return@forEach

            if (path.extension == "kt" && Files.isRegularFile(path)) {
                findings += checkPackagePathInvariant(path)
                findings += checkDuplicateImports(path)
                findings += checkImportReconciliation(path)
                findings += checkAstImpact(path)
            }
        }
        findings += checkCommandRegistryIntegrity()
        findings += checkRedactionInvariant()
        findings += checkForbiddenPaths(sourcePaths)
        findings += checkArchitectureCompliance(sourcePaths)
        patchText?.let { findings += checkPatchStructure(it) }
        shellCommand?.let { findings += checkShellSafety(it) }
        dloiAddress?.let { findings += checkDloiAddress(it) }
        return DeterministicVerificationResult(findings.filterNotNull())
    }

    private fun checkSourceScope(path: Path): List<DeterministicFinding> {
        val normalized = path.toAbsolutePath().normalize()
        return constraintEvaluator.evaluate(
            DeterministicConstraint(
                invariantId = "source_scope",
                satisfied = normalized.startsWith(repoRoot),
                expected = "path under ${repoRoot.invariantSeparatorsPathString}",
                observed = normalized.invariantSeparatorsPathString,
                remediation = "limit verification to repository files",
                file = normalized.invariantSeparatorsPathString
            )
        )
    }

    private fun checkPackagePathInvariant(path: Path): List<DeterministicFinding> {
        val lines = path.readLines(StandardCharsets.UTF_8)
        val packageLine = lines.firstOrNull { it.trimStart().startsWith("package ") } ?: return emptyList()
        val packageName = packageLine.removePrefix("package ").trim()
        val relative = repoRoot.relativize(path).invariantSeparatorsPathString
        val expectedSuffix = packageName.replace('.', '/') + "/" + path.fileName
        return constraintEvaluator.evaluate(
            DeterministicConstraint(
                invariantId = "package_path_invariant",
                satisfied = relative.endsWith(expectedSuffix),
                expected = expectedSuffix,
                observed = relative,
                remediation = "align Kotlin package with file path",
                file = relative,
                symbolOrLocation = packageName
            )
        )
    }

    private fun checkDuplicateImports(path: Path): List<DeterministicFinding> {
        val imports = path.readLines(StandardCharsets.UTF_8)
            .filter { it.trimStart().startsWith("import ") }
            .map { it.removePrefix("import ").trim() }
        val duplicates = imports.groupingBy { it }.eachCount().filterValues { it > 1 }
        return duplicates.keys.map { duplicate ->
            finding(
                invariantId = "duplicate_imports",
                severity = DiagnosticSeverity.ERROR,
                file = repoRoot.relativize(path).invariantSeparatorsPathString,
                symbolOrLocation = duplicate,
                evidence = "duplicate import",
                remediation = "remove repeated import"
            )
        }
    }

    private fun checkImportReconciliation(path: Path): List<DeterministicFinding> {
        val relative = repoRoot.relativize(path).invariantSeparatorsPathString
        val reconciliation = astGraph.reconcileImports(relative)
        return reconciliation.resolutions.mapNotNull { resolution ->
            when (resolution.status) {
                AstImportStatus.LOCAL_EXACT,
                AstImportStatus.EXTERNAL -> null

                AstImportStatus.WILDCARD -> finding(
                    invariantId = "import_reconciliation",
                    severity = DiagnosticSeverity.ERROR,
                    file = relative,
                    symbolOrLocation = resolution.importPath,
                    evidence = "wildcard import is not deterministic",
                    remediation = "replace wildcard import with an exact import"
                )

                AstImportStatus.AMBIGUOUS -> finding(
                    invariantId = "import_reconciliation",
                    severity = DiagnosticSeverity.ERROR,
                    file = relative,
                    symbolOrLocation = resolution.importPath,
                    evidence = "ambiguous import matches ${resolution.matches.joinToString(", ")}",
                    remediation = "select one exact package path and import it explicitly"
                )

                AstImportStatus.UNRESOLVED -> finding(
                    invariantId = "import_reconciliation",
                    severity = DiagnosticSeverity.ERROR,
                    file = relative,
                    symbolOrLocation = resolution.importPath,
                    evidence = "import cannot be reconciled against the local symbol graph",
                    remediation = "fix the import path or add the missing symbol before provider review"
                )
            }
        }
    }

    private fun checkAstImpact(path: Path): List<DeterministicFinding> {
        val impacted = astGraph.impactedByPaths(listOf(repoRoot.relativize(path).invariantSeparatorsPathString))
            .filter { it.kind != atropos.ast.AstSymbolKind.FILE }
        return if (impacted.isEmpty()) {
            listOf(
                finding(
                    invariantId = "ast_impact",
                    severity = DiagnosticSeverity.WARNING,
                    file = repoRoot.relativize(path).invariantSeparatorsPathString,
                    evidence = "no symbols resolved from Kotlin source",
                    remediation = "verify parser coverage or symbol declarations"
                )
            )
        } else {
            emptyList()
        }
    }

    private fun checkCommandRegistryIntegrity(): List<DeterministicFinding> {
        val commands = CommandRegistry.commands()
        return constraintEvaluator.evaluate(
            DeterministicConstraint(
                invariantId = "command_registry_integrity",
                satisfied = commands.size == commands.distinct().size,
                expected = "all slash command entries unique",
                observed = "commands=${commands.size} distinct=${commands.distinct().size}",
                remediation = "deduplicate command registry",
                file = "src/main/kotlin/atropos/cli/input/CommandRegistry.kt"
            )
        )
    }

    private fun checkRedactionInvariant(): List<DeterministicFinding> {
        val sample = "Authorization: Bearer " + "A".repeat(24) + " sk-" + "B".repeat(24)
        val redacted = redactionFilter.redact(sample)
        return constraintEvaluator.evaluate(
            DeterministicConstraint(
                invariantId = "redaction",
                satisfied = !redacted.contains("A".repeat(24)) && !redacted.contains("B".repeat(24)),
                expected = "redacted output omits raw bearer and api key material",
                observed = redacted,
                remediation = "expand redaction coverage",
                file = "src/main/kotlin/atropos/core/security/RedactionFilter.kt"
            )
        )
    }

    private fun checkForbiddenPaths(paths: List<Path>): List<DeterministicFinding> {
        val banned = listOf(".gradle/", "build/", ".jar", ".atropos/secrets", ".git/")
        return paths.mapNotNull { path ->
            val normalized = path.toAbsolutePath().normalize()
            if (!normalized.startsWith(repoRoot)) return@mapNotNull null
            val relative = repoRoot.relativize(normalized).invariantSeparatorsPathString
            banned.firstOrNull { token -> relative.contains(token) || relative.endsWith(token) }?.let { token ->
                constraintEvaluator.evaluate(
                    DeterministicConstraint(
                        invariantId = "forbidden_path",
                        satisfied = false,
                        expected = "path outside forbidden tokens ${banned.joinToString(",")}",
                        observed = relative,
                        remediation = "remove forbidden file from deterministic scope",
                        file = relative,
                        symbolOrLocation = token
                    )
                ).single()
            }
        }
    }

    private fun checkArchitectureCompliance(paths: List<Path>): List<DeterministicFinding> {
        val inScope = paths.map { it.toAbsolutePath().normalize() }
            .filter { it.startsWith(repoRoot) && Files.isRegularFile(it) && it.extension == "kt" }
        if (inScope.isEmpty()) return emptyList()

        val report = architectureComplianceChecker.checkFiles(inScope.map { it.toFile() })
        val severity = if (report.blocksBuild) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
        return report.violations.map { violation ->
            val file = runCatching {
                val normalized = Path.of(violation.path).toAbsolutePath().normalize()
                if (normalized.startsWith(repoRoot)) repoRoot.relativize(normalized).invariantSeparatorsPathString else violation.path
            }.getOrElse { violation.path }
            finding(
                invariantId = violation.invariant,
                severity = severity,
                file = file,
                evidence = "expected=one atomic responsibility observed=${violation.observed}",
                remediation = "split transport, normalization, routing, rendering, verification, and execution into single-responsibility files"
            )
        }
    }

    private fun checkPatchStructure(patchText: String): List<DeterministicFinding> {
        val extraction = patchExtractor.extract(patchText)
            ?: return listOf(
                finding(
                    invariantId = "patch_structure",
                    severity = DiagnosticSeverity.ERROR,
                    file = null,
                    evidence = "no unified diff found",
                    remediation = "supply a valid unified diff"
                )
            )
        val validation = patchExtractor.validate(extraction.diff) ?: return emptyList()
        return listOf(
            finding(
                invariantId = "patch_structure",
                severity = DiagnosticSeverity.ERROR,
                file = extraction.touchedPaths.firstOrNull(),
                evidence = validation,
                remediation = "remove forbidden or malformed patch paths"
            )
        )
    }

    private fun checkShellSafety(shellCommand: String): List<DeterministicFinding> {
        val refusal = smokeRunner.validate(shellCommand) ?: return emptyList()
        return listOf(
            finding(
                invariantId = "shell_safety",
                severity = DiagnosticSeverity.ERROR,
                file = null,
                evidence = refusal,
                remediation = "use conservative local-only smoke commands"
            )
        )
    }

    private fun checkDloiAddress(address: String): List<DeterministicFinding> {
        // Through the guard, so an unresolvable address is a typed NoMatch
        // rather than an exception this method has to interpret.
        return when (val result = higZeroGuard.resolve(address)) {
            is atropos.dloi.DloiLookupResult.Resolved -> emptyList()
            is atropos.dloi.DloiLookupResult.NoMatch -> listOf(
                finding(
                    invariantId = "dloi_address",
                    severity = DiagnosticSeverity.ERROR,
                    file = "docs/ATROPOS_CANONICAL_PHASES_1_11_AUTHORITY.md",
                    symbolOrLocation = address,
                    evidence = result.reason,
                    remediation = "use a provable document#section@Lstart-end address"
                )
            )
        }
    }

    private fun finding(
        invariantId: String,
        severity: DiagnosticSeverity,
        file: String? = null,
        symbolOrLocation: String? = null,
        evidence: String,
        remediation: String
    ) = DeterministicFinding(
        invariantId = invariantId,
        severity = severity,
        file = file,
        symbolOrLocation = symbolOrLocation,
        evidence = evidence,
        remediation = remediation,
        classification = DeterministicClassification.DETERMINISTIC
    )
}
