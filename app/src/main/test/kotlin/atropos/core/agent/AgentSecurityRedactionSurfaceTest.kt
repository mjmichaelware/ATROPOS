package atropos.core.agent

import atropos.core.provider.ProviderRedactor
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentSecurityRedactionSurfaceTest {
    @Test
    fun durable_agent_surfaces_redact_secrets() {
        val repoRoot = Files.createTempDirectory("atropos-agent-redaction-")
        val secret = "sk-ABCDEFGHIJKLMNOPQRSTUVWX"
        val path = "/tmp/client_secret-prod.json"

        val jobStore = AgentJobStore(repoRoot)
        val job = jobStore.createJob("fix token=$secret using $path", "groq")
        val updatedJob = jobStore.update(
            job.copy(
                result = "Authorization: Bearer ABCDEFGHIJKLMNOPQRSTUVWX",
                failureReason = "secret=$secret",
                smokeCommand = "cat $path",
                smokeStdout = "token=$secret",
                finalReport = "task uses $path and api_key=$secret",
                sourceEvidence = "doc secret=$secret path=$path",
                impactedSymbols = listOf("src/secret.kt:token=$secret", "src/secret.kt:path=$path")
            )
        )
        val reopenedJob = jobStore.resolve("latest") ?: error("missing job")
        val jobSummary = reopenedJob.renderSummaryLine() + "\n" + updatedJob.render()

        assertFalse(reopenedJob.task.contains(secret))
        assertFalse(reopenedJob.task.contains("client_secret-prod.json"))
        assertTrue(reopenedJob.task.contains("<redacted"))
        assertFalse(jobSummary.contains(secret))
        assertFalse(jobSummary.contains("client_secret-prod.json"))
        assertFalse(reopenedJob.sourceEvidence.orEmpty().contains(secret))
        assertFalse(reopenedJob.impactedSymbols.joinToString("\n").contains(secret))
        assertFalse(reopenedJob.impactedSymbols.joinToString("\n").contains("client_secret-prod.json"))

        val queueStore = AgentQueueStore(repoRoot)
        val queue = queueStore.createEntry("queue secret=$secret path=$path", "cat $path", failureReason = "token=$secret")
        val reopenedQueue = queueStore.resolve(queue.id) ?: error("missing queue")
        val queueRendered = reopenedQueue.renderRaw()

        assertFalse(reopenedQueue.task.contains(secret))
        assertFalse(reopenedQueue.task.contains("client_secret-prod.json"))
        assertFalse(queueRendered.contains(secret))
        assertFalse(queueRendered.contains("client_secret-prod.json"))

        val exportStore = AgentContextExportStore(repoRoot)
        val exportPath = exportStore.write(
            updatedJob.copy(
                task = "task secret=$secret path=$path",
                nextSuggestedCommand = "echo $secret",
                commitProposal = "commit with $path",
                finalReport = "report $secret",
                sourceEvidence = "source secret=$secret path=$path",
                impactedSymbols = listOf("impact $secret", "impact $path")
            ),
            listOf("secrets/$path")
        )
        val exportText = Files.readString(exportPath, StandardCharsets.UTF_8)
        assertFalse(exportText.contains(secret))
        assertFalse(exportText.contains("client_secret-prod.json"))
        assertTrue(exportText.contains("<redacted"))

        val daemonStore = AgentDaemonStore(repoRoot)
        val daemon = daemonStore.writeState(
            daemonStore.initialRecord(
                state = AgentDaemonState.RUNNING,
                pollSeconds = 30,
                message = "token=$secret path=$path"
            )
        )
        assertFalse(daemon.lastMessage.orEmpty().contains(secret))
        assertFalse(daemon.lastMessage.orEmpty().contains("client_secret-prod.json"))
        assertTrue(daemon.lastMessage.orEmpty().contains("<redacted"))

        val providerRedacted = ProviderRedactor.redact("Authorization: Bearer ABCDEFGHIJKLMNOPQRSTUVWX api_key=$secret")
        assertFalse(providerRedacted.contains(secret))
        assertFalse(providerRedacted.contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertTrue(providerRedacted.contains("<redacted"))

        val verificationStore = AgentVerificationStore(repoRoot)
        val verification = verificationStore.createRecord(
            patchId = "patch-1",
            command = "JAVA_HOME=/x OPENAI_API_KEY=$secret ./gradlew test",
            exitCode = 1,
            durationMillis = 42,
            changedPaths = listOf("secrets/$path"),
            stdout = "token=$secret",
            stderr = "Authorization: Bearer ABCDEFGHIJKLMNOPQRSTUVWX",
            passed = false,
            failureReason = "api_key=$secret"
        )
        assertFalse(verification.command.contains(secret))
        assertFalse(verification.changedPaths.joinToString(",").contains("client_secret-prod.json"))
        assertFalse(verification.stdout.contains(secret))
        assertFalse(verification.stderr.contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertFalse(verification.failureReason.orEmpty().contains(secret))

        val renderedVerification = AgentVerificationRunResult(
            patchId = "patch-1",
            verificationId = verification.id,
            patchFile = repoRoot.resolve(path.removePrefix("/")),
            command = "OPENAI_API_KEY=$secret ./gradlew test",
            changedPaths = listOf(path),
            stdout = "token=$secret",
            stderr = "Bearer ABCDEFGHIJKLMNOPQRSTUVWX",
            refusalReason = "secret=$secret"
        ).render()
        assertFalse(renderedVerification.contains(secret))
        assertFalse(renderedVerification.contains("client_secret-prod.json"))
        assertFalse(renderedVerification.contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertTrue(renderedVerification.contains("<redacted"))

        val patchStore = AgentPatchStore(repoRoot)
        val patch = patchStore.createRecord("groq", "patch secret=$secret path=$path", 0, "--- a/x\n+++ b/x\n@@ -1 +1 @@\n-a\n+b\n")
        patchStore.writeMeta(patch, AgentPatchCheckResult(passed = true, exitCode = 0, output = "api_key=$secret"))
        val meta = Files.readString(patch.metaFile, StandardCharsets.UTF_8)
        assertFalse(meta.contains(secret))
        assertFalse(meta.contains("client_secret-prod.json"))
        assertTrue(meta.contains("<redacted"))

        runCatching {
            patchStore.createRecord("groq", "task", 0, "--- a/.env\n+++ b/.env\n@@ -0,0 +1 @@\n+OPENAI_API_KEY=$secret\n")
        }.onSuccess {
            error("expected secret-bearing diff refusal")
        }.onFailure { failure ->
            assertTrue(failure.message.orEmpty().contains("refused"))
        }
    }
}
