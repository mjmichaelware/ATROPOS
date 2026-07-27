package atropos.core.agent

import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ContextEnvelopeFactory
import java.nio.file.Path

object AgentPromptContract {
    const val SYSTEM_TEXT =
        "You are an ATROPOS reasoning provider. ATROPOS has read the local repo and supplied bounded context. " +
        "You cannot directly access files. Use the provided context. Do not ask for API keys. Return direct answers, plans, or diffs only."

    const val PATCH_SYSTEM_TEXT =
        "You are an ATROPOS patch provider. Return ONLY a unified diff. No markdown fences. No explanation. " +
        "No secrets. Stay inside allowed repo paths. Do not edit .env, secrets, credentials, jars, build outputs, or git metadata. " +
        "Prefer narrow diffs. File snapshots in the context are marked with FILE and END FILE and are not diff headers. " +
        "If the task names a file, use the provided file content and include the full file headers and hunk lines."

    const val REPAIR_SYSTEM_TEXT =
        "You are an ATROPOS repair provider. ATROPOS has read the local repo and supplied bounded context. " +
        "You cannot directly access files. Use the provided context. Return ONLY a unified diff. No markdown fences. " +
        "No explanation. No prose before or after. No secrets. Stay inside allowed repo paths. Do not edit .env, secrets, " +
        "credentials, jars, build outputs, or git metadata. Prefer narrow diffs that fix the failed verification."

    fun build(
        context: String,
        providerId: String = "groq",
        modelId: String = "",
        task: String = "",
        repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
        explicitMythologyRequest: Boolean = false
    ): String {
        val envelope = ContextEnvelopeFactory.createSimple(
            providerId = providerId,
            modelId = modelId,
            task = task.ifBlank { "general reasoning" },
            repoRoot = repoRoot
        )
        val corePrompt = if (context.isBlank()) {
            SYSTEM_TEXT
        } else {
            SYSTEM_TEXT + "\n\nRepository context:\n" + context.trim()
        }
        return ContextAttestationService.injectContext(envelope, corePrompt, explicitMythologyRequest)
    }

    fun buildPatch(
        context: String,
        providerId: String = "groq",
        modelId: String = "",
        task: String = "",
        repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    ): String {
        val envelope = ContextEnvelopeFactory.createSimple(
            providerId = providerId,
            modelId = modelId,
            task = task.ifBlank { "patch generation" },
            repoRoot = repoRoot
        )
        val corePrompt = if (context.isBlank()) {
            PATCH_SYSTEM_TEXT
        } else {
            PATCH_SYSTEM_TEXT + "\n\nRepository context:\n" + context.trim()
        }
        return ContextAttestationService.injectContext(envelope, corePrompt)
    }

    @JvmOverloads
    fun buildRepair(
        patchId: String,
        changedPaths: List<String>,
        failedCommand: String,
        exitCode: Int?,
        durationMillis: Long,
        stdout: String,
        stderr: String,
        context: String,
        providerId: String = "groq",
        modelId: String = "",
        repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    ): String {
        val verificationBlock = buildString {
            appendLine("Patch id: $patchId")
            appendLine("Changed paths: ${changedPaths.joinToString(", ").ifBlank { "none" }}")
            appendLine("Failed command: $failedCommand")
            appendLine("Exit code: ${exitCode?.toString() ?: "none"}")
            appendLine("Duration ms: $durationMillis")
            appendLine("Verification stdout:")
            appendLine(stdout.ifBlank { "(empty)" })
            appendLine("Verification stderr:")
            appendLine(stderr.ifBlank { "(empty)" })
        }

        val corePrompt = if (context.isBlank()) {
            REPAIR_SYSTEM_TEXT + "\n\nVerification failure:\n" + verificationBlock.trimEnd()
        } else {
            REPAIR_SYSTEM_TEXT + "\n\nVerification failure:\n" + verificationBlock.trimEnd() +
                "\n\nRepository context:\n" + context.trim()
        }

        val envelope = ContextEnvelopeFactory.createSimple(
            providerId = providerId,
            modelId = modelId,
            task = "repair patch $patchId",
            repoRoot = repoRoot
        )
        return ContextAttestationService.injectContext(envelope, corePrompt)
    }
}
