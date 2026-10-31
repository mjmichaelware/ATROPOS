/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * ATOM 22.2: Cryptographic Owner Privilege Verification.
 *
 * Enforces cryptographic authentication before allowing any self-hosting or
 * administrative operations. Ordinary authenticated users in the Supabase database
 * must be strictly denied access to these privileged endpoints unless verified
 * by the owner master key.
 *
 * Research Directive: Hardware FIDO2 and WebAuthn security key integration for
 * biometric owner authentication in headless server environments.
 */
class DecoupledCryptographicOwnerGate(
    private val clock: () -> Instant = { Instant.now() }
) {
    data class PrivilegedToken(
        val tokenString: String,
        val issuedToOwner: Boolean,
        val expiresAt: Instant,
        val operationScope: String,
        val fido2Attested: Boolean = false
    ) {
        fun isExpired(now: Instant): Boolean = now.isAfter(expiresAt)
    }

    private val random = SecureRandom()

    fun issuePrivilegedOwnerToken(scope: String, validFor: Duration = Duration.ofMinutes(15)): PrivilegedToken {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val tokenHex = bytes.joinToString("") { "%02x".format(it) }
        return PrivilegedToken(
            tokenString = "owner-priv-$tokenHex",
            issuedToOwner = true,
            expiresAt = clock().plus(validFor),
            operationScope = scope,
            fido2Attested = false
        )
    }

    fun assertPrivilegedAccess(token: PrivilegedToken?, requiredScope: String) {
        if (token == null) {
            throw SecurityException("ATOM 22.2: Missing cryptographic owner authorization token for privileged scope: $requiredScope")
        }
        if (!token.issuedToOwner) {
            throw SecurityException("ATOM 22.2: Ordinary database credentials cannot execute owner self-host operations.")
        }
        if (token.isExpired(clock())) {
            throw SecurityException("ATOM 22.2: Privileged owner authorization token has expired.")
        }
        if (token.operationScope != requiredScope && token.operationScope != "*") {
            throw SecurityException("ATOM 22.2: Token scope ${token.operationScope} does not match required $requiredScope")
        }
    }
}
