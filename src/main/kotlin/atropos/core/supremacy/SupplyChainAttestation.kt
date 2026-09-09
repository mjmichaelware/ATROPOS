/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-018: Supply-Chain Attestation Binary/Response
 *
 * Implements supply chain attestation for verifying the integrity
 * of dependencies and build artifacts.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

object SupplyChainAttestation {
    data class Attestation(
        val id: String = java.util.UUID.randomUUID().toString(),
        val subject: String, // Artifact identifier
        val hash: String,    // SHA-256 of artifact
        val algorithm: String = "SHA-256",
        val signer: String,  // Who signed this
        val timestamp: Instant = Instant.now(),
        val metadata: Map<String, String> = emptyMap()
    )

    data class VerificationResult(
        val attestation: Attestation,
        val valid: Boolean,
        val reason: String? = null
    )

    private val attestations = mutableListOf<Attestation>()

    /**
     * Creates an attestation for an artifact.
     */
    fun attest(
        subject: String,
        artifactPath: Path,
        signer: String,
        metadata: Map<String, String> = emptyMap()
    ): Attestation {
        val hash = computeHash(artifactPath)
        val attestation = Attestation(
            subject = subject,
            hash = hash,
            signer = signer,
            metadata = metadata
        )
        attestations.add(attestation)
        return attestation
    }

    /**
     * Verifies an attestation against an artifact.
     */
    fun verify(attestation: Attestation, artifactPath: Path): VerificationResult {
        val actualHash = computeHash(artifactPath)
        val valid = actualHash == attestation.hash
        return VerificationResult(
            attestation = attestation,
            valid = valid,
            reason = if (valid) null else "Hash mismatch: expected ${attestation.hash}, got $actualHash"
        )
    }

    /**
     * Computes SHA-256 hash of a file.
     */
    fun computeHash(path: Path): String {
        val bytes = Files.readAllBytes(path)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Persists attestations to CAS.
     */
    fun persistAttestations(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("supply-chain-attestations.json")
        val json = attestations.map { a ->
            """
                {
                    "id": "${a.id}",
                    "subject": "${a.subject}",
                    "hash": "${a.hash}",
                    "algorithm": "${a.algorithm}",
                    "signer": "${a.signer}",
                    "timestamp": "${a.timestamp}",
                    "metadata": ${a.metadata.joinToString(",") { "\"$it.key\": \"$it.value\"" }}
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to attest an artifact.
     */
    fun attestAndShow(subject: String, path: Path, signer: String): String {
        val attestation = attest(subject, path, signer)
        return "Attested $subject: ${attestation.hash} (by ${attestation.signer})"
    }
}