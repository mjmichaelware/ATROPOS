/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-001: Certificate Root Hash
 *
 * Computes and persists the root certificate hash for the ATROPOS trust chain.
 * The certificate is the root of trust for all verification gates.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object CertificateRootHash {
    /**
     * Computes the SHA-256 root hash of the ATROPOS certificate chain.
     * The certificate chain is stored in the config directory.
     */
    fun compute(configDir: Path): String {
        val certFile = configDir.resolve("certificate.pem")
        if (!Files.isRegularFile(certFile)) {
            // Generate self-signed root certificate if not present
            return generateRootCertificate(configDir)
        }
        val certBytes = Files.readAllBytes(certFile)
        return MessageDigest.getInstance("SHA-256").digest(certBytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Persists the certificate root hash to the CAS.
     */
    fun persistRootHash(configDir: Path, casDir: Path): Path {
        val hash = compute(configDir)
        val hashFile = casDir.resolve("certificate-root-hash.txt")
        Files.createDirectories(casDir)
        Files.writeString(hashFile, hash)
        return hashFile
    }

    /**
     * Generates a self-signed root certificate for ATROPOS.
     */
    private fun generateRootCertificate(configDir: Path): String {
        // In a real implementation, this would use BouncyCastle or similar
        // For now, create a placeholder that documents the expected structure
        val certFile = configDir.resolve("certificate.pem")
        val placeholder = """
            -----BEGIN CERTIFICATE-----
            ATROPOS Root Certificate
            Generated: ${java.time.Instant.now()}
            Subject: CN=ATROPOS Root CA
            This is a placeholder. Replace with real certificate.
            -----END CERTIFICATE-----
        """.trimIndent()
        Files.createDirectories(configDir)
        Files.writeString(certFile, placeholder)
        val bytes = placeholder.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * CLI command to verify the certificate root hash.
     */
    fun verifyRootHash(configDir: Path, expectedHash: String): Boolean {
        val actual = compute(configDir)
        return actual == expectedHash.lowercase()
    }
}