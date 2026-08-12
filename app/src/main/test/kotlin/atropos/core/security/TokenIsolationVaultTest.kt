package atropos.core.security

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenIsolationVaultTest {
    @Test
    fun isolates_secret_round_trip_inside_vault_root() {
        val root = Files.createTempDirectory("atropos-vault-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())

        val path = vault.writeSecret("OPENAI_API_KEY", "local-secret")
        val report = vault.inspectSecret("OPENAI_API_KEY")

        assertTrue(path.startsWith(root.toAbsolutePath().normalize()))
        assertTrue(report.isolated, report.findings.joinToString("; "))
        assertEquals("local-secret", vault.readSecret("OPENAI_API_KEY"))
        val storedBytes = Files.readAllBytes(path)
            .toString(Charsets.ISO_8859_1)
        assertFalse(storedBytes.contains("local-secret"))
    }

    @Test
    fun sanitizes_secret_names_without_directory_escape() {
        val root = Files.createTempDirectory("atropos-vault-sanitize-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())

        val path = vault.writeSecret("../nested/token", "abc123")

        assertTrue(path.fileName.toString().endsWith(".secret"))
        assertTrue(path.startsWith(root.toAbsolutePath().normalize()))
        assertEquals("abc123", vault.readSecret("../nested/token"))
    }

    @Test
    fun rejects_blank_secret_names_and_values() {
        val root = Files.createTempDirectory("atropos-vault-reject-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())

        assertFailsWith<IllegalArgumentException> { vault.writeSecret("   ", "value") }
        assertFailsWith<IllegalArgumentException> { vault.writeSecret("TOKEN", "   ") }
    }

    @Test
    fun refuses_to_read_secret_paths_that_are_symbolic_links() {
        val root = Files.createTempDirectory("atropos-vault-symlink-")
        val outside = Files.createTempFile("atropos-outside-secret-", ".txt")
        Files.writeString(outside, "outside-secret\n")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())
        val secretPath = vault.secretFile("GROQ_API_KEY").toPath()
        val symlinkCreated = runCatching {
            Files.createSymbolicLink(secretPath, outside)
        }.isSuccess
        if (!symlinkCreated) return

        val report = vault.inspectSecret("GROQ_API_KEY")

        assertFalse(report.isolated)
        assertTrue(report.findings.any { it.contains("symbolic link") })
        assertEquals(null, vault.readSecret("GROQ_API_KEY"))
    }

    @Test
    fun stores_ciphertext_and_rejects_tampered_payload() {
        val root = Files.createTempDirectory("atropos-vault-encrypted-")
        val vault = TokenIsolationVault(root, TestSecretVaultKeyProvider())
        val path = vault.writeSecret("TOKEN", "secret-at-rest")

        assertFalse(Files.readAllBytes(path).toString(Charsets.ISO_8859_1).contains("secret-at-rest"))
        assertTrue(vault.inspectSecret("TOKEN").isolated)

        val payload = Files.readAllBytes(path)
        payload[payload.lastIndex] = (payload.last().toInt() xor 0x01).toByte()
        Files.write(path, payload)

        assertFalse(vault.inspectSecret("TOKEN").isolated)
        assertEquals(null, vault.readSecret("TOKEN"))
    }
}
