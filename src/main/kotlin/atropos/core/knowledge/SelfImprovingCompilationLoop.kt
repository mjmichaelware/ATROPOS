/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.knowledge

import atropos.core.verification.*
import atropos.core.verifier.ProbabilisticImmunityEngine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.*
import java.time.Instant
import java.util.concurrent.*

fun interface VerificationProcessExecutor {
    fun execute(request: VerificationRequest): ProcessExecution
}

class JdkVerificationProcessExecutor : VerificationProcessExecutor {
    override fun execute(request: VerificationRequest): ProcessExecution {
        val started = System.nanoTime()
        val root = request.workspace.toAbsolutePath().normalize()

        val process = try {
            ProcessBuilder(request.command)
                .directory(root.toFile())
                .apply {
                    environment().keys.removeIf { key ->
                        val name = key.uppercase()
                        name.contains("TOKEN") ||
                            name.contains("SECRET") ||
                            name.contains("PASSWORD") ||
                            name.endsWith("_KEY") ||
                            name.contains("CREDENTIAL")
                    }
                }
                .start()
        } catch (failure: Exception) {
            return ProcessExecution(
                request.command,
                null,
                false,
                elapsed(started),
                CapturedText("", false),
                CapturedText("", false),
                "${failure.javaClass.simpleName}: ${failure.message ?: "launch failed"}"
            )
        }

        val pumps = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "atropos-verification-stream").apply { isDaemon = true }
        }

        val stdout = pumps.submit<CapturedText> {
            collect(process.inputStream, request.maxOutputBytes, request.maxOutputLines)
        }
        val stderr = pumps.submit<CapturedText> {
            collect(process.errorStream, request.maxOutputBytes, request.maxOutputLines)
        }

        val finished = process.waitFor(request.timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) terminate(process)

        val out = futureResult(stdout)
        val err = futureResult(stderr)
        pumps.shutdownNow()

        return ProcessExecution(
            command = request.command,
            exitCode = if (finished) process.exitValue() else null,
            timedOut = !finished,
            durationMillis = elapsed(started),
            stdout = out,
            stderr = err
        )
    }

    private fun collect(
        input: InputStream,
        maximumBytes: Int,
        maximumLines: Int
    ): CapturedText {
        val captured = ByteArrayOutputStream(minOf(maximumBytes, 8192))
        val buffer = ByteArray(8192)
        var lines = 0
        var truncated = false
        var read: Int

        input.use { stream ->
            while (stream.read(buffer).also { read = it } != -1) {
                for (index in 0 until read) {
                    val value = buffer[index]
                    if (captured.size() < maximumBytes && lines < maximumLines) {
                        captured.write(value.toInt())
                        if (value.toInt() == '\n'.code) lines++
                    } else {
                        truncated = true
                    }
                }
            }
        }

        return CapturedText(
            captured.toByteArray().toString(Charsets.UTF_8),
            truncated
        )
    }

    private fun futureResult(future: Future<CapturedText>): CapturedText =
        try {
            future.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            future.cancel(true)
            CapturedText("", true)
        }

    private fun terminate(process: Process) {
        process.toHandle().descendants().forEach { it.destroy() }
        process.destroy()

        if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
            process.toHandle().descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            process.waitFor(1, TimeUnit.SECONDS)
        }
    }

    private fun elapsed(started: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
}

class AtomicRewardRecorder(
    workspace: Path,
    relativePath: Path = Path.of(".atropos", "verification", "rewards.tsv")
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

        val line = listOf(
            Instant.now().toString(),
            event.scope.name,
            event.reward.toString(),
            event.exitCode?.toString() ?: "none",
            event.timedOut.toString(),
            event.durationMillis.toString()
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
        val reward = if (
            !execution.timedOut &&
            execution.launchError == null &&
            execution.exitCode == 0
        ) 1.0 else -1.0

        val persistenceError = try {
            rewardRecorder.record(
                RewardEvent(
                    request.scope,
                    reward,
                    execution.exitCode,
                    execution.timedOut,
                    execution.durationMillis
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
