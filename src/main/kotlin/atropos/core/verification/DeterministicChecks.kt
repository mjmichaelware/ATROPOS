package atropos.core.verification

import atropos.ast.AstSymbolGraph
import atropos.ast.AstImportStatus
import atropos.cli.input.CommandRegistry
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentSmokeRunner
import atropos.core.security.RedactionFilter
import atropos.core.verifier.ConstraintSolverEvaluator
import atropos.core.verifier.BoundaryConstraint
import atropos.core.verifier.BoundaryRule
import atropos.core.verifier.DeterministicConstraint
import atropos.dloi.DloiService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class DeterministicChecks(
    private val repoRoot: Path,
    private val dloiService: DloiService,
    private val higZeroGuard: atropos.dloi.HigZeroGuard,
    private val astGraph: AstSymbolGraph,
    private val smokeRunner: AgentSmokeRunner,
    private val patchExtractor: AgentPatchExtractor,
    private val redactionFilter: RedactionFilter,
    private val constraintEvaluator: ConstraintSolverEvaluator,
    private val architectureComplianceChecker: ArchitectureComplianceChecker
) {
    fun checkSourceScope(path: Path): List<DeterministicFinding> {
        val normalized = path.toAbsolutePath().normalize()
        return constraintEvaluator.evaluateBoundaries(
            BoundaryConstraint(
                invariantId = "source_scope",
                rule = BoundaryRule.PATH_WITHIN_ROOT,
                expected = portablePath(repoRoot),
                observed = portablePath(normalized),
                remediation = "limit verification to repository files",
                file = portablePath(normalized)
            )
        )
    }

    fun checkPackagePathInvariant(path: Path): List<DeterministicFinding> {
        val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
        val packageLine = lines.firstOrNull { it.trimStart().startsWith("package ") } ?: return emptyList()
        val packageName = packageLine.trim().removePrefix("package ").trim()
        val relative = portablePath(repoRoot.relativize(path))
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

    fun checkDuplicateImports(path: Path): List<DeterministicFinding> {
        val imports = Files.readAllLines(path, StandardCharsets.UTF_8)
            .filter { it.trimStart().startsWith("import ") }
            .map { it.removePrefix("import ").trim() }
        val duplicates = imports.groupingBy { it }.eachCount().filterValues { it > 1 }
        return duplicates.keys.map { duplicate ->
            finding(
                invariantId = "duplicate_imports",
                severity = DiagnosticSeverity.ERROR,
                file = portablePath(repoRoot.relativize(path)),
                symbolOrLocation = duplicate,
                evidence = "duplicate import",
                remediation = "remove repeated import"
            )
        }
    }

    fun checkImportReconciliation(path: Path): List<DeterministicFinding> {
        val relative = portablePath(repoRoot.relativize(path))
        val wildcardFindings = Files.readAllLines(path, StandardCharsets.UTF_8)
            .filter { it.trimStart().startsWith("import ") && it.trim().removePrefix("import ").contains(".*") }
            .map { importLine ->
                finding(
                    invariantId = "import_reconciliation",
                    severity = DiagnosticSeverity.ERROR,
                    file = relative,
                    symbolOrLocation = importLine.trim().removePrefix("import "),
                    evidence = "wildcard import is not deterministic",
                    remediation = "replace wildcard import with an exact import"
                )
            }
        if (!Files.isDirectory(repoRoot.resolve("src/main/kotlin")) &&
            !Files.isDirectory(repoRoot.resolve("src/test/kotlin"))
        ) {
            return wildcardFindings
        }
        val reconciliation = astGraph.reconcileImports(relative)
        val statusFindings = reconciliation.resolutions.mapNotNull { resolution ->
            when (resolution.status) {
                AstImportStatus.LOCAL_EXACT, AstImportStatus.EXTERNAL -> null
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
        val ruleFindings = reconciliation.violations.map { violation ->
            finding(
                invariantId = "import_determinism",
                severity = DiagnosticSeverity.ERROR,
                file = relative,
                symbolOrLocation = violation.imports.joinToString(","),
                evidence = "${violation.rule}: ${violation.evidence}",
                remediation = violation.remediation
            )
        }
        return wildcardFindings + statusFindings + ruleFindings
    }

    fun checkAstImpact(paths: List<Path>): List<DeterministicFinding> {
        // A temporary or generated audit root may intentionally contain a
        // standalone source file rather than the repository's canonical
        // src/main/kotlin and src/test/kotlin roots. There is no graph impact
        // claim to make in that shape; the other deterministic checks still
        // inspect the file.
        if (!Files.isDirectory(repoRoot.resolve("src/main/kotlin"))) return emptyList()
        val normalizedPaths = paths.map { it.toAbsolutePath().normalize() }
        return normalizedPaths.mapNotNull { path ->
            val relativePath = portablePath(repoRoot.relativize(path))
            val impacted = astGraph.impactOfPaths(listOf(relativePath))
            if (impacted.any { it.kind != atropos.ast.AstSymbolKind.FILE }) return@mapNotNull null
            finding(
                invariantId = "ast_impact",
                // A Kotlin source file that produces no declarations means
                // parser coverage or source integrity failed. Treating this
                // as a warning lets the deterministic result pass because
                // only error findings block completion.
                severity = DiagnosticSeverity.ERROR,
                file = portablePath(repoRoot.relativize(path)),
                evidence = "no symbols resolved from changed file or its local import dependents",
                remediation = "verify parser coverage, symbol declarations, and import reconciliation"
            )
        }
    }

    fun checkCommandRegistryIntegrity(): List<DeterministicFinding> {
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

    fun checkRedactionInvariant(): List<DeterministicFinding> {
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

    fun checkForbiddenPaths(paths: List<Path>): List<DeterministicFinding> {
        val banned = listOf(".gradle/", "build/", ".jar", ".atropos/secrets", ".git/")
        return paths.mapNotNull { path ->
            val normalized = path.toAbsolutePath().normalize()
            if (!normalized.startsWith(repoRoot)) return@mapNotNull null
            val relative = portablePath(repoRoot.relativize(normalized))
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

    fun checkArchitectureCompliance(paths: List<Path>): List<DeterministicFinding> {
        val inScope = paths.map { it.toAbsolutePath().normalize() }
            .filter {
                it.startsWith(repoRoot) && Files.isRegularFile(it) &&
                    it.fileName.toString().substringAfterLast('.', "") == "kt"
            }
        if (inScope.isEmpty()) return emptyList()

        val report = architectureComplianceChecker.checkFiles(inScope.map { it.toFile() })
        val severity = if (report.blocksBuild) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
        return report.violations.map { violation ->
            val file = runCatching {
                val normalized = Path.of(violation.path).toAbsolutePath().normalize()
                if (normalized.startsWith(repoRoot)) portablePath(repoRoot.relativize(normalized)) else violation.path
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

    fun checkPatchStructure(patchText: String): List<DeterministicFinding> {
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

    fun checkShellSafety(shellCommand: String): List<DeterministicFinding> {
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

    fun checkDloiAddress(address: String): List<DeterministicFinding> {
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

    private fun portablePath(path: Path): String = path.toString().replace('\\', '/')
}
