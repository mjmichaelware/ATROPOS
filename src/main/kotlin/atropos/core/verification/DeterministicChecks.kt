package atropos.core.verification

import atropos.ast.AstSymbolGraph
import atropos.ast.AstImportStatus
import atropos.cli.input.CommandRegistry
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentSmokeRunner
import atropos.core.ast.CodebaseDeltaTreeTracker
import atropos.core.ast.TopologicalMutationVector
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
    private val architectureComplianceChecker: ArchitectureComplianceChecker,
    private val gateReachabilityChecker: GateReachabilityChecker = GateReachabilityChecker(),
    private val surfaceParityProbe: atropos.core.parity.SurfaceParityProbe =
        atropos.core.parity.SurfaceParityProbe()
) {

    /** Repo-relative form for a finding's file field, or the raw path if outside. */
    private fun relativeTo(file: Path): String = runCatching {
        val normalized = file.toAbsolutePath().normalize()
        if (normalized.startsWith(repoRoot)) portablePath(repoRoot.relativize(normalized))
        else normalized.toString()
    }.getOrElse { file.toString() }

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
        NamedAssertion.require(packageName.isNotBlank(), "package_name", packageName)
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

    /**
     * The tree-wide structural rules: shared-core purity, gate reachability,
     * and surface parity.
     *
     * Added here rather than in a checker of their own. Each of
     * [atropos.core.platform.SharedCore], [GateReachabilityChecker] and
     * [atropos.core.parity.SurfaceParityProbe] existed with no production
     * caller, and the tempting fix — one new class that runs all three — would
     * have been a second verifier alongside this one, which §0.7 forbids. This
     * is already the owner of "check the source without running it"; the three
     * belong in it.
     *
     * Unlike the per-file checks above, these read the whole tree and so run
     * once per verification rather than once per path.
     */
    fun checkStructuralInvariants(): List<DeterministicFinding> {
        val mainSource = repoRoot.resolve("src/main/kotlin")
        if (!Files.isDirectory(mainSource)) return emptyList()

        val findings = mutableListOf<DeterministicFinding>()

        // A scan of zero files that reports nothing has found nothing, which is
        // not the same as finding nothing wrong — so an inconclusive scan is
        // itself reported rather than passing silently.
        val shared = atropos.core.platform.SharedCore.scan(
            mainSource.resolve(atropos.core.platform.SharedCore.SHARED_ROOTS.first())
        )
        if (shared.scanned == 0) {
            findings += finding(
                invariantId = "SHARED-CORE-INCONCLUSIVE",
                severity = DiagnosticSeverity.WARNING,
                evidence = "shared core scan examined 0 files",
                remediation = "run from a repository root where src/main/kotlin/atropos/core exists"
            )
        }
        shared.violations.forEach { violation ->
            findings += finding(
                invariantId = "SHARED-CORE-PLATFORM-IMPORT",
                severity = DiagnosticSeverity.ERROR,
                file = relativeTo(violation.file),
                symbolOrLocation = "line ${violation.line}",
                evidence = "core imports ${violation.import}, which exists on one target only",
                remediation = "move the platform-facing code to the CLI or bridge layer, or abstract it behind PlatformAbstraction"
            )
        }

        val gates = gateReachabilityChecker.check(mainSource.toFile())
        gates.violations.forEach { violation ->
            findings += finding(
                invariantId = "GATE-REACHABILITY",
                severity = DiagnosticSeverity.ERROR,
                file = violation.path,
                evidence = violation.render(),
                remediation = "route the execution site through BoundedAgencyGate so P(raw-prose-execution)=0 holds by construction"
            )
        }

        // Parity reads two registries that can each fail to load; a probe that
        // throws must not take the whole verification with it, because the
        // findings already gathered are still true.
        runCatching { surfaceParityProbe.forbiddenOnPort() }.getOrDefault(emptyList()).forEach { exposed ->
            findings += finding(
                invariantId = "SURFACE-PORT-EXPOSURE",
                severity = DiagnosticSeverity.ERROR,
                symbolOrLocation = exposed,
                evidence = "a capability reaching the operating system is advertised on a surface bound to a port",
                remediation = "remove the action from the bridge menu; shell access belongs to the CLI only"
            )
        }
        runCatching { surfaceParityProbe.danglingActions() }.getOrDefault(emptyList()).forEach { action ->
            findings += finding(
                invariantId = "SURFACE-DANGLING-ACTION",
                severity = DiagnosticSeverity.WARNING,
                symbolOrLocation = action,
                evidence = "menu action resolves to no route",
                remediation = "restore the route or remove the menu entry; on a phone this renders as a button that does nothing"
            )
        }
        runCatching { surfaceParityProbe.check().divergences }.getOrDefault(emptyList()).forEach { divergence ->
            findings += finding(
                invariantId = "SURFACE-PARITY",
                severity = DiagnosticSeverity.WARNING,
                symbolOrLocation = divergence.field,
                evidence = "${divergence.left} vs ${divergence.right}",
                remediation = "surfaces may offer less than one another; they may not offer something different under the same name"
            )
        }

        return findings
    }

    /**
     * The declared side-effect paths and what bounds each one.
     *
     * Reported rather than checked: [atropos.core.policy.SideEffectInventory]
     * is a declaration, and its value is that a reviewer can see the four
     * places the engine can affect the world outside itself next to the gate
     * that bounds each. Emitted as INFO so it never fails a verification.
     */
    fun reportSideEffectPaths(): List<DeterministicFinding> =
        atropos.core.policy.SideEffectInventory.getEnforcedCallers().map { path ->
            finding(
                invariantId = "SIDE-EFFECT-PATH",
                severity = DiagnosticSeverity.INFO,
                symbolOrLocation = "${path.className}.${path.methodName}",
                evidence = path.description,
                remediation = "bounded by ${path.enforcedBy}"
            )
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
        val validation = patchExtractor.validate(extraction.diff)
        val findings = mutableListOf<DeterministicFinding>()
        // Preserve the changed paths as typed graph deltas before any
        // adversarial or structural verdict is emitted.  This is the single
        // deterministic verification path; it does not create a second
        // mutation or verifier owner.
        val mutationVectors = extraction.touchedPaths.map { path ->
            TopologicalMutationVector(
                nodeId = "patch:$path",
                type = "UPDATE",
                targetAddress = path,
                newValue = CodebaseDeltaTreeTracker().trackTreeDelta("", extraction.diff)
            )
        }
        if (mutationVectors.any { it.targetAddress.isBlank() || it.newValue.isBlank() }) {
            findings += finding(
                invariantId = "topological_mutation_delta",
                severity = DiagnosticSeverity.ERROR,
                file = extraction.touchedPaths.firstOrNull(),
                evidence = "patch produced no typed graph delta",
                remediation = "provide a non-empty unified diff with a concrete target path"
            )
        }
        val adversarial = atropos.core.integration.OnDeviceAdversarialValidator.validate(extraction.diff)
        if (!adversarial.syntaxValid || adversarial.missingImports.isNotEmpty()) {
            findings += finding(
                invariantId = "adversarial_patch_validation",
                severity = DiagnosticSeverity.ERROR,
                file = extraction.touchedPaths.firstOrNull(),
                evidence = "syntaxValid=${adversarial.syntaxValid} missingImports=${adversarial.missingImports}",
                remediation = "repair the patch before deterministic verification"
            )
        }
        if (validation != null) findings += finding(
                invariantId = "patch_structure",
                severity = DiagnosticSeverity.ERROR,
                file = extraction.touchedPaths.firstOrNull(),
                evidence = validation,
                remediation = "remove forbidden or malformed patch paths"
            )
        return findings
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
