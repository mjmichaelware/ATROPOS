package atropos.core.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Performs authenticated AES-GCM encryption for vault payloads. */
internal class VaultCipher(
    private val key: SecretKey,
    private val random: SecureRandom = SecureRandom()
) {
    init {
        require(key.algorithm.equals("AES", ignoreCase = true)) { "vault key algorithm unsupported" }
        require(key.encoded?.size == AES_256_KEY_BYTES) { "vault key must be AES-256" }
    }

    fun encrypt(plainText: ByteArray, associatedData: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES)
        random.nextBytes(nonce)
        val cipherText = cipher(Cipher.ENCRYPT_MODE, nonce, associatedData).doFinal(plainText)
        return ByteBuffer.allocate(MAGIC.size + nonce.size + cipherText.size)
            .put(MAGIC)
            .put(nonce)
            .put(cipherText)
            .array()
    }

    fun decrypt(payload: ByteArray, associatedData: ByteArray): ByteArray? {
        if (!hasSupportedEnvelope(payload)) return null
        val nonceStart = MAGIC.size
        val nonce = payload.copyOfRange(nonceStart, nonceStart + NONCE_BYTES)
        val cipherText = payload.copyOfRange(nonceStart + NONCE_BYTES, payload.size)
        return try {
            cipher(Cipher.DECRYPT_MODE, nonce, associatedData).doFinal(cipherText)
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    fun hasSupportedEnvelope(payload: ByteArray): Boolean =
        payload.size > MAGIC.size + NONCE_BYTES + TAG_BYTES &&
            payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    companion object {
        fun associatedData(secretFileName: String): ByteArray =
            "ATROPOS_VAULT_V1:$secretFileName".toByteArray(Charsets.UTF_8)

        private val MAGIC = byteArrayOf('A'.code.toByte(), 'T'.code.toByte(), 'V'.code.toByte(), 1)
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private const val TAG_BITS = TAG_BYTES * 8
        private const val AES_256_KEY_BYTES = 32
    }

    private fun cipher(mode: Int, nonce: ByteArray, associatedData: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(associatedData)
        }
}
