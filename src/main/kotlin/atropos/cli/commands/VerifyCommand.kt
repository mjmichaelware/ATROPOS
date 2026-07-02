/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.knowledge.AtomicRewardRecorder
import atropos.core.knowledge.SelfImprovingCompilationLoop
import atropos.core.verification.*
import java.nio.file.Files
import java.nio.file.Path

sealed class VerifyCommandOutcome {
    data class Completed(val result: VerificationResult) : VerifyCommandOutcome()
    data class Invalid(val message: String) : VerifyCommandOutcome()
}

fun interface VerifyCommandHandler {
    fun execute(tokens: List<String>): VerifyCommandOutcome
}

class VerifyCommand(
    private val ui: AnsiTerminalEngine,
    workspace: Path = Path.of("."),
    private val compilerExecutable: String = "kotlinc",
    private val runner: VerificationRunner = SelfImprovingCompilationLoop(
        rewardRecorder = AtomicRewardRecorder(workspace)
    )
) : VerifyCommandHandler {
    private val root = workspace.toAbsolutePath().normalize()

    override fun execute(tokens: List<String>): VerifyCommandOutcome {
        if (tokens.size != 2) {
            return invalid("usage: /verify <narrow|wide>")
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

        ui.startSpinner("Verifying ${scope.name.lowercase()} scope")
        val result = try {
            runner.executeVerification(request)
        } finally {
            ui.stopSpinner()
        }

        ui.renderVerificationResult(result)
        return VerifyCommandOutcome.Completed(result)
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
