package atropos.core.agent

import java.security.MessageDigest

/** Stores queue/supervisor lease identity without persisting the bearer token. */
object LeaseTokenDigest {
    fun of(token: String): String {
        require(token.isNotBlank()) { "lease token must not be blank" }
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun matches(stored: String, presented: String?): Boolean {
        if (presented.isNullOrBlank()) return false
        return stored == presented || stored == of(presented)
    }
}
