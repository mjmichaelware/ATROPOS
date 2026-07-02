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
}
