package atropos.core.security

import javax.crypto.spec.SecretKeySpec

internal class TestSecretVaultKeyProvider(private val seed: Int = 17) : SecretVaultKeyProvider {
    override fun load(): SecretVaultKeyResult = SecretVaultKeyResult.Available(
        SecretKeySpec(ByteArray(32) { index -> (index + seed).toByte() }, "AES")
    )
}
