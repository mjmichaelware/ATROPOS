package atropos.core.agent

import atropos.core.security.RedactionFilter

/**
 * Cleans up what an ask returns, and writes the answer when no provider could.
 *
 * ## Why an answer that echoes its own context is replaced
 *
 * A model handed a repository context pack sometimes answers by reciting it —
 * git status, the file tree, the selected sources — instead of using it. That
 * output is worse than useless: it is long, it puts collected source back on
 * the operator's screen, and it reads as though the model examined the repo
 * when it merely repeated the prompt.
 *
 * [normalize] detects the echo by looking for the context pack's own section
 * headers, which are strings ATROPOS emits and a genuine answer has no reason
 * to contain, and substitutes a short honest sentence. The detection is
 * deliberately narrow: a false positive would discard a real answer, so only
 * the exact headers count, not anything that merely mentions the repository.
 *
 * [fallbackAnswer] is what the operator sees when every provider failed. It
 * states plainly what ATROPOS can and cannot see rather than apologising,
 * because the next thing the operator needs to decide is whether to retry or
 * narrow the task.
 */
internal class AgentAskAnswerNormalizer(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun normalize(answer: String): String {
        val trimmed = answer.trim()
        return if (echoesContext(trimmed)) CONTEXT_ECHO_REPLACEMENT else trimmed
    }

    /** Normalised and redacted, the form that reaches the operator. */
    fun present(answer: String): String = redactionFilter.redact(normalize(answer.trim()))

    private fun echoesContext(text: String): Boolean =
        CONTEXT_MARKERS.any { marker -> text.contains(marker) }

    fun fallbackAnswer(task: String, snapshot: AgentContextSnapshot): String = buildString {
        appendLine(
            "Yes. ATROPOS supplied repo context, so I can see the workspace " +
                "through that bounded snapshot."
        )
        appendLine(
            "I do not have direct filesystem access, but the collected context includes " +
                "git status, a shallow tree, and selected provider/routing/agent source files."
        )
        appendLine(
            "I can use this context to reason about the code, draft a patch, " +
                "or inspect a specific file next."
        )
        appendLine("Task: ${redactionFilter.redact(task.trim().ifBlank { BLANK_TASK })}")
        appendLine("Context bytes: ${snapshot.byteCount}")
    }.trimEnd()

    private companion object {
        const val BLANK_TASK = "(blank task)"

        /**
         * Section headers ATROPOS writes into a context pack. Their presence in
         * a response means the pack was recited rather than used.
         */
        val CONTEXT_MARKERS = listOf(
            "\n# Repo Root",
            "\n# Git Status",
            "\n# Selected Sources",
            "Repository context:"
        )

        const val CONTEXT_ECHO_REPLACEMENT =
            "Yes. ATROPOS supplied bounded repo context, so I can reason over the " +
                "workspace snapshot without direct filesystem access."
    }
}
