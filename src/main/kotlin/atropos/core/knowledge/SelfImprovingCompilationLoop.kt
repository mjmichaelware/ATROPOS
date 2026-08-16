/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.knowledge

import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import atropos.core.verification.*
import atropos.core.verifier.ProbabilisticImmunityEngine
import java.nio.file.*
import java.time.Instant

fun interface VerificationProcessExecutor {
    fun execute(request: VerificationRequest): ProcessExecution
}

class JdkVerificationProcessExecutor : VerificationProcessExecutor {
    override fun execute(request: VerificationRequest): ProcessExecution {
        val root = request.workspace.toAbsolutePath().normalize()
        val result = BoundedProcessRunner().run(
            command = request.command,
            directory = root,
            timeoutMillis = request.timeoutMillis.coerceAtMost(1_800_000L),
            maxOutputBytes = request.maxOutputBytes.coerceAtMost(256 * 1024),
            maxOutputLines = request.maxOutputLines.coerceAtMost(4_000),
            removeEnvironmentKeys = System.getenv().keys.filter { key ->
                val name = key.uppercase()
                name.contains("TOKEN") || name.contains("SECRET") ||
                    name.contains("PASSWORD") || name.endsWith("_KEY") ||
                    name.contains("CREDENTIAL")
            }.toSet()
        )
        return ProcessExecution(
            command = request.command,
            exitCode = result.exitCode,
            timedOut = result.timedOut,
            durationMillis = result.durationMillis,
            stdout = CapturedText(result.stdout, result.outputTruncated),
            stderr = CapturedText(result.stderr, result.outputTruncated),
            launchError = result.launchError
        )
    }
}

class AtomicRewardRecorder(
    workspace: Path,
    relativePath: Path = Path.of(".atropos", "verification", "rewards.tsv"),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(workspace.resolve(".atropos/memory").toFile()),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) : RewardRecorder {
    private val root = workspace.toAbsolutePath().normalize()
    private val target = root.resolve(relativePath).normalize()

    init {
        require(target.startsWith(root)) { "Reward path escapes workspace" }
    }

    override fun record(event: RewardEvent) {
        Files.createDirectories(target.parent)

        val previous = if (Files.isRegularFile(target)) {
            Files.readAllLines(target).takeLast(499)
        } else {
            emptyList()
        }

        val trace = redactionFilter.compact(event.trace, maxChars = 400)
            .replace("\t", " ")
            .replace("\n", " ")
        val line = listOf(
            Instant.now().toString(),
            event.scope.name,
            event.reward.toString(),
            event.exitCode?.toString() ?: "none",
            event.timedOut.toString(),
            event.durationMillis.toString(),
            trace
        ).joinToString("\t")

        val temporary = Files.createTempFile(target.parent, "rewards-", ".tmp")
        Files.write(
            temporary,
            previous + line,
            StandardOpenOption.TRUNCATE_EXISTING
        )

        try {
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary,
                target,
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        val direction = if (event.reward >= 0.0) "reward" else "penalty"
        val exitCode = event.exitCode?.toString() ?: "none"
        memoryStore.rememberReward(
            subjectId = event.scope.name.lowercase(),
            title = "verification $direction ${event.reward}",
            body = "scope=${event.scope.name.lowercase()} exitCode=$exitCode timedOut=${event.timedOut} durationMs=${event.durationMillis}",
            tags = listOf("verification", direction, event.scope.name.lowercase())
        )
    }
}

class SelfImprovingCompilationLoop(
    private val processExecutor: VerificationProcessExecutor =
        JdkVerificationProcessExecutor(),
    private val analyzer: ProbabilisticImmunityEngine =
        ProbabilisticImmunityEngine(),
    private val rewardRecorder: RewardRecorder
) : VerificationRunner {
    override fun executeVerification(
        request: VerificationRequest
    ): VerificationResult {
        val execution = processExecutor.execute(request)
        val report = analyzer.analyze(execution)
        val successRate = if (
            !execution.timedOut &&
            execution.launchError == null &&
            execution.exitCode == 0
        ) 1.0 else 0.0
        // A verification outcome, not a throughput ratio.
        //
        // RewardCalculator.computeReward answers a different question — success
        // per second per unit cost, for comparing providers — and it is
        // unbounded: a run that finishes in ten milliseconds scores 100. This
        // signal is read as a sign by the recorder, which files anything below
        // zero as a penalty, so scale and direction are the whole meaning.
        val reward = if (successRate > 0.0) 1.0 else -1.0

        val persistenceError = try {
            rewardRecorder.record(
                RewardEvent(
                    request.scope,
                    reward,
                    execution.exitCode,
                    execution.timedOut,
                    execution.durationMillis,
                    execution.stderr.text,
                    successRate = successRate,
                    cost = 1.0
                )
            )
            null
        } catch (failure: Exception) {
            "${failure.javaClass.simpleName}: ${failure.message ?: "reward persistence failed"}"
        }

        return VerificationResult(
            request,
            execution,
            report,
            reward,
            persistenceError
        )
    }
}
