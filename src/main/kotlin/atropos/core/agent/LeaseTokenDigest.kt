package atropos.core.agent

import java.security.MessageDigest

/** Stores queue/supervisor lease identity without persisting the bearer token. */
object LeaseTokenDigest {
    private val sha256Hex = Regex("[0-9a-fA-F]{64}")

    fun of(token: String): String {
        require(token.isNotBlank()) { "lease token must not be blank" }
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun persistedIdentity(token: String): String =
        if (isPersistedIdentity(token)) token.lowercase() else of(token)

    fun isPersistedIdentity(value: String?): Boolean =
        value?.matches(sha256Hex) == true

    fun fingerprint(token: String?): String =
        token?.takeIf { it.isNotBlank() }?.let(::persistedIdentity)?.take(10) ?: "none"

    fun matches(stored: String, presented: String?): Boolean {
        if (presented.isNullOrBlank()) return false
        if (!isPersistedIdentity(stored)) return false
        return MessageDigest.isEqual(
            stored.lowercase().toByteArray(Charsets.US_ASCII),
            of(presented).toByteArray(Charsets.US_ASCII)
        )
    }
}
