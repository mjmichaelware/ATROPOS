package atropos.core.security

import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Supplies the key used by an encrypted secret vault.
 *
 * Implementations must obtain key material from an external protected source.
 * A vault path, host identity, or ciphertext is not a valid key source because
 * each is observable alongside the encrypted data.
 */
interface SecretVaultKeyProvider {
    fun load(): SecretVaultKeyResult
}

/** Typed key lookup outcome; failures carry no secret values or exception text. */
sealed interface SecretVaultKeyResult {
    class Available(val key: SecretKey) : SecretVaultKeyResult {
        override fun toString(): String = "SecretVaultKeyResult.Available"
    }

    data class Refused(val reason: SecretVaultKeyFailure) : SecretVaultKeyResult
}

enum class SecretVaultKeyFailure {
    MISSING,
    MALFORMED_ENCODING,
    INVALID_LENGTH,
    UNSUPPORTED_ALGORITHM
}

/**
 * JVM provider for an operator-supplied Base64 encoded AES-256 key.
 *
 * The environment is injected for deterministic callers and tests. The default
 * source is the process environment; no key is persisted by this class.
 */
class EnvironmentSecretVaultKeyProvider(
    private val environment: Map<String, String> = System.getenv(),
    private val variableName: String = DEFAULT_VARIABLE_NAME
) : SecretVaultKeyProvider {

    override fun load(): SecretVaultKeyResult {
        val encoded = environment[variableName]?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return SecretVaultKeyResult.Refused(SecretVaultKeyFailure.MISSING)

        val decoded = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { return SecretVaultKeyResult.Refused(SecretVaultKeyFailure.MALFORMED_ENCODING) }

        if (decoded.size != AES_256_KEY_BYTES) {
            decoded.fill(0)
            return SecretVaultKeyResult.Refused(SecretVaultKeyFailure.INVALID_LENGTH)
        }

        return runCatching {
            val key = SecretKeySpec(decoded, AES_ALGORITHM)
            decoded.fill(0)
            SecretVaultKeyResult.Available(key)
        }.getOrElse {
            decoded.fill(0)
            SecretVaultKeyResult.Refused(SecretVaultKeyFailure.UNSUPPORTED_ALGORITHM)
        }
    }

    companion object {
        const val DEFAULT_VARIABLE_NAME: String = "ATROPOS_VAULT_KEY"
        const val AES_ALGORITHM: String = "AES"
        const val AES_256_KEY_BYTES: Int = 32
    }
}
