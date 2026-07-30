package atropos.core.security

import java.nio.file.Path

/** Reasons a vault read can be refused without exposing secret material. */
enum class VaultReadRefusalReason {
    MISSING,
    NOT_REGULAR_FILE,
    SYMBOLIC_LINK,
    OUTSIDE_VAULT_ROOT,
    NOT_ISOLATED,
    INVALID_CIPHERTEXT,
    UNSUPPORTED_FORMAT,
    TAMPERED,
    KEY_UNAVAILABLE,
    IO_FAILURE
}

class VaultEnrollmentRefused(val reason: VaultReadRefusalReason) : RuntimeException(null, null, false, false)

/** Typed, non-throwing boundary for vault reads. */
sealed interface VaultReadResult {
    val path: Path

    /** The caller may consume the value, but it must never be rendered or persisted. */
    class Available(
        override val path: Path,
        val value: String
    ) : VaultReadResult {
        override fun toString(): String = "VaultReadResult.Available(path=$path)"
    }

    /** A refusal carries only a stable reason and path, never exception text or bytes. */
    class Refused(
        override val path: Path,
        val reason: VaultReadRefusalReason
    ) : VaultReadResult {
        override fun toString(): String = "VaultReadResult.Refused(path=$path, reason=$reason)"
    }
}
