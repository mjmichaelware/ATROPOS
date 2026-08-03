package atropos.core.agent

import atropos.core.security.RedactionFilter

/**
 * Turns a thrown message into one bounded, redacted line.
 *
 * Existed three times — in [AgentService], [AgentRepairService], and the patch
 * cascade — with the same 240-character bound and the same fallback string, so
 * it was already a shared rule kept in sync by hand.
 *
 * Both halves are load-bearing. **Redaction**, because a provider transport
 * failure is one of the likeliest places for a credential to surface: a failed
 * HTTPS call reports the URL it failed on, and that URL can carry a key.
 * **The bound**, because a stack-trace-length message stored per failure turns
 * the durable record into the biggest thing in the repository.
 *
 * A blank or absent message becomes a fixed line rather than an empty one, so a
 * failure is never recorded as though nothing went wrong.
 */
internal class AgentFailureSummary(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    /**
     * @param fallback what to say when there is no message. Callers name their
     *   own because "provider cascade failed" is wrong for a run that never
     *   reached a provider.
     */
    fun compact(message: String?, fallback: String = CASCADE_FAILED): String =
        message?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?.let { redactionFilter.compact(it, MAXIMUM_CHARACTERS) }
            ?: fallback

    internal companion object {
        const val MAXIMUM_CHARACTERS = 240
        const val CASCADE_FAILED = "provider cascade failed"
        const val RUN_FAILED = "agent run failed"
    }
}
