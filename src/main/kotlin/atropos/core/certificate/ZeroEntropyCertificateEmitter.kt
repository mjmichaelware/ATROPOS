/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.certificate

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class ZeroEntropyCertificate(
    val specHash: String,
    val negativeSpecHash: String,
    val smtProofsHash: String,
    val tlaCheckHash: String,
    val mutationScoreHash: String,
    val chaosReceiptHash: String,
    val toolchainHash: String,
    val h0PromptLineageHash: String,
    val confidenceHash: String,
    val rootCommitHash: String
)

class ZeroEntropyCertificateEmitter {
    fun emit(cert: ZeroEntropyCertificate, outputDir: Path): Path {
        require(atropos.core.neurosymbolic.ZeroEntropyProofCertificateValidator.isValid(cert)) {
            "Zero entropy certificate failed formal validation"
        }

        val canonicalJson = buildString {
            append("{\n")
            append("  \"specHash\": \"${cert.specHash}\",\n")
            append("  \"negativeSpecHash\": \"${cert.negativeSpecHash}\",\n")
            append("  \"smtProofsHash\": \"${cert.smtProofsHash}\",\n")
            append("  \"tlaCheckHash\": \"${cert.tlaCheckHash}\",\n")
            append("  \"mutationScoreHash\": \"${cert.mutationScoreHash}\",\n")
            append("  \"chaosReceiptHash\": \"${cert.chaosReceiptHash}\",\n")
            append("  \"toolchainHash\": \"${cert.toolchainHash}\",\n")
            append("  \"h0PromptLineageHash\": \"${cert.h0PromptLineageHash}\",\n")
            append("  \"confidenceHash\": \"${cert.confidenceHash}\",\n")
            append("  \"rootCommitHash\": \"${cert.rootCommitHash}\"\n")
            append("}\n")
        }

        Files.createDirectories(outputDir)
        val certFile = outputDir.resolve("zero_entropy_proof.json")
        Files.writeString(certFile, canonicalJson)
        return certFile
    }

    fun computeMerkleRoot(cert: ZeroEntropyCertificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val tuple = listOf(
            cert.specHash,
            cert.negativeSpecHash,
            cert.smtProofsHash,
            cert.tlaCheckHash,
            cert.mutationScoreHash,
            cert.chaosReceiptHash,
            cert.toolchainHash,
            cert.h0PromptLineageHash,
            cert.confidenceHash,
            cert.rootCommitHash
        )
        for (item in tuple) {
            digest.update(item.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
