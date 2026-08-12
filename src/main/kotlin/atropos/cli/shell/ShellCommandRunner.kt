/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.shell

import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ShellActionProposals
import atropos.core.policy.ToolExecutionResult
import atropos.core.policy.TypedToolExecutor
import atropos.core.security.RedactionFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ShellCommandResult(
    val command: List<String>,
    val cwd: String,
    val exitCode: Int,
    val elapsedMs: Long,
    val timedOut: Boolean,
    val output: String,
    /**
     * How bounded agency disposed of the proposal, when one was made.
     *
     * Carried so a refusal is a typed outcome a compositor can act on — an
     * `APPROVAL_REQUIRED` result is something a future dialog can offer to
     * escalate, which a bare exit code could never express. `null` for paths
     * that make no proposal at all, such as `cd` and the empty command.
     */
    val disposition: AgencyDisposition? = null,
    val proposalId: String? = null,
    val policyReason: String? = null
) {
    val passed: Boolean = exitCode == 0 && !timedOut
}

/**
 * Runs bounded local shell commands.
 *
 * This runner holds no execution authority. It states an intent as an
 * [ActionProposal], hands it to [TypedToolExecutor], and renders whatever the
 * system decided — it never asks the policy engine anything directly. That is
 * the externally-bounded-agency contract: the caller proposes, the system
 * decides, and nothing reaches [ProcessBuilder] that was not authorised.
 */
class ShellCommandRunner(
    initialDirectory: Path = Path.of(System.getProperty("user.dir") ?: "."),
    private val timeoutMs: Long = 15_000L,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val agency: TypedToolExecutor = TypedToolExecutor(
        BoundedAgencyGate(ExecutionPolicyEngine(initialDirectory.toAbsolutePath().normalize()))
    ),
    /**
     * Process spawn seam. Exists so a test can prove a refused proposal never
     * reaches a real [ProcessBuilder]; production always uses the default.
     */
    private val spawn: (List<String>, File) -> Process = { command, directory ->
        BoundedProcessRunner().start(command, directory.toPath())
    }
) {
    private var cwd: File = initialDirectory.toFile().canonicalFile

    fun currentDirectory(): String = cwd.path

    fun changeDirectory(target: String?): ShellCommandResult {
        val rawTarget = target?.trim().orEmpty().ifBlank { "~" }
        val next = resolveDirectory(rawTarget)

        return if (next != null && next.isDirectory) {
            cwd = next.canonicalFile
            ShellCommandResult(
                command = listOf("cd", rawTarget),
                cwd = cwd.path,
                exitCode = 0,
                elapsedMs = 0L,
                timedOut = false,
                output = "cwd: ${cwd.path}"
            )
        } else {
            ShellCommandResult(
                command = listOf("cd", rawTarget),
                cwd = cwd.path,
                exitCode = 1,
                elapsedMs = 0L,
                timedOut = false,
                output = "cd: no such directory: $rawTarget"
            )
        }
    }

    fun list(args: List<String>): ShellCommandResult =
        run(listOf("ls") + args)

    fun gitStatus(): ShellCommandResult =
        run(listOf("git", "status", "--short"))

    fun run(command: List<String>): ShellCommandResult {
        val cleaned = command.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) {
            return ShellCommandResult(
                command = emptyList(),
                cwd = cwd.path,
                exitCode = 2,
                elapsedMs = 0L,
                timedOut = false,
                output = "shell: empty command"
            )
        }

        val proposal = ShellActionProposals.forCommand(cleaned, cwd.toPath())

        // The executor lambda is the only place a process can be born, and the
        // gate decides whether it is ever invoked.
        var executed: ShellCommandResult? = null
        val outcome = agency.execute(proposal) {
            val result = spawnAndCollect(cleaned, proposal)
            executed = result
            result.output
        }

        return executed ?: refusal(cleaned, proposal, outcome)
    }

    /**
     * Refusal rendered from the system's decision.
     *
     * `APPROVAL_REQUIRED` keeps its own exit code: it is not a denial, it is an
     * action awaiting an authority that has not been asked yet, and collapsing
     * it into the blocked code would erase the difference the approval flow
     * depends on.
     */
    private fun refusal(
        command: List<String>,
        proposal: ActionProposal,
        outcome: ToolExecutionResult
    ): ShellCommandResult = ShellCommandResult(
        command = command,
        cwd = cwd.path,
        exitCode = when (outcome.disposition) {
            AgencyDisposition.APPROVAL_REQUIRED -> EXIT_APPROVAL_REQUIRED
            else -> EXIT_POLICY_BLOCKED
        },
        elapsedMs = 0L,
        timedOut = false,
        output = outcome.refusalReason ?: outcome.policyDecision.reason,
        disposition = outcome.disposition,
        proposalId = proposal.id,
        policyReason = outcome.policyDecision.reason
    )

    private fun spawnAndCollect(cleaned: List<String>, proposal: ActionProposal): ShellCommandResult {
        val started = System.currentTimeMillis()
        val output = ByteArrayOutputStream()

        return try {
            val process = spawn(cleaned, cwd)

            val reader = thread(
                start = true,
                isDaemon = true,
                name = "atropos-shell-output"
            ) {
                process.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        synchronized(output) {
                            if (output.size() < MAX_OUTPUT_BYTES) {
                                val remaining = MAX_OUTPUT_BYTES - output.size()
                                output.write(buffer, 0, minOf(read, remaining))
                            }
                        }
                    }
                }
            }

            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
            }
            reader.join(1000L)

            val elapsed = System.currentTimeMillis() - started
            val exit = if (completed) process.exitValue() else 124
            ShellCommandResult(
                command = cleaned,
                cwd = cwd.path,
                exitCode = exit,
                elapsedMs = elapsed,
                timedOut = !completed,
                output = cleanOutput(output),
                disposition = AgencyDisposition.ALLOWED,
                proposalId = proposal.id
            )
        } catch (failure: Exception) {
            ShellCommandResult(
                command = cleaned,
                cwd = cwd.path,
                exitCode = 127,
                elapsedMs = System.currentTimeMillis() - started,
                timedOut = false,
                output = failure.message ?: failure.javaClass.simpleName,
                disposition = AgencyDisposition.ALLOWED,
                proposalId = proposal.id
            )
        }
    }

    fun render(result: ShellCommandResult): String {
        val command = result.command.joinToString(" ")
        val status = if (result.timedOut) "timeout" else result.exitCode.toString()
        val body = result.output
            .lineSequence()
            .take(MAX_OUTPUT_LINES)
            .joinToString("\n")
            .ifBlank { "(no output)" }

        return buildString {
            appendLine("shell:")
            appendLine("  cwd: ${result.cwd}")
            appendLine("  command: $command")
            appendLine("  exit: $status")
            appendLine("  elapsed_ms: ${result.elapsedMs}")
            // Only refusals carry a disposition line: an allowed command renders
            // exactly as it always has, so nothing that already worked changes.
            result.disposition
                ?.takeIf { it != AgencyDisposition.ALLOWED }
                ?.let {
                    appendLine("  disposition: ${it.name.lowercase()}")
                    result.proposalId?.let { id -> appendLine("  proposal: $id") }
                }
            appendLine("  output:")
            body.lines().forEach { appendLine("    $it") }
        }.trimEnd()
    }

    private fun resolveDirectory(target: String): File? {
        val home = System.getProperty("user.home") ?: ""
        val expanded = when {
            target == "~" -> home
            target.startsWith("~/") -> home + target.removePrefix("~")
            else -> target
        }

        val candidate = File(expanded)
        return if (candidate.isAbsolute) candidate else File(cwd, expanded)
    }

    private fun cleanOutput(output: ByteArrayOutputStream): String {
        val text = synchronized(output) {
            output.toByteArray().toString(Charset.defaultCharset())
        }
        val normalized = text
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trimEnd()

        return redactionFilter.redact(normalized).take(MAX_RENDER_CHARS)
    }

    private companion object {
        const val MAX_OUTPUT_BYTES = 64 * 1024
        const val MAX_RENDER_CHARS = 12_000
        const val MAX_OUTPUT_LINES = 120

        /** Refused by policy. Unchanged from before bounded agency. */
        const val EXIT_POLICY_BLOCKED = 126

        /** Withheld pending an authority that has not been asked. Distinct on purpose. */
        const val EXIT_APPROVAL_REQUIRED = 125
    }
}
