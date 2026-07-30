package atropos.core.security

import java.nio.file.Files
import java.util.Base64
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Operator-facing proof that exercises the production environment key provider.
 *
 * The key reaches the production [EnvironmentSecretVaultKeyProvider] through its
 * injectable environment map instead of the host's real environment. That
 * distinction is the point: the class under test is the production one, but the
 * proof does not require the machine running it to already hold a vault key.
 *
 * Reading the real environment here would make `./gradlew build` fail for everyone
 * who clones or curls this project without first exporting a secret — which
 * contradicts the portability requirement that any device anywhere gets the same
 * guarantee, and would make a green build a property of one workstation rather
 * than of the code.
 */
class SecretVaultRuntimeProofTest {

    private fun freshKeyEnvironment(): Map<String, String> {
        val generated = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return mapOf(
            EnvironmentSecretVaultKeyProvider.DEFAULT_VARIABLE_NAME to
                Base64.getEncoder().encodeToString(generated.encoded)
        )
    }

    @Test
    fun external_key_round_trip_tamper_and_name_binding_are_real() {
        val environment = freshKeyEnvironment()
        val provider = { EnvironmentSecretVaultKeyProvider(environment) }

        val key = assertNotNull(provider().load() as? SecretVaultKeyResult.Available)
        assertEquals("AES", key.key.algorithm)

        val root = Files.createTempDirectory("atropos-vault-runtime-proof-")
        val vault = TokenIsolationVault(root, provider())
        val path = vault.writeSecret("RUNTIME_KEY", "runtime-proof-secret")

        // Round trip works, and the plaintext is genuinely absent from the file.
        assertEquals("runtime-proof-secret", vault.readSecret("RUNTIME_KEY"))
        assertFalse(Files.readAllBytes(path).decodeToString().contains("runtime-proof-secret"))

        // Name binding: ciphertext is bound to the name it was written under, so
        // moving the file to another name must not yield a readable secret.
        Files.move(path, vault.secretFile("MOVED_KEY").toPath())
        assertNull(vault.readSecret("MOVED_KEY"))

        // Tamper detection: flipping one ciphertext bit must fail the read rather
        // than returning corrupted plaintext.
        val restored = vault.writeSecret("RUNTIME_KEY", "runtime-proof-secret")
        val bytes = Files.readAllBytes(restored)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(restored, bytes)
        assertNull(vault.readSecret("RUNTIME_KEY"))
    }

    @Test
    fun a_host_with_no_configured_key_is_refused_as_missing_not_treated_as_unlocked() {
        // Absence must be reported, never faked. A fresh device legitimately has no
        // vault key, and the provider has to say so with a typed reason instead of
        // behaving as though the vault were open.
        val result = EnvironmentSecretVaultKeyProvider(environment = emptyMap()).load()

        val refused = assertIs<SecretVaultKeyResult.Refused>(result)
        assertEquals(SecretVaultKeyFailure.MISSING, refused.reason)
    }

    @Test
    fun a_malformed_key_is_refused_rather_than_silently_downgraded() {
        val result = EnvironmentSecretVaultKeyProvider(
            environment = mapOf(
                EnvironmentSecretVaultKeyProvider.DEFAULT_VARIABLE_NAME to "not-base64!!"
            )
        ).load()

        val refused = assertIs<SecretVaultKeyResult.Refused>(result)
        assertTrue(
            refused.reason == SecretVaultKeyFailure.MALFORMED_ENCODING ||
                refused.reason == SecretVaultKeyFailure.INVALID_LENGTH,
            "expected a typed refusal, got ${refused.reason}"
        )
    }
}
