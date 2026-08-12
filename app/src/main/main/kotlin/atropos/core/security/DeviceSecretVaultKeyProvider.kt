/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

/**
 * Resolves the vault key without requiring the operator to have one already.
 *
 * The problem this closes: [EnvironmentSecretVaultKeyProvider] refuses with
 * `MISSING` when `ATROPOS_VAULT_KEY` is unset, and it was the only provider wired
 * into the product. Someone who downloads or curls ATROPOS onto a fresh device
 * therefore could not store a single secret until they had hand-generated a
 * base64 AES-256 key. That makes working software a property of one machine's
 * shell history, which is exactly the coupling the portability requirement
 * forbids — the same binary must behave identically for anyone, anywhere.
 *
 * Precedence, and each step is deliberate:
 * 1. **Operator key wins.** If `ATROPOS_VAULT_KEY` is set, it is used. CI, key
 *    rotation, and shared-key deployments all depend on the operator being able
 *    to override the device.
 * 2. **A malformed operator key is refused, never bypassed.** If the variable is
 *    present but unusable, this returns that refusal instead of quietly falling
 *    back to the device key. Falling back would mean an operator who fat-fingered
 *    a rotation silently keeps writing under the old key and believes otherwise.
 * 3. **Otherwise the device key.** Read from disk, or generated on first use with
 *    a CSPRNG and stored with owner-only permissions.
 *
 * ## The honest limit of a device key
 *
 * The key file sits alongside the ciphertext it protects, so anyone who can read
 * the vault directory can read both. That is not encryption against a local
 * attacker with filesystem access, and this class does not pretend otherwise.
 * What it does buy is real: secrets are no longer at rest in plaintext, so they
 * do not leak through backups, `grep`, screen sharing, log collection, or an
 * accidental `cat` — the paths credentials actually escape through in practice.
 * On a single-user phone or Termux home the filesystem is the trust boundary and
 * owner-only permissions are meaningful.
 *
 * A platform with a real keystore should supply its own [SecretVaultKeyProvider]
 * rather than use this one. Android is the case that matters: its Keystore can
 * hold a key the app itself cannot export, which this cannot. The port exists so
 * that substitution is a constructor argument, not a rewrite.
 */
class DeviceSecretVaultKeyProvider(
    private val vaultRoot: Path,
    private val operatorProvider: SecretVaultKeyProvider = EnvironmentSecretVaultKeyProvider(),
    private val keyFileName: String = DEFAULT_KEY_FILE_NAME,
    private val random: SecureRandom = SecureRandom()
) : SecretVaultKeyProvider {

    override fun load(): SecretVaultKeyResult {
        when (val operator = operatorProvider.load()) {
            is SecretVaultKeyResult.Available -> return operator
            is SecretVaultKeyResult.Refused ->
                // Only a genuinely absent operator key falls through to the device
                // key. A present-but-broken one is the operator's problem to see.
                if (operator.reason != SecretVaultKeyFailure.MISSING) return operator
        }

        return runCatching { loadOrCreateDeviceKey() }
            .getOrElse { SecretVaultKeyResult.Refused(SecretVaultKeyFailure.MISSING) }
    }

    /** True when this device has already provisioned a key. Never reads the bytes. */
    fun isProvisioned(): Boolean = Files.isRegularFile(keyPath())

    private fun keyPath(): Path = vaultRoot.toAbsolutePath().normalize().resolve(keyFileName)

    private fun loadOrCreateDeviceKey(): SecretVaultKeyResult {
        val path = keyPath()
        if (Files.isRegularFile(path)) {
            val encoded = Files.readString(path).trim()
            val decoded = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
                ?: return SecretVaultKeyResult.Refused(SecretVaultKeyFailure.MALFORMED_ENCODING)
            if (decoded.size != KEY_BYTES) {
                return SecretVaultKeyResult.Refused(SecretVaultKeyFailure.INVALID_LENGTH)
            }
            return SecretVaultKeyResult.Available(SecretKeySpec(decoded, "AES"))
        }
        return SecretVaultKeyResult.Available(SecretKeySpec(generateAndStore(path), "AES"))
    }

    private fun generateAndStore(path: Path): ByteArray {
        val material = ByteArray(KEY_BYTES).also(random::nextBytes)
        Files.createDirectories(path.parent)
        restrict(path.parent, DIRECTORY_PERMISSIONS)

        // Write then restrict is a window where the key is world-readable, so the
        // file is created with the restrictive mode up front where POSIX allows it.
        val created = runCatching {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))
        }
        if (created.isFailure && !Files.exists(path)) Files.createFile(path)
        Files.writeString(path, Base64.getEncoder().encodeToString(material))
        restrict(path, FILE_PERMISSIONS)
        return material
    }

    /** Best effort: a filesystem without POSIX permissions must not fail the write. */
    private fun restrict(path: Path, permissions: Set<PosixFilePermission>) {
        runCatching { Files.setPosixFilePermissions(path, permissions) }
    }

    companion object {
        const val DEFAULT_KEY_FILE_NAME: String = "device-vault.key"
        private const val KEY_BYTES = 32

        private val FILE_PERMISSIONS: Set<PosixFilePermission> =
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)

        private val DIRECTORY_PERMISSIONS: Set<PosixFilePermission> = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
        )
    }
}
