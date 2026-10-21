/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.security.RedactionFilter
import atropos.core.security.TokenIsolationVault
import atropos.core.security.VaultCipher
import atropos.core.security.VaultPathResolver
import atropos.core.security.VaultReadResult
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.output.OutputMode
import atropos.core.output.OutputModeDetector
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEParameterSpec

/**
 * Phase 20 Owner Authorization
 *
 * Enforces explicit owner authorization before any protected source mutation.
 *
 * ## Trust Boundary
 *
 * - The owner password is never stored in plaintext, configuration committed to Git,
 *   logs, prompts, or memory beyond the verification scope.
 * - A salted PBKDF2 hash is stored in the TokenIsolationVault under the secret
 *   name `phase20-owner-auth`.
 * - The vault uses AES-GCM encryption at rest; the PBKDF2 hash adds a second
 *   layer of work-factor protection if the vault key is compromised.
 * - Authorization is granted per-operation and per-scope; a token authorizes
 *   exactly one reviewed Phase 20 operation and expires after use.
 *
 * ## Limitations
 *
 * - A local password is not a defense against a machine administrator or malicious
 *   process that already has unrestricted access to the machine. The vault key
 *   (ATROPOS_VAULT_KEY or device-derived) and the password hash are both stored
 *   on the same filesystem. An attacker with root access can extract both.
 * - This mechanism raises the bar for remote/code-injection attacks and prevents
 *   accidental or automated self-modification. It does not replace OS-level
 *   access controls.
 * - In non-interactive environments (CI, headless), authorization fails closed
 *   unless a valid pre-authorization token is supplied through the approved
 *   environment variable `ATROPOS_PHASE20_AUTH_TOKEN`.
 *
 * ## Usage
 *
 * 1. Owner runs `atropos phase20 auth setup` once to set their password.
 * 2. Before any Phase 20 operation that mutates protected source, the system
 *    calls [Phase20Authorization.requireAuthorization] with the operation scope.
 * 3. In interactive mode, the owner is prompted for their password via a
 *    non-echoing console read.
 * 4. In non-interactive mode, the operation fails unless `ATROPOS_PHASE20_AUTH_TOKEN`
 *    contains a valid, unexpired, scope-bound token.
 * 5. A successful verification returns a single-use [AuthorizationToken] that
 *    must be passed to the Phase 20 operation.
 *
 * The authorization token carries the operation scope and a timestamp. It is
 * validated by the Phase 20 operation before any mutation occurs.
 */
class Phase20Authorization(
    private val vault: TokenIsolationVault = TokenIsolationVault(),
    private val vaultPathResolver: VaultPathResolver = VaultPathResolver(AtroposConfig.configRoot().resolve("secrets")),
    private val clock: () -> java.time.Instant = { java.time.Instant.now() },
    private val console: java.io.Console? = System.console(),
    private val outputMode: OutputMode = OutputModeDetector.detect(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val PBKDF2_ITERATIONS = 100_000
    private val PBKDF2_KEY_LENGTH = 256
    private val SALT_LENGTH = 16
    private val TOKEN_LENGTH = 32
    private val TOKEN_TTL_SECONDS = 300 // 5 minutes

    private val secretName = "phase20-owner-auth"
    private val tokenSecretName = "phase20-auth-token"

    /**
     * Checks if owner authorization is configured.
     */
    fun isConfigured(): Boolean {
        val result = vault.readSecretResult(secretName)
        return result is VaultReadResult.Available
    }

    /**
     * Sets up owner authorization with a new password.
     *
     * Must be run interactively. Stores a salted PBKDF2 hash in the vault.
     */
    fun setup(password: String? = null): SetupResult {
        val pwd = password ?: readPasswordInteractive("Enter Phase 20 owner password: ") ?: return SetupResult.Failed("no password provided")
        val confirm = readPasswordInteractive("Confirm Phase 20 owner password: ") ?: return SetupResult.Failed("no confirmation provided")
        if (pwd != confirm) return SetupResult.Failed("passwords do not match")
        if (pwd.length < 12) return SetupResult.Failed("password must be at least 12 characters")

        val salt = generateSalt()
        val hash = pbkdf2(pwd, salt)
        val stored = "$PBKDF2_ITERATIONS:$PBKDF2_KEY_LENGTH:${bytesToHex(salt)}:${bytesToHex(hash)}"

        try {
            vault.writeSecret(secretName, stored)
            return SetupResult.Success
        } catch (e: Exception) {
            return SetupResult.Failed("failed to store authorization: ${redactionFilter.redact(e.message ?: "unknown error")}")
        }
    }

    /**
     * Changes the owner password.
     */
    fun changePassword(currentPassword: String? = null, newPassword: String? = null): SetupResult {
        if (!verifyPassword(currentPassword ?: readPasswordInteractive("Current password: ") ?: return SetupResult.Failed("no current password"))) {
            return SetupResult.Failed("current password incorrect")
        }
        return setup(newPassword)
    }

    /**
     * Removes owner authorization (disables Phase 20 self-modification).
     */
    fun disable(): Boolean = runCatching { vault.deleteSecret(secretName) }.getOrDefault(false)

    /**
     * Verifies the provided password against the stored hash.
     */
    fun verifyPassword(password: String): Boolean {
        val result = vault.readSecretResult(secretName)
        if (result !is VaultReadResult.Available) return false
        val stored = result.value
        return try {
            val parts = stored.split(':')
            if (parts.size != 4) return false
            val iterations = parts[0].toIntOrNull() ?: return false
            val keyLength = parts[1].toIntOrNull() ?: return false
            val salt = hexToBytes(parts[2])
            val expectedHash = hexToBytes(parts[3])
            val computedHash = pbkdf2(password, salt, iterations, keyLength)
            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Requests authorization for a specific Phase 20 operation scope.
     *
     * In interactive mode, prompts for password and returns a single-use token.
     * In non-interactive mode, validates `ATROPOS_PHASE20_AUTH_TOKEN` environment variable.
     *
     * @param scope Human-readable description of the operation and its scope.
     * @return AuthorizationToken if authorized, null if refused or unavailable.
     */
    fun requireAuthorization(scope: String): AuthorizationToken? {
        // Check for pre-authorization token in non-interactive environments
        val envToken = System.getenv("ATROPOS_PHASE20_AUTH_TOKEN")
        if (envToken != null) {
            return validateToken(envToken, scope)
        }

        // Interactive mode is optional. A missing console or headless output
        // mode must never abort construction or block a piped invocation.
        // Phase 20 authorization remains fail-closed without a valid token.
        if (console == null || outputMode == OutputMode.HEADLESS || outputMode == OutputMode.TERM_DUMB) {
            return null
        }

        // Prompt for password
        val password = readPasswordInteractive("Phase 20 authorization required for: $scope\nEnter owner password: ")
            ?: return null

        if (!verifyPassword(password)) {
            // Increment failed attempt counter
            recordFailedAttempt()
            return null
        }

        // Check for excessive failed attempts
        if (getFailedAttempts() > 5) {
            return null
        }

        // Clear failed attempts on success
        clearFailedAttempts()

        // Generate and store single-use token
        return generateToken(scope)
    }

    /**
     * Validates an authorization token for the given scope.
     */
    fun validateToken(token: String, scope: String): AuthorizationToken? {
        val result = vault.readSecretResult(tokenSecretName)
        if (result !is VaultReadResult.Available) return null
        val stored = result.value
        return try {
            val parts = stored.split(':')
            if (parts.size != 4) return null
            val tokenHash = parts[0]
            val tokenScope = parts[1]
            val timestamp = parts[2].toLongOrNull() ?: return null
            val nonce = parts[3]

            // Verify token hash
            val providedHash = sha256(token)
            if (!MessageDigest.isEqual(hexToBytes(tokenHash), hexToBytes(providedHash))) return null

            // Check scope match
            if (tokenScope != scope) return null

            // Check expiry
            val now = currentTime().toEpochMilli()
            if (now - timestamp > TOKEN_TTL_SECONDS * 1000L) {
                vault.deleteSecret(tokenSecretName)
                return null
            }

            // Verify nonce matches (single-use)
            val storedNonce = vault.readSecret("$tokenSecretName-nonce")
            if (storedNonce != nonce) return null

            // Mark token as used by deleting it
            vault.deleteSecret(tokenSecretName)
            vault.deleteSecret("$tokenSecretName-nonce")

            return AuthorizationToken(token = token, scope = scope, issuedAt = java.time.Instant.ofEpochMilli(timestamp))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a new single-use authorization token for the given scope.
     */
    private fun generateToken(scope: String): AuthorizationToken {
        val tokenBytes = ByteArray(TOKEN_LENGTH)
        SecureRandom().nextBytes(tokenBytes)
        val token = bytesToHex(tokenBytes)
        val tokenHash = sha256(token)
        val timestamp = currentTime().toEpochMilli()
        val nonce = bytesToHex(ByteArray(16).also { SecureRandom().nextBytes(it) })

        val stored = "$tokenHash:$scope:$timestamp:$nonce"
        vault.writeSecret(tokenSecretName, stored)
        vault.writeSecret("$tokenSecretName-nonce", nonce)

        return AuthorizationToken(token = token, scope = scope, issuedAt = currentTime())
    }

    // --- Password hashing ---

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS, keyLength: Int = PBKDF2_KEY_LENGTH): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray = pbkdf2(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    // --- Failed attempt tracking ---

    private fun recordFailedAttempt() {
        val count = getFailedAttempts() + 1
        val timestamp = currentTime().toEpochMilli()
        vault.writeSecret("phase20-failed-attempts", "$count:$timestamp")
    }

    private fun getFailedAttempts(): Int {
        val result = vault.readSecret("phase20-failed-attempts")
        return result?.split(':')?.firstOrNull()?.toIntOrNull() ?: 0
    }

    private fun clearFailedAttempts() {
        vault.deleteSecret("phase20-failed-attempts")
    }

    // --- Interactive password reading ---

    private fun readPasswordInteractive(prompt: String): String? {
        return console?.readPassword(prompt)?.joinToString("")?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    // --- Clock for testability --

    private fun currentTime(): java.time.Instant = clock()
}

/**
 * Result of owner authorization setup.
 */
sealed class SetupResult {
    object Success : SetupResult()
    data class Failed(val reason: String) : SetupResult()
}

/**
 * Single-use authorization token for a Phase 20 operation.
 *
 * Carries the operation scope and issuance timestamp. Must be validated
 * by the Phase 20 operation before any mutation occurs.
 */
data class AuthorizationToken(
    val token: String,
    val scope: String,
    val issuedAt: java.time.Instant
) {
    /**
     * Renders the token for logging (redacted).
     */
    fun render(): String = "AuthorizationToken(scope=$scope, issuedAt=$issuedAt, token=***REDACTED***)"
}
