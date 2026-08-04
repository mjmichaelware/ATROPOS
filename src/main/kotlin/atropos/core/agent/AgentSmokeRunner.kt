package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ActionActor
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.VerificationActionProposals
import atropos.core.security.RedactionFilter
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class AgentSmokeExecutionResult(
    val command: String,
    val passed: Boolean,
    val exitCode: Int? = null,
    val durationMillis: Long = 0L,
    val stdout: String = "",
    val stderr: String = "",
    val refusalReason: String? = null,
    val failure: AgentExecutionFailure? = null,
    val outputTruncated: Boolean = false
) {
    fun summary(): String = when {
        passed -> "passed (exit ${exitCode ?: "?"}, ${durationMillis} ms)"
        exitCode != null -> "failed (exit ${exitCode}, ${durationMillis} ms)"
        refusalReason != null -> "refused: ${refusalReason.trim()}"
        else -> "failed (exit ${exitCode ?: "?"}, ${durationMillis} ms)"
    }
}

class AgentSmokeRunner(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val timeoutMillis: Long = (System.getenv("ATROPOS_SMOKE_TIMEOUT_SECONDS") ?: "120").toLongOrNull()
        ?.coerceAtLeast(10)?.times(1000) ?: 120_000L,
    private val maxOutputBytes: Int = 48 * 1024,
    private val maxOutputLines: Int = 1_000,
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) {
    fun validate(command: String): String? {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return "smoke command is blank"
        if (trimmed.length > 500) return "smoke command too long"
        if (trimmed.any { it == '\n' || it == '\r' }) return "smoke command must be a single line"

        val lower = trimmed.lowercase()
        if (listOf("&&", "||", ";", "|", "`", "\$(", ">", "<", "\\", "&").any { it in trimmed }) {
            return "smoke command refuses shell chaining or redirects"
        }
        if (
            lower.contains("curl ") ||
                lower.contains("wget ") ||
                lower.contains("ssh ") ||
                lower.contains("scp ") ||
                lower.contains("sftp ") ||
                lower.contains("rsync ") ||
                lower.contains("nc ") ||
                lower.contains("ncat ") ||
                lower.contains("netcat ") ||
                lower.contains("sudo ") ||
                lower.contains("su ") ||
                lower.contains("rm ") ||
                lower.contains("kill ") ||
                lower.contains("pkill ") ||
                lower.contains("git push") ||
                lower.contains("git commit") ||
                lower.contains("git fetch") ||
                lower.contains("git pull") ||
                lower.contains("git reset") ||
                lower.contains("git clean")
        ) {
            return "smoke command refuses dangerous operations"
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = tokens.firstOrNull() ?: return "smoke command is blank"
        if (tokens.drop(1).any(::isUnboundedPathToken)) {
            return "smoke command refuses absolute or parent-traversal paths"
        }
        val allowed = setOf(
            "test",
            "git",
            "./gradlew",
            "gradlew",
            "printf",
            "cat",
            "grep",
            "sed",
            "awk",
            "ls",
            "find",
            "pwd",
            "wc",
            "head",
            "tail",
            "sort",
            "cmp",
            "diff",
            "true",
            "false",
            "mkdir",
            "touch",
            "cp"
        )

        if (first == "git") {
            val subcommand = tokens.getOrNull(1)?.lowercase().orEmpty()
            val allowedGit = setOf("status", "diff", "log", "rev-parse", "show", "ls-files")
            if (subcommand !in allowedGit) {
                return "smoke command refuses git subcommand: ${subcommand.ifBlank { "missing" }}"
            }
        } else if (first !in allowed && !first.startsWith("./")) {
            return "smoke command refuses unsupported command: $first"
        }

        return null
    }

    /**
     * @param actor who asked. Smoke commands are operator-supplied by default;
     *   an automated run passes its own identity.
     */
    fun run(
        command: String,
        actor: ActionActor = ActionActor.HumanOwner
    ): AgentSmokeExecutionResult {
        val trimmed = command.trim()
        val refusal = validate(trimmed)
        if (refusal != null) {
            return AgentSmokeExecutionResult(
                command = trimmed,
                passed = false,
                refusalReason = refusal,
                failure = AgentExecutionFailure.INVALID_COMMAND
            )
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        // Pre-authorisation: these tokens came from free text, so the gate
        // decides before the process is built. `validate` above still runs
        // first — bounded agency adds an authority, it does not replace the
        // syntactic refusals.
        val decision = agencyGate.evaluate(
            VerificationActionProposals.smoke(tokens, repoRoot, actor)
        )
        if (decision.disposition != AgencyDisposition.ALLOWED) {
            return AgentSmokeExecutionResult(
                command = trimmed,
                passed = false,
                refusalReason = decision.reason,
                failure = AgentExecutionFailure.POLICY_REFUSED
            )
        }
        val result = processRunner.run(
            command = tokens,
            directory = repoRoot,
            timeoutMillis = timeoutMillis,
            maxOutputBytes = maxOutputBytes,
            maxOutputLines = maxOutputLines,
            removeEnvironmentKeys = sensitiveEnvironmentKeys()
        )
        val stdout = redactionFilter.redact(result.stdout)
        val stderr = redactionFilter.redact(result.stderr)
        val exitCode = result.exitCode
        val passed = exitCode == 0 && !result.timedOut && result.launchError == null

        return AgentSmokeExecutionResult(
            command = trimmed,
            passed = passed,
            exitCode = exitCode ?: -1,
            durationMillis = result.durationMillis,
            stdout = stdout,
            stderr = stderr,
            refusalReason = when {
                passed -> null
                result.launchError != null -> result.launchError
                result.timedOut -> "smoke timed out after $timeoutMillis ms"
                else -> null
            },
            failure = when {
                result.launchError != null -> AgentExecutionFailure.LAUNCH_FAILED
                result.timedOut -> AgentExecutionFailure.TIMEOUT
                exitCode != 0 -> AgentExecutionFailure.NONZERO_EXIT
                else -> null
            },
            outputTruncated = result.outputTruncated
        )
    }

    private fun sensitiveEnvironmentKeys(): Set<String> = System.getenv().keys.filter { key ->
        val name = key.uppercase()
        name.contains("TOKEN") || name.contains("SECRET") || name.contains("PASSWORD") ||
            name.endsWith("_KEY") || name.contains("CREDENTIAL")
    }.toSet()

    private fun isUnboundedPathToken(token: String): Boolean =
        token.startsWith("/") || token == ".." || token.startsWith("../") || token.contains("/../")
}
