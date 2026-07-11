package atropos.core.verification

import atropos.ast.AstSymbolGraph
import atropos.cli.input.CommandRegistry
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentSmokeRunner
import atropos.core.security.RedactionFilter
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
    private val astGraph: AstSymbolGraph = AstSymbolGraph(repoRoot),
    private val smokeRunner: AgentSmokeRunner = AgentSmokeRunner(repoRoot),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun verify(
        sourcePaths: List<Path>,
        patchText: String? = null,
        shellCommand: String? = null,
        dloiAddress: String? = null
    ): DeterministicVerificationResult {
        val findings = mutableListOf<DeterministicFinding>()
        sourcePaths.forEach { path ->
            findings += checkSourceScope(path)
            if (path.extension == "kt" && Files.isRegularFile(path)) {
                findings += checkPackagePathInvariant(path)
                findings += checkDuplicateImports(path)
                findings += checkAstImpact(path)
            }
        }
        findings += checkCommandRegistryIntegrity()
        findings += checkRedactionInvariant()
        findings += checkForbiddenPaths(sourcePaths)
        patchText?.let { findings += checkPatchStructure(it) }
        shellCommand?.let { findings += checkShellSafety(it) }
        dloiAddress?.let { findings += checkDloiAddress(it) }
        return DeterministicVerificationResult(findings.filterNotNull())
    }

    private fun checkSourceScope(path: Path): List<DeterministicFinding> {
        val normalized = path.toAbsolutePath().normalize()
        if (!normalized.startsWith(repoRoot)) {
            return listOf(
                finding(
                    invariantId = "source_scope",
                    severity = DiagnosticSeverity.ERROR,
                    file = normalized.invariantSeparatorsPathString,
                    evidence = "path outside repository root",
                    remediation = "limit verification to repository files"
                )
            )
        }
        return emptyList()
    }

    private fun checkPackagePathInvariant(path: Path): List<DeterministicFinding> {
        val lines = path.readLines(StandardCharsets.UTF_8)
        val packageLine = lines.firstOrNull { it.trimStart().startsWith("package ") } ?: return emptyList()
        val packageName = packageLine.removePrefix("package ").trim()
        val relative = repoRoot.relativize(path).invariantSeparatorsPathString
        val expectedSuffix = packageName.replace('.', '/') + "/" + path.fileName
        return if (!relative.endsWith(expectedSuffix)) {
            listOf(
                finding(
                    invariantId = "package_path_invariant",
                    severity = DiagnosticSeverity.ERROR,
                    file = relative,
                    symbolOrLocation = packageName,
                    evidence = "expected path suffix $expectedSuffix",
                    remediation = "align Kotlin package with file path"
                )
            )
        } else {
            emptyList()
        }
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
        return if (commands.size != commands.distinct().size) {
            listOf(
                finding(
                    invariantId = "command_registry_integrity",
                    severity = DiagnosticSeverity.ERROR,
                    file = "src/main/kotlin/atropos/cli/input/CommandRegistry.kt",
                    evidence = "duplicate slash command entries detected",
                    remediation = "deduplicate command registry"
                )
            )
        } else {
            emptyList()
        }
    }

    private fun checkRedactionInvariant(): List<DeterministicFinding> {
        val sample = "Authorization: Bearer " + "A".repeat(24) + " sk-" + "B".repeat(24)
        val redacted = redactionFilter.redact(sample)
        return if (redacted.contains("A".repeat(24)) || redacted.contains("B".repeat(24))) {
            listOf(
                finding(
                    invariantId = "redaction",
                    severity = DiagnosticSeverity.ERROR,
                    file = "src/main/kotlin/atropos/core/security/RedactionFilter.kt",
                    evidence = "raw secret still present after redaction",
                    remediation = "expand redaction coverage"
                )
            )
        } else {
            emptyList()
        }
    }

    private fun checkForbiddenPaths(paths: List<Path>): List<DeterministicFinding> {
        val banned = listOf(".gradle/", "build/", ".jar", ".atropos/secrets", ".git/")
        return paths.mapNotNull { path ->
            val relative = repoRoot.relativize(path.toAbsolutePath().normalize()).invariantSeparatorsPathString
            banned.firstOrNull { token -> relative.contains(token) || relative.endsWith(token) }?.let { token ->
                finding(
                    invariantId = "forbidden_path",
                    severity = DiagnosticSeverity.ERROR,
                    file = relative,
                    evidence = "path matches forbidden token $token",
                    remediation = "remove forbidden file from deterministic scope"
                )
            }
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
        return try {
            dloiService.lookup(address)
            emptyList()
        } catch (failure: Exception) {
            listOf(
                finding(
                    invariantId = "dloi_address",
                    severity = DiagnosticSeverity.ERROR,
                    file = "docs/ATROPOS_CANONICAL_PHASES_1_11_AUTHORITY.md",
                    symbolOrLocation = address,
                    evidence = failure.message ?: failure.javaClass.simpleName,
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
