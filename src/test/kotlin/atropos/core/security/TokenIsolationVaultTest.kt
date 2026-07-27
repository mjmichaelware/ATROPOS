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
        val vault = TokenIsolationVault(root)

        val path = vault.writeSecret("OPENAI_API_KEY", "local-secret")

        assertTrue(path.startsWith(root.toAbsolutePath().normalize()))
        assertEquals("local-secret", vault.readSecret("OPENAI_API_KEY"))
        assertFalse(Files.readString(path).contains("local-secret\n\n"))
    }

    @Test
    fun sanitizes_secret_names_without_directory_escape() {
        val root = Files.createTempDirectory("atropos-vault-sanitize-")
        val vault = TokenIsolationVault(root)

        val path = vault.writeSecret("../nested/token", "abc123")

        assertTrue(path.fileName.toString().endsWith(".secret"))
        assertTrue(path.startsWith(root.toAbsolutePath().normalize()))
        assertEquals("abc123", vault.readSecret("../nested/token"))
    }

    @Test
    fun rejects_blank_secret_names_and_values() {
        val root = Files.createTempDirectory("atropos-vault-reject-")
        val vault = TokenIsolationVault(root)

        assertFailsWith<IllegalArgumentException> { vault.writeSecret("   ", "value") }
        assertFailsWith<IllegalArgumentException> { vault.writeSecret("TOKEN", "   ") }
    }
}
