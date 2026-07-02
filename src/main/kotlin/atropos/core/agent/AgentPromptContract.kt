package atropos.core.agent

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

    fun build(context: String): String =
        if (context.isBlank()) {
            SYSTEM_TEXT
        } else {
            SYSTEM_TEXT + "\n\nRepository context:\n" + context.trim()
        }

    fun buildPatch(context: String): String =
        if (context.isBlank()) {
            PATCH_SYSTEM_TEXT
        } else {
            PATCH_SYSTEM_TEXT + "\n\nRepository context:\n" + context.trim()
        }

    fun buildRepair(
        patchId: String,
        changedPaths: List<String>,
        failedCommand: String,
        exitCode: Int?,
        durationMillis: Long,
        stdout: String,
        stderr: String,
        context: String
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

        return if (context.isBlank()) {
            REPAIR_SYSTEM_TEXT + "\n\nVerification failure:\n" + verificationBlock.trimEnd()
        } else {
            REPAIR_SYSTEM_TEXT + "\n\nVerification failure:\n" + verificationBlock.trimEnd() +
                "\n\nRepository context:\n" + context.trim()
        }
    }
}
