package atropos.core.factory

import atropos.core.intent.Sd5HumanIntentInvariants

/** Canonical vocabulary for recognizing general application-generation intent. */
class AppActionRegistry(
    private val actionWords: Set<String> = DEFAULT_ACTIONS
) {
    fun isAction(token: String): Boolean = normalize(token) in actionWords

    fun isAppRequest(tokens: List<String>): Boolean {
        if (!Sd5HumanIntentInvariants.validateAll()) return false
        val meaningful = tokens.count { it.isNotBlank() }
        // The noun is intentionally unconstrained: arbitrary app domains are
        // part of the factory contract, not a registry of product names.
        return meaningful >= 2 && tokens.any(::isAction)
    }

    private fun normalize(token: String): String = token.lowercase().trim(',', '.', ':', ';', '!', '?')

    private companion object {
        val DEFAULT_ACTIONS = setOf("build", "create", "make", "generate", "write", "implement")
    }
}
