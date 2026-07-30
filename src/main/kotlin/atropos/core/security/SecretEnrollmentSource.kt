/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

/**
 * Where a device's own credentials come from, as a port rather than a hard-coded
 * lookup.
 *
 * This interface is the reason the egress guarantee travels. Tier 1 of the filter
 * only works on credentials that were enrolled, so "who enrolls?" decides whether
 * the protection is a property of the software or a property of one machine. If
 * enrollment read a fixed path or a fixed variable list, then a user who curls the
 * APK onto a phone in another country would run the same binary with an empty
 * registry and silently lose tier 1 — the guard would look present and protect
 * nothing, which is worse than not shipping it.
 *
 * So enrollment is inverted: the core states what it needs (labelled credential
 * values, discovered at runtime) and each platform supplies them from wherever
 * that platform actually keeps them — process environment on a JVM or Termux
 * host, the vault on disk, and on Android the OS keystore or an app-private store
 * that has no environment variables at all.
 *
 * Three rules hold for every implementation, and they are what make the design
 * device-independent:
 * 1. **No baked-in values.** An implementation discovers, it never carries a
 *    credential compiled into the artifact. A secret in the APK is a secret
 *    published to everyone who downloads it.
 * 2. **No absolute paths owned by one machine.** Resolve from the platform's own
 *    notion of home or app storage, so nothing depends on one developer's layout.
 * 3. **Absence is reported, never faked.** Returning an empty map is legitimate —
 *    a fresh install genuinely has no credentials — and must be distinguishable
 *    from "discovery failed", because the second case means tier 1 is silently off.
 */
interface SecretEnrollmentSource {
    /** A short name for evidence lines, e.g. `environment` or `vault`. */
    val sourceName: String

    /**
     * Labelled credential values found on this device.
     *
     * Keys are non-sensitive labels (`GROQ_API_KEY`); values are the secrets. The
     * caller enrolls and discards them; nothing here is expected to cache.
     */
    fun discover(): Map<String, String>
}

/**
 * Discovers credentials from the process environment.
 *
 * Portable by construction: it names no path and no specific provider, and matches
 * on the shape of the *variable name* rather than a fixed list, so a provider added
 * next year is enrolled without a code change. Any host that gives the process an
 * environment — Termux, a desktop JVM, CI, a container — is covered identically.
 *
 * Android is the deliberate exception: an app there has no meaningful environment,
 * so this source returns empty and an Android-side implementation of
 * [SecretEnrollmentSource] supplies the keystore instead. That is the port earning
 * its keep rather than a gap.
 */
class EnvironmentSecretSource(
    private val environment: Map<String, String> = System.getenv()
) : SecretEnrollmentSource {

    override val sourceName: String = "environment"

    override fun discover(): Map<String, String> =
        environment.filterKeys(::looksLikeCredentialName)
            .filterValues { it.isNotBlank() }

    private fun looksLikeCredentialName(name: String): Boolean {
        val upper = name.uppercase()
        // Name-shape matching, not a provider allowlist: the point is that an
        // unfamiliar provider's key is still enrolled.
        if (DENY_SUBSTRINGS.any { it in upper }) return false
        return SUBSTRINGS.any { it in upper } || upper.endsWith("_KEY")
    }

    private companion object {
        val SUBSTRINGS = listOf("API_KEY", "APIKEY", "TOKEN", "SECRET", "PASSWORD", "CREDENTIAL")

        /**
         * Names that carry configuration rather than a credential. Enrolling one of
         * these would flood output with markers for a value that is not secret —
         * `TOKEN_LIMIT=4096` would redact every occurrence of "4096".
         */
        val DENY_SUBSTRINGS = listOf(
            "TOKEN_LIMIT", "TOKENS_", "_TOKENS", "MAX_TOKEN", "TOKENIZER",
            "SECRET_NAME", "KEY_PATH", "KEYSTORE_PATH", "PUBLIC_KEY"
        )
    }
}

/**
 * Composes several sources and enrolls everything they find.
 *
 * Kept separate from [KnownSecretRegistry] so the registry stays a pure membership
 * structure with no knowledge of discovery, and so a platform can be added by
 * passing one more source rather than by editing the registry.
 */
class SecretEnrollment(
    private val sources: List<SecretEnrollmentSource>
) {
    data class Result(
        val enrolledLabels: Set<String>,
        val variantCount: Int,
        val bySource: Map<String, Int>
    ) {
        /** Safe to log: labels and counts only, never a value. */
        fun evidenceLine(): String =
            "secret_enrollment sources=${bySource.entries.joinToString("|") { "${it.key}=${it.value}" }} " +
                "labels=${enrolledLabels.size} variants=$variantCount"
    }

    fun enrollInto(registry: KnownSecretRegistry): Result {
        val bySource = mutableMapOf<String, Int>()
        var variants = 0
        sources.forEach { source ->
            val found = runCatching { source.discover() }.getOrDefault(emptyMap())
            var perSource = 0
            found.forEach { (label, value) ->
                val added = registry.enroll(label, value)
                variants += added
                if (added > 0) perSource += 1
            }
            bySource[source.sourceName] = perSource
        }
        return Result(registry.enrolledLabels.toSet(), variants, bySource)
    }
}
