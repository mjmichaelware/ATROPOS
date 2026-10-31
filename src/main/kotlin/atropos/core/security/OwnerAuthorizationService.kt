/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import java.io.Console
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.spec.PBEKeySpec
import java.time.Instant
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEParameterSpec

/**
 * Owner Authorization Service
 *
 * Provides local installation owner authentication for Phase 20 operations.
 * This is a separate, stricter authorization layer than Phase20Authorization:
 * - Uses a dedicated shadow file (~/.atropos/auth/owner.shadow) with 0600 permissions
 * - Enforces scope binding to exact file paths
 * - Rate-limits failed attempts with exponential backoff
 * - Fails closed in non-interactive environments
 * - Explicitly blocks authorization from dopamine rewards, memory recalls, HF exports, and autonomous loops
 *
 * ## Trust Boundary
 *
 * - The owner password is never stored in plaintext, never accepted via CLI arguments,
 *   never logged, and never held in memory beyond the verification scope.
 * - A salted PBKDF2WithHmacSHA256 hash (100,000 iterations) is stored in the shadow file.
 * - The shadow file is created with owner-only permissions (0600).
 * - Authorization is granted per-operation and per-scope; a token authorizes
 *   exactly one reviewed Phase 20 operation and expires after use.
 * - Dopamine reward updates (+1/-1), memory recalls, Hugging Face exports,
 *   and autonomous agent loops CANNOT grant or trigger Phase 20 authorization.
 *
 * ## Limitations
 *
 * - A local password is not a defense against a machine administrator or malicious
 *   process that already has unrestricted access to the machine. The shadow file
 *   and any derived keys are both stored on the same filesystem. An attacker with
 *   root access can extract both.
 * - This mechanism raises the bar for remote/code-injection attacks and prevents
 *   accidental or automated self-modification. It does not replace OS-level
 *   access controls.
 * - In non-interactive environments (CI, headless), authorization fails closed
 *   unless a valid pre-authorization token is supplied through the approved
 *   environment variable `ATROPOS_OWNER_AUTH_TOKEN`.
 */
class OwnerAuthorizationService(
    private val configRoot: Path = AtroposConfig.configRoot(),
    private val clock: () -> Instant = { Instant.now() },
    private val console: Console? = System.console(),
    val decoupledCryptographicOwnerGate: DecoupledCryptographicOwnerGate = DecoupledCryptographicOwnerGate(clock)
) {
    companion object {
        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val TOKEN_LENGTH = 32
        private const val TOKEN_TTL_SECONDS = 300 // 5 minutes
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val BASE_BACKOFF_SECONDS = 2
        private const val SHADOW_FILE_NAME = "owner.shadow"
        private const val AUTH_DIR_NAME = "auth"
        private const val TOKEN_SECRET_NAME = "owner-auth-token"
        private const val NONCE_SECRET_NAME = "owner-auth-token-nonce"
        private const val FAILED_ATTEMPTS_SECRET_NAME = "owner-failed-attempts"
        private const val LAST_FAILED_ATTEMPT_SECRET_NAME = "owner-last-failed-attempt"

        // Scope prefixes that are explicitly forbidden from granting Phase 20 authorization
        private val FORBIDDEN_AUTH_SOURCES = setOf(
            "dopamine-reward",
            "memory-recall",
            "huggingface-export",
            "autonomous-loop",
            "self-improvement-loop",
            "governance-observation"
        )
    }

    private val shadowDir: Path = configRoot.resolve(AUTH_DIR_NAME)
    private val shadowFile: Path = shadowDir.resolve(SHADOW_FILE_NAME)
    private val failedAttemptsCache = ConcurrentHashMap<String, Long>()

    private val isHeadless: Boolean
        get() = console == null

    init {
        ensureShadowDirectory()
    }

    /**
     * Checks if owner authorization is configured.
     */
    fun isConfigured(): Boolean {
        return Files.isRegularFile(shadowFile) && hasOwnerOnlyPermissions(shadowFile)
    }

    /**
     * Sets up owner authorization with a new password.
     *
     * Must be run interactively. Stores a salted PBKDF2 hash in the shadow file
     * with 0600 permissions.
     *
     * @param password Password provided interactively (never via CLI args)
     * @return SetupResult indicating success or failure reason
     */
    fun setup(password: String? = null): SetupResult {
        val pwd = password ?: readPasswordInteractive("Enter Phase 20 owner password: ") ?: return SetupResult.Failed("no password provided")
        val confirm = readPasswordInteractive("Confirm Phase 20 owner password: ") ?: return SetupResult.Failed("no confirmation provided")
        if (pwd != confirm) return SetupResult.Failed("passwords do not match")
        if (pwd.length < 16) return SetupResult.Failed("password must be at least 16 characters")

        val salt = generateSalt()
        val hash = pbkdf2(pwd, salt)
        val stored = "$PBKDF2_ITERATIONS:$PBKDF2_KEY_LENGTH:${bytesToHex(salt)}:${bytesToHex(hash)}"

        try {
            Files.createDirectories(shadowDir)
            Files.writeString(shadowFile, stored, StandardCharsets.UTF_8)
            setOwnerOnlyPermissions(shadowFile)
            return SetupResult.Success
        } catch (e: Exception) {
            return SetupResult.Failed("failed to store authorization: ${e.message}")
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
     * Removes owner authorization (disables Phase 20 owner authorization).
     */
    fun disable(): Boolean = runCatching { Files.deleteIfExists(shadowFile) }.getOrDefault(false)

    /**
     * Verifies the provided password against the stored hash.
     */
    fun verifyPassword(password: String): Boolean {
        if (!isConfigured()) return false
        val stored = Files.readString(shadowFile, StandardCharsets.UTF_8).trim()
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
     * The scope must be an exact, canonical path or set of paths that the operation
     * is authorized to modify. Authorization tokens are bound to this scope and
     * cannot be used for any other paths.
     *
     * In interactive mode, prompts for password and returns a single-use token.
     * In non-interactive mode, validates `ATROPOS_OWNER_AUTH_TOKEN` environment variable.
     *
     * @param scope Exact file paths (relative to repo root) that this authorization covers.
     *              Must not be empty. Each path is canonicalized and verified.
     * @param source The source requesting authorization. Sources in FORBIDDEN_AUTH_SOURCES
     *               will cause immediate failure (dopamine, memory, HF export, autonomous loops).
     * @return AuthorizationToken if authorized, null if refused or unavailable.
     */
    fun requireAuthorization(
        scope: List<String>,
        source: String = "interactive"
    ): AuthorizationToken? {
        // Block forbidden authorization sources
        if (FORBIDDEN_AUTH_SOURCES.contains(source.lowercase())) {
            return null
        }

        // Validate scope: must be non-empty, canonical paths within repo
        val repoRoot = AtroposRepoRootLocator.resolve()
        val canonicalScope = scope.mapNotNull { path ->
            canonicalizeAndVerify(path, repoRoot)
        }
        if (canonicalScope.isEmpty()) {
            return null
        }
        if (canonicalScope.size != scope.size) {
            return null // Some paths failed canonicalization
        }
        val scopeString = canonicalScope.sorted().joinToString("|")

        // Check for pre-authorization token in non-interactive environments
        val envToken = System.getenv("ATROPOS_OWNER_AUTH_TOKEN")
        if (envToken != null) {
            return validateToken(envToken, scopeString)
        }

        // Check rate limiting
        if (isRateLimited()) {
            return null
        }

        // Interactive authorization is unavailable without a console. Fail closed
        // instead of allowing Kotlin's non-null platform type to throw at startup.
        if (isHeadless) {
            return null
        }

        // Prompt for password
        val password = readPasswordInteractive("Phase 20 owner authorization required for: $scopeString\nEnter owner password: ")
            ?: return null

        if (!verifyPassword(password)) {
            recordFailedAttempt()
            return null
        }

        // Check for excessive failed attempts (after successful verification to avoid lockout on typo)
        if (getFailedAttempts() > MAX_FAILED_ATTEMPTS) {
            return null
        }

        // Clear failed attempts on success
        clearFailedAttempts()

        // Generate and store single-use token
        return generateToken(scopeString)
    }

    /**
     * Validates an authorization token for the given scope.
     */
    fun validateToken(token: String, scope: String): AuthorizationToken? {
        val stored = readSecret(TOKEN_SECRET_NAME)
            ?: return null
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

            // Check scope match (exact match required)
            if (tokenScope != scope) return null

            // Check expiry
            val now = clock().toEpochMilli()
            if (now - timestamp > TOKEN_TTL_SECONDS * 1000L) {
                deleteSecret(TOKEN_SECRET_NAME)
                deleteSecret(NONCE_SECRET_NAME)
                return null
            }

            // Verify nonce matches (single-use)
            val storedNonce = readSecret(NONCE_SECRET_NAME)
            if (storedNonce != nonce) return null

            // Mark token as used by deleting it
            deleteSecret(TOKEN_SECRET_NAME)
            deleteSecret(NONCE_SECRET_NAME)

            val authToken = AuthorizationToken(token = token, scope = scope, issuedAt = Instant.ofEpochMilli(timestamp))
            DecoupledOwnerIdentityAuthorizer.bindAuthority(authToken, OwnerIdentityRole.SOVEREIGN_OWNER)
            return authToken
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
        val timestamp = clock().toEpochMilli()
        val nonce = bytesToHex(ByteArray(16).also { SecureRandom().nextBytes(it) })

        val stored = "$tokenHash:$scope:$timestamp:$nonce"
        writeSecret(TOKEN_SECRET_NAME, stored)
        writeSecret(NONCE_SECRET_NAME, nonce)

        return AuthorizationToken(token = token, scope = scope, issuedAt = clock())
    }

    // --- Password hashing ---

    private fun pbkdf2(
        password: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
        keyLength: Int = PBKDF2_KEY_LENGTH
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

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

    // --- Rate limiting with exponential backoff ---

    private fun recordFailedAttempt() {
        val now = clock().toEpochMilli()
        val count = getFailedAttempts() + 1
        writeSecret(FAILED_ATTEMPTS_SECRET_NAME, count.toString())
        writeSecret(LAST_FAILED_ATTEMPT_SECRET_NAME, now.toString())
    }

    private fun getFailedAttempts(): Int {
        return readSecret(FAILED_ATTEMPTS_SECRET_NAME)?.toIntOrNull() ?: 0
    }

    private fun getLastFailedAttempt(): Long? {
        return readSecret(LAST_FAILED_ATTEMPT_SECRET_NAME)?.toLongOrNull()
    }

    private fun clearFailedAttempts() {
        deleteSecret(FAILED_ATTEMPTS_SECRET_NAME)
        deleteSecret(LAST_FAILED_ATTEMPT_SECRET_NAME)
    }

    private fun isRateLimited(): Boolean {
        val attempts = getFailedAttempts()
        if (attempts <= MAX_FAILED_ATTEMPTS) return false

        // Exponential backoff: 2^attempts seconds, capped at 1 hour
        val lastAttempt = getLastFailedAttempt() ?: return true
        val now = clock().toEpochMilli()
        val backoffMs = Math.min(BASE_BACKOFF_SECONDS.toLong() * (2L shl (attempts - MAX_FAILED_ATTEMPTS - 1)), 3600_000L) * 1000L
        return (now - lastAttempt) < backoffMs
    }

    // --- Shadow file security ---

    private fun ensureShadowDirectory() {
        try {
            Files.createDirectories(shadowDir)
            setOwnerOnlyPermissions(shadowDir)
        } catch (_: Exception) {
            // Directory may already exist; permissions will be enforced on file write
        }
    }

    private fun setOwnerOnlyPermissions(path: Path) {
        try {
            val permissions = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            )
            Files.setPosixFilePermissions(path, permissions)
        } catch (_: Exception) {
            // Fallback for non-POSIX filesystems
            path.toFile().apply {
                setReadable(false, false)
                setWritable(false, false)
                setExecutable(false, false)
                setReadable(true, true)
                setWritable(true, true)
            }
        }
    }

    private fun hasOwnerOnlyPermissions(path: Path): Boolean {
        return try {
            val permissions = Files.getPosixFilePermissions(path)
            permissions == setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE) ||
                permissions == setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        } catch (_: Exception) {
            // On non-POSIX, check via File API
            val file = path.toFile()
            file.canRead() && file.canWrite() && !file.canExecute()
        }
    }

    // --- Vault-like secret operations using the shadow directory ---

    private fun writeSecret(name: String, value: String) {
        val path = shadowDir.resolve("$name.secret")
        Files.createDirectories(shadowDir)
        Files.writeString(path, value, StandardCharsets.UTF_8)
        setOwnerOnlyPermissions(path)
    }

    private fun readSecret(name: String): String? {
        val path = shadowDir.resolve("$name.secret")
        return if (Files.isRegularFile(path)) {
            Files.readString(path, StandardCharsets.UTF_8).trim().takeIf { it.isNotBlank() }
        } else null
    }

    private fun deleteSecret(name: String) {
        val path = shadowDir.resolve("$name.secret")
        Files.deleteIfExists(path)
    }

    // --- Path canonicalization and verification ---

    private fun canonicalizeAndVerify(path: String, repoRoot: Path): String? {
        return try {
            val inputPath = Paths.get(path)
            val absolutePath = if (inputPath.isAbsolute) {
                inputPath.toRealPath(LinkOption.NOFOLLOW_LINKS)
            } else {
                repoRoot.resolve(inputPath).toRealPath(LinkOption.NOFOLLOW_LINKS)
            }

            val repoAbsolute = repoRoot.toRealPath(LinkOption.NOFOLLOW_LINKS)
            if (!absolutePath.startsWith(repoAbsolute)) {
                return null
            }

            repoAbsolute.relativize(absolutePath).toString().replace('\\', '/')
        } catch (_: Exception) {
            null
        }
    }

    // --- Interactive password reading ---

    private fun readPasswordInteractive(prompt: String): String? {
        return console?.readPassword(prompt)?.joinToString("")?.trim()
            ?.takeIf { it.isNotBlank() }
    }
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
 * Carries the operation scope (exact file paths) and issuance timestamp.
 * Must be validated by the Phase 20 operation before any mutation occurs.
 */
data class AuthorizationToken(
    val token: String,
    val scope: String,
    val issuedAt: Instant
) {
    /**
     * Renders the token for logging (redacted).
     */
    fun render(): String = "AuthorizationToken(scope=$scope, issuedAt=$issuedAt, token=***REDACTED***)"

    /**
     * Checks if this token authorizes the given path.
     */
    fun authorizes(path: String): Boolean {
        val normalizedPath = path.replace('\\', '/').trim('/')
        val normalizedScope = scope.replace('\\', '/').trim('/')
        return normalizedPath == normalizedScope || normalizedPath.startsWith("$normalizedScope/")
    }
}
