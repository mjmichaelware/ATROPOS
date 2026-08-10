package atropos.core.factory

import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ShellActionProposals
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class AppVerificationHelper(
    private val repoRoot: Path,
    private val agencyGate: BoundedAgencyGate,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun runVerify(directory: Path): String {
        val actor = ActionActor.HierarchyNode("factory-worker", "factory-${directory.fileName}")
        val command = listOf("sh", "verify.sh")
        val targetPath = repoRoot.toAbsolutePath().normalize()
            .relativize(directory.resolve("verify.sh").toAbsolutePath().normalize())
            .toString()
        val proposal = ShellActionProposals.forCommand(command, directory, actor)
            .copy(targetPaths = listOf(targetPath))
        val authorization = agencyGate.evaluate(proposal)
        check(authorization.disposition == AgencyDisposition.ALLOWED) {
            "generated verification refused by policy: ${authorization.reason}"
        }
        val bounded = processRunner.run(
            command = command,
            directory = directory,
            timeoutMillis = 900_000L,
            maxOutputBytes = 64 * 1024,
            maxOutputLines = 4_000,
            removeEnvironmentKeys = setOf("KOTLIN_RUNNER"),
            evidenceDirectory = directory.resolve(".atropos/evidence/build")
        )
        val output = redactionFilter.redact(
            listOf(bounded.stdout, bounded.stderr)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trimEnd()
        )
        val proofTail = redactionFilter.redact(
            listOf(bounded.stdoutTail, bounded.stderrTail)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        )
        val marker = "APP_FACTORY_VERIFY_OK"
        val markerInEvidence = containsMarker(bounded.stdoutLogPath, marker) ||
            containsMarker(bounded.stderrLogPath, marker)
        check(
            bounded.launchError == null &&
                !bounded.timedOut &&
                bounded.exitCode == 0 &&
                (output.contains(marker) || proofTail.contains(marker) || markerInEvidence)
        ) {
            val detail = bounded.launchError?.let(redactionFilter::redact)
                ?: (output + "\n" + proofTail).replace(Regex("\\s+"), " ").trim().take(400)
            "generated app verification failed: $detail"
        }
        return buildString {
            if (output.isNotBlank()) appendLine(output)
            appendLine(marker)
            appendLine("verification_output_bytes=${bounded.totalOutputBytes}")
            appendLine("verification_output_lines=${bounded.totalOutputLines}")
            appendLine("verification_output_truncated=${bounded.outputTruncated}")
            appendLine("verification_output_sha256=${bounded.outputSha256 ?: "unavailable"}")
            bounded.stdoutLogPath?.let { appendLine("verification_stdout_log=${it.fileName}") }
            bounded.stderrLogPath?.let { appendLine("verification_stderr_log=${it.fileName}") }
        }.trimEnd()
    }

    private fun containsMarker(path: Path?, marker: String): Boolean {
        if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false
        val needle = marker.toByteArray(StandardCharsets.UTF_8)
        if (needle.isEmpty()) return true
        var matched = 0
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return false
                for (index in 0 until count) {
                    if (buffer[index] == needle[matched]) {
                        matched++
                        if (matched == needle.size) return true
                    } else {
                        matched = if (buffer[index] == needle[0]) 1 else 0
                    }
                }
            }
        }
    }
}
