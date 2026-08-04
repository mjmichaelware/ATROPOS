package atropos.core.security

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecretVaultKeyProviderTest {
    @Test
    fun missing_key_is_typed_without_secret_material() {
        val result = EnvironmentSecretVaultKeyProvider(emptyMap()).load()

        assertEquals(SecretVaultKeyFailure.MISSING, (result as SecretVaultKeyResult.Refused).reason)
    }

    @Test
    fun malformed_and_wrong_length_keys_fail_closed() {
        val malformed = EnvironmentSecretVaultKeyProvider(mapOf("ATROPOS_VAULT_KEY" to "not-base64")).load()
        val short = EnvironmentSecretVaultKeyProvider(
            mapOf("ATROPOS_VAULT_KEY" to Base64.getEncoder().encodeToString(ByteArray(16)))
        ).load()

        assertEquals(SecretVaultKeyFailure.MALFORMED_ENCODING, (malformed as SecretVaultKeyResult.Refused).reason)
        assertEquals(SecretVaultKeyFailure.INVALID_LENGTH, (short as SecretVaultKeyResult.Refused).reason)
    }

    @Test
    fun valid_key_is_available_only_at_required_length() {
        val encoded = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val result = EnvironmentSecretVaultKeyProvider(mapOf("ATROPOS_VAULT_KEY" to encoded)).load()

        assertTrue(result is SecretVaultKeyResult.Available)
        assertEquals("AES", (result as SecretVaultKeyResult.Available).key.algorithm)
    }
}
