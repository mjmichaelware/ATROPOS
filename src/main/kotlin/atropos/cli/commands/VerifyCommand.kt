/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.knowledge.AtomicRewardRecorder
import atropos.core.knowledge.NonBlockingRewardRecorder
import atropos.core.knowledge.SelfImprovingCompilationLoop
import atropos.core.verification.*
import atropos.core.platform.SharedCore
import atropos.core.acceptance.FinalSD1SD2Acceptance
import atropos.core.agent.AgentContextCollector
import atropos.core.memory.LocalMemoryStore
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import java.nio.file.Files
import java.nio.file.Path

sealed class VerifyCommandOutcome {
    data class Completed(val result: VerificationResult) : VerifyCommandOutcome()
    data class Invalid(val message: String) : VerifyCommandOutcome()

    /**
     * A structural pass ran. Carries the findings rather than a boolean so a
     * caller can act on which rule broke, not only on that one did.
     */
    data class Structural(
        val findings: List<atropos.core.verification.DeterministicFinding>
    ) : VerifyCommandOutcome()
}

fun interface VerifyCommandHandler {
    fun execute(tokens: List<String>): VerifyCommandOutcome
}

class VerifyCommand(
    private val ui: AnsiTerminalEngine,
    workspace: Path = Path.of("."),
    private val compilerExecutable: String = "kotlinc",
    private val runner: VerificationRunner = SelfImprovingCompilationLoop(
        rewardRecorder = NonBlockingRewardRecorder(AtomicRewardRecorder(workspace))
    )
) : VerifyCommandHandler {
    private val root = workspace.toAbsolutePath().normalize()
    private val deterministicVerifier = DeterministicVerifier(root)
    private val gateReachabilityChecker = GateReachabilityChecker()
    private val foundationAcceptance = FinalSD1SD2Acceptance(
        contextCollector = AgentContextCollector(repoRoot = root),
        memoryStore = LocalMemoryStore(root.resolve(".atropos/memory").toFile()),
        territoryService = TerritoryService(TerritoryStore(root))
    )

    override fun execute(tokens: List<String>): VerifyCommandOutcome {
        if (tokens.size != 2) {
            return invalid("usage: /verify <narrow|wide|structural>")
        }

        // `structural` answers a different question from the other two and so
        // takes a different path: narrow and wide compile and run, this reads
        // the tree. Folding it into a scope would have made it share the
        // compile step it does not need and cannot use.
        if (tokens[1].equals("structural", ignoreCase = true)) {
            return renderStructural()
        }

        val scope = when (tokens[1].lowercase()) {
            "narrow" -> VerificationScope.NARROW
            "wide" -> VerificationScope.WIDE
            else -> return invalid("unknown verification scope: ${tokens[1]}")
        }

        val request = try {
            createRequest(scope)
        } catch (failure: Exception) {
            return invalid(failure.message ?: "unable to create verification request")
        }

        val gateReachability = gateReachabilityChecker.check(root.resolve("src/main/kotlin").toFile())
        if (!gateReachability.predicateHolds) {
            ui.renderError(gateReachability.render())
            return invalid("execution gate reachability failed")
        }
        val sharedCore = SharedCore.scan(root.resolve("src/main/kotlin/atropos/core"))
        if (!sharedCore.isShareable) {
            ui.renderError(sharedCore.render())
            return invalid("shared-core portability verification failed")
        }
        if (!foundationAcceptance.evaluateSD1SD2Readiness()) {
            ui.renderNotice("foundation acceptance remains incomplete; continuing with scoped verification")
        }

        RiskyStdlibScanner.scan(request.command.drop(3).map(Path::of).map(Path::toFile))
            .take(12)
            .forEach { usage ->
                ui.renderNotice("compatibility advisory: ${usage.file}:${usage.line} ${usage.pattern}")
            }

        val deterministic = deterministicVerifier.verify(
            sourcePaths = request.command.drop(3).map(Path::of)
        )
        if (!deterministic.passed) {
            ui.renderError("deterministic verification failed")
            deterministic.findings.forEach { finding ->
                ui.renderNotice(
                    "${finding.invariantId} ${finding.severity.name.lowercase()}: ${finding.evidence} :: ${finding.remediation}"
                )
            }
            return invalid("deterministic verification failed")
        }

        // Keep the command's scoped verification report grounded in the same
        // source snapshot and authority checks as the verifier itself.
        val snapshot = Rule127Snapshot.formatSnapshot(deterministic.render().toByteArray())
        val batch = BatchReporter.report(
            before = emptyList(),
            after = request.command.drop(3).flatMap { path ->
                runCatching { Files.readAllLines(Path.of(path)) }.getOrDefault(emptyList())
            }
        )
        val precedence = PrecedenceLattice().checkPrecedence("USER", 3, "WRITE")
        val velocity = AcceptanceVelocity.calculate(
            listOf(VerificationEvent(java.time.Instant.now(), "verify-${scope.name.lowercase()}", true))
        )
        ui.renderNotice(
            "verification evidence: snapshots=${snapshot.size} lines=${batch.physicalLines} " +
                "precedence=$precedence velocity=${"%.2f".format(velocity)}"
        )

        ui.startSpinner("Verifying ${scope.name.lowercase()} scope")
        val result = try {
            runner.executeVerification(request)
        } finally {
            ui.stopSpinner()
        }

        ui.renderVerificationResult(result)
        return VerifyCommandOutcome.Completed(result)
    }

    /**
     * `/verify structural` — the design rules, checked against the source.
     *
     * Reported as a completed verification either way. A structural violation
     * is a finding, not a command failure: the command did exactly what it was
     * asked to and the answer was "no". Returning [VerifyCommandOutcome.Invalid]
     * for it would make a working audit indistinguishable from a mistyped one.
     */
    private fun renderStructural(): VerifyCommandOutcome {
        val findings = deterministicVerifier.verifyStructure().findings
        val errors = findings.filter { it.severity == atropos.core.verification.DiagnosticSeverity.ERROR }
        val body = buildString {
            appendLine("STRUCTURAL — ${findings.size} finding(s), ${errors.size} blocking")
            findings.forEach { finding ->
                appendLine(
                    "  ${finding.severity.name.lowercase()} ${finding.invariantId} " +
                        "${finding.file ?: finding.symbolOrLocation ?: ""}: ${finding.evidence}"
                )
            }
        }.trimEnd()
        if (errors.isEmpty()) ui.renderNotice(body) else ui.renderError(body)
        return VerifyCommandOutcome.Structural(findings)
    }

    private fun invalid(message: String): VerifyCommandOutcome.Invalid {
        ui.renderError(message)
        return VerifyCommandOutcome.Invalid(message)
    }

    private fun createRequest(scope: VerificationScope): VerificationRequest {
        val outputDirectory = root.resolve(".atropos/verification").normalize()
        require(outputDirectory.startsWith(root))
        Files.createDirectories(outputDirectory)

        val sources = when (scope) {
            VerificationScope.NARROW -> narrowSources()
            VerificationScope.WIDE -> allSources()
        }

        require(sources.isNotEmpty()) { "No Kotlin sources selected" }

        val output = outputDirectory.resolve(
            if (scope == VerificationScope.NARROW) "narrow.jar" else "wide.jar"
        )

        return VerificationRequest(
            scope = scope,
            workspace = root,
            command = buildList {
                add(compilerExecutable)
                add("-d")
                add(output.toString())
                addAll(sources.map(Path::toString))
            },
            timeoutMillis = if (scope == VerificationScope.NARROW) 120_000 else 300_000
        )
    }

    private fun narrowSources(): List<Path> {
        val relative = listOf(
            "src/main/kotlin/atropos/core/verification/VerificationModels.kt",
            "src/main/kotlin/atropos/core/verifier/ProbabilisticImmunityEngine.kt",
            "src/main/kotlin/atropos/core/knowledge/SelfImprovingCompilationLoop.kt",
            "src/main/kotlin/atropos/core/Config.kt",
            "src/main/kotlin/atropos/cli/config/ConfigurationManager.kt",
            "src/main/kotlin/atropos/cli/session/QuotaSessionTracker.kt",
            "src/main/kotlin/atropos/cli/ui/TerminalCanvas.kt",
            "src/main/kotlin/atropos/cli/ui/SpinnerEngine.kt",
            "src/main/kotlin/atropos/cli/ui/AnsiTerminalEngine.kt",
            "src/main/kotlin/atropos/cli/commands/VerifyCommand.kt"
        )

        return relative.map(root::resolve).filter(Files::isRegularFile)
    }

    private fun allSources(): List<Path> {
        val sourceRoot = root.resolve("src/main/kotlin").normalize()
        require(sourceRoot.startsWith(root))

        val stream = Files.walk(sourceRoot)
        return try {
            stream.filter {
                Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt")
            }.sorted().toList()
        } finally {
            stream.close()
        }
    }

    private fun render(result: VerificationResult) {
        if (result.successful) {
            ui.renderNotice(
                "verification passed | ${result.execution.durationMillis} ms | reward +1.0"
            )
        } else {
            ui.renderError(
                "verification failed | ${result.report.classification} | " +
                    "${result.execution.durationMillis} ms | reward -1.0"
            )
        }

        result.report.diagnostics.take(12).forEach { diagnostic ->
            val location = listOfNotNull(
                diagnostic.path,
                diagnostic.line?.toString(),
                diagnostic.column?.toString()
            ).joinToString(":")

            val prefix = if (location.isEmpty()) "" else "$location: "
            ui.renderNotice(
                "$prefix${diagnostic.severity.name.lowercase()}: ${diagnostic.message}"
            )
        }

        result.report.recommendations.forEach {
            ui.renderNotice("recommendation: $it")
        }

        if (result.execution.stdout.truncated || result.execution.stderr.truncated) {
            ui.renderNotice("verification output was truncated at configured bounds")
        }

        result.persistenceError?.let {
            ui.renderError("reward persistence failed: $it")
        }
    }
}
