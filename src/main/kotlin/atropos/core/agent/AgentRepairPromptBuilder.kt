package atropos.core.agent

/**
 * Builds prompts for repair attempts.
 *
 * Constructs the initial repair prompt and retry prompts based on
 * previous response rejections.
 */
internal class AgentRepairPromptBuilder {
    /**
     * Builds the retry prompt when initial repair is rejected.
     *
     * Adds context about the rejection and requests the canonical edit formats.
     */
    fun buildRetryPrompt(): String = buildString {
        appendLine(REPAIR_PROMPT)
        appendLine()
        appendLine(
            "Your previous response was rejected. Return a strict edit envelope " +
                "(preferred) or a valid unified diff for the same task. Exact search/replace " +
                "must match once; do not use approximate context."
        )
        appendLine("Include file headers, at least one @@ hunk header, and the added or removed line(s).")
    }.trimEnd()

    companion object {
        const val REPAIR_PROMPT = "Repair the verification failure with a strict edit envelope or unified diff."
    }
}
