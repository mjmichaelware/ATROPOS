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
     * Adds context about the rejection and requests only a valid unified diff.
     */
    fun buildRetryPrompt(): String = buildString {
        appendLine(REPAIR_PROMPT)
        appendLine()
        appendLine(
            "Your previous response was rejected because no unified diff was found. " +
                "Return ONLY a valid unified diff for the same task."
        )
        appendLine("Include file headers, at least one @@ hunk header, and the added or removed line(s).")
    }.trimEnd()

    companion object {
        const val REPAIR_PROMPT = "Repair the verification failure by returning only a unified diff."
    }
}
