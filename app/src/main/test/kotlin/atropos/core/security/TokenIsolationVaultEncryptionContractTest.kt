package atropos.core.security

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Focused encryption-at-rest contracts for the public vault API. */
class TokenIsolationVaultEncryptionContractTest {
    @Test
    fun raw_secret_file_contains_ciphertext_not_secret_bytes() {
        val root = Files.createTempDirectory("atropos-vault-ciphertext-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())
        val secret = "ciphertext-contract-secret-7f4e"

        val path = vault.writeSecret("CONTRACT_KEY", secret)
        val payload = Files.readAllBytes(path)

        assertTrue(payload.size > secret.toByteArray(StandardCharsets.UTF_8).size)
        assertFalse(payload.toString(StandardCharsets.UTF_8).contains(secret))
        assertEquals(secret, vault.readSecret("CONTRACT_KEY"))
    }

    @Test
    fun tampered_ciphertext_is_refused_without_plaintext() {
        val root = Files.createTempDirectory("atropos-vault-tamper-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())
        val path = vault.writeSecret("TAMPER_KEY", "tamper-contract-secret-9a31")
        val original = Files.readAllBytes(path)
        val tampered = original.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }

        Files.write(path, tampered)

        assertNotEquals(original.toList(), Files.readAllBytes(path).toList())
        assertFalse(vault.inspectSecret("TAMPER_KEY").isolated)
        assertNull(vault.readSecret("TAMPER_KEY"))
    }

    @Test
    fun replacement_vault_key_refuses_existing_ciphertext() {
        val root = Files.createTempDirectory("atropos-vault-wrong-key-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider(17))
        vault.writeSecret("WRONG_KEY", "wrong-key-contract-secret-b82c")
        val wrongKeyVault = TokenIsolationVault(root, TestSecretVaultKeyProvider(77))

        assertFalse(wrongKeyVault.inspectSecret("WRONG_KEY").isolated)
        assertNull(wrongKeyVault.readSecret("WRONG_KEY"))
    }

    @Test
    fun moving_ciphertext_to_another_secret_name_fails_authentication() {
        val root = Files.createTempDirectory("atropos-vault-aad-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())
        val original = vault.writeSecret("SOURCE_KEY", "name-bound-secret")
        Files.move(original, vault.secretFile("OTHER_KEY").toPath())

        assertNull(vault.readSecret("OTHER_KEY"))
        assertFalse(vault.inspectSecret("OTHER_KEY").isolated)
    }

    @Test
    fun refuses_non_aes_256_keys_from_custom_key_providers() {
        val shortKey = SecretKeySpec(ByteArray(16), "AES")

        assertFalse(runCatching { VaultCipher(shortKey) }.isSuccess)
    }

    @Test
    fun failed_replacement_does_not_leave_a_temporary_ciphertext_file() {
        val root = Files.createTempDirectory("atropos-vault-temp-cleanup-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())
        Files.createDirectory(vault.secretFile("BLOCKED_KEY").toPath())

        val exception = assertFailsWith<IllegalArgumentException> {
            vault.writeSecret("BLOCKED_KEY", "cleanup-contract-secret")
        }
        assertEquals("target path exists and is not a regular file", exception.message)

        Files.list(root).use { files ->
            assertFalse(files.anyMatch { it.fileName.toString().contains(".tmp-") || it.fileName.toString().endsWith(".tmp") })
        }
    }

    @Test
    fun directory_collision_throws_illegal_argument_exception_and_does_not_leak_tmp() {
        val root = Files.createTempDirectory("atropos-vault-collision-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())
        val name = "COLLIDING_DIR"
        val collidingPath = vault.secretFile(name).toPath()

        // Create a directory where the secret file is expected to be
        Files.createDirectories(collidingPath)

        // Writing to it should fail with IllegalArgumentException
        val exception = assertFailsWith<IllegalArgumentException> {
            vault.writeSecret(name, "secret-value")
        }
        assertEquals("target path exists and is not a regular file", exception.message)

        // Ensure no temporary files are left behind
        Files.list(root).use { files ->
            val hasTmp = files.anyMatch {
                val fn = it.fileName.toString()
                fn.contains(".tmp-") || fn.endsWith(".tmp")
            }
            assertFalse(hasTmp, "Temporary files should not be leaked")
        }
    }
}
