/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

/** Bounded cross-turn boundary for the canonical secret-egress scanner. */
class LeakageAccumulator(
    private val maxConversations: Int = DEFAULT_MAX_CONVERSATIONS,
    private val maxTailChars: Int = DEFAULT_MAX_TAIL_CHARS
) {
    private data class Cursor(val tail: String, val turns: Int)
    private val cursors = linkedMapOf<String, Cursor>()

    init {
        require(maxConversations > 0) { "maxConversations must be positive" }
        require(maxTailChars > 0) { "maxTailChars must be positive" }
    }

    fun scan(conversationId: String, fragment: String, sink: SecretSinkKind = SecretSinkKind.MODEL_OUTPUT): List<EgressViolation> {
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        val previous = cursors[conversationId]?.tail.orEmpty()
        val combined = previous + fragment
        val violations = SecretEgressGate.scan(combined, sink)
        cursors[conversationId] = Cursor(combined.takeLast(maxTailChars), (cursors[conversationId]?.turns ?: 0) + 1)
        trim()
        return violations
    }

    fun forget(conversationId: String) { cursors.remove(conversationId) }
    fun clear() { cursors.clear() }
    fun turnCount(conversationId: String): Int = cursors[conversationId]?.turns ?: 0

    private fun trim() {
        while (cursors.size > maxConversations) cursors.remove(cursors.entries.first().key)
    }

    private companion object {
        const val DEFAULT_MAX_CONVERSATIONS = 64
        const val DEFAULT_MAX_TAIL_CHARS = 4096
    }
}
