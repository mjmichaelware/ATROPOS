package atropos.core.security

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Operator-facing proof that exercises the production environment key provider. */
class SecretVaultRuntimeProofTest {
    @Test
    fun external_key_round_trip_tamper_and_name_binding_are_real() {
        val loaded = EnvironmentSecretVaultKeyProvider().load()
        val key = assertNotNull(loaded as? SecretVaultKeyResult.Available)
        val root = Files.createTempDirectory("atropos-vault-runtime-proof-")
        val vault = TokenIsolationVault(root, EnvironmentSecretVaultKeyProvider())
        val path = vault.writeSecret("RUNTIME_KEY", "runtime-proof-secret")

        assertEquals("runtime-proof-secret", vault.readSecret("RUNTIME_KEY"))
        assertFalse(Files.readAllBytes(path).decodeToString().contains("runtime-proof-secret"))

        Files.move(path, vault.secretFile("MOVED_KEY").toPath())
        assertNull(vault.readSecret("MOVED_KEY"))

        val restored = vault.writeSecret("RUNTIME_KEY", "runtime-proof-secret")
        val bytes = Files.readAllBytes(restored)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(restored, bytes)
        assertNull(vault.readSecret("RUNTIME_KEY"))
        assertTrue(key.key.algorithm == "AES")
    }
}
