/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The portability predicate for Phase 4: a device that has never been configured
 * must still be able to protect a secret.
 */
class DeviceSecretVaultKeyProviderTest {

    private fun noOperatorKey() = EnvironmentSecretVaultKeyProvider(environment = emptyMap())

    @Test
    fun a_fresh_device_with_no_operator_key_can_still_use_the_vault() {
        val root = Files.createTempDirectory("atropos-device-key-fresh-")
        val provider = DeviceSecretVaultKeyProvider(root, operatorProvider = noOperatorKey())

        assertFalse(provider.isProvisioned(), "nothing should exist before first use")

        val loaded = assertNotNull(provider.load() as? SecretVaultKeyResult.Available)
        assertEquals("AES", loaded.key.algorithm)
        assertEquals(32, loaded.key.encoded.size, "must be AES-256")
        assertTrue(provider.isProvisioned(), "first use must persist the device key")

        // The whole point: the vault works end to end with no human-supplied key.
        val vault = TokenIsolationVault(root, DeviceSecretVaultKeyProvider(root, noOperatorKey()))
        val path = vault.writeSecret("JOE_API_KEY", "joe-downloaded-atropos")
        assertEquals("joe-downloaded-atropos", vault.readSecret("JOE_API_KEY"))
        assertFalse(
            Files.readAllBytes(path).decodeToString().contains("joe-downloaded-atropos"),
            "the secret must be ciphertext at rest even with a device key"
        )
    }

    @Test
    fun the_device_key_is_stable_across_restarts() {
        val root = Files.createTempDirectory("atropos-device-key-stable-")

        val first = assertNotNull(
            DeviceSecretVaultKeyProvider(root, noOperatorKey()).load() as? SecretVaultKeyResult.Available
        )
        // A second process on the same device must resolve the same key, or every
        // restart would orphan the secrets written before it.
        val second = assertNotNull(
            DeviceSecretVaultKeyProvider(root, noOperatorKey()).load() as? SecretVaultKeyResult.Available
        )

        assertTrue(first.key.encoded.contentEquals(second.key.encoded))
    }

    @Test
    fun two_devices_do_not_share_a_key() {
        val a = Files.createTempDirectory("atropos-device-key-a-")
        val b = Files.createTempDirectory("atropos-device-key-b-")

        val keyA = assertNotNull(
            DeviceSecretVaultKeyProvider(a, noOperatorKey()).load() as? SecretVaultKeyResult.Available
        )
        val keyB = assertNotNull(
            DeviceSecretVaultKeyProvider(b, noOperatorKey()).load() as? SecretVaultKeyResult.Available
        )

        assertFalse(
            keyA.key.encoded.contentEquals(keyB.key.encoded),
            "a per-device key that is identical everywhere is a shipped shared secret"
        )
    }

    @Test
    fun an_operator_key_overrides_the_device_key() {
        val root = Files.createTempDirectory("atropos-device-key-override-")
        val operatorMaterial = ByteArray(32) { 7 }
        val operatorEnvironment = mapOf(
            EnvironmentSecretVaultKeyProvider.DEFAULT_VARIABLE_NAME to
                Base64.getEncoder().encodeToString(operatorMaterial)
        )

        val resolved = assertNotNull(
            DeviceSecretVaultKeyProvider(
                root,
                operatorProvider = EnvironmentSecretVaultKeyProvider(operatorEnvironment)
            ).load() as? SecretVaultKeyResult.Available
        )

        assertTrue(resolved.key.encoded.contentEquals(operatorMaterial), "operator key must win")
    }

    @Test
    fun a_malformed_operator_key_is_refused_instead_of_falling_back() {
        val root = Files.createTempDirectory("atropos-device-key-malformed-")

        val result = DeviceSecretVaultKeyProvider(
            root,
            operatorProvider = EnvironmentSecretVaultKeyProvider(
                mapOf(EnvironmentSecretVaultKeyProvider.DEFAULT_VARIABLE_NAME to "not-base64!!")
            )
        ).load()

        // Falling back here would let a fat-fingered rotation keep writing under a
        // different key while the operator believes the new one is in force.
        assertIs<SecretVaultKeyResult.Refused>(result)
        assertFalse(
            DeviceSecretVaultKeyProvider(root, noOperatorKey()).isProvisioned(),
            "a refused operator key must not silently provision a device key"
        )
    }

    @Test
    fun a_corrupted_device_key_file_is_refused_with_a_typed_reason() {
        val root = Files.createTempDirectory("atropos-device-key-corrupt-")
        val provider = DeviceSecretVaultKeyProvider(root, noOperatorKey())
        provider.load()

        Files.writeString(
            root.resolve(DeviceSecretVaultKeyProvider.DEFAULT_KEY_FILE_NAME),
            "%%% not base64 %%%"
        )

        val result = DeviceSecretVaultKeyProvider(root, noOperatorKey()).load()

        val refused = assertIs<SecretVaultKeyResult.Refused>(result)
        assertEquals(SecretVaultKeyFailure.MALFORMED_ENCODING, refused.reason)
    }
}
