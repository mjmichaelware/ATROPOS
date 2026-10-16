/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import atropos.core.certificate.ZeroEntropyCertificate

object ZeroEntropyProofCertificateValidator {

    fun isValid(cert: ZeroEntropyCertificate): Boolean {
        if (cert.specHash.isBlank()) return false
        if (cert.negativeSpecHash.isBlank()) return false
        if (cert.smtProofsHash.isBlank()) return false
        if (cert.tlaCheckHash.isBlank()) return false
        if (cert.mutationScoreHash.isBlank()) return false
        if (cert.chaosReceiptHash.isBlank()) return false
        if (cert.toolchainHash.isBlank()) return false
        if (cert.h0PromptLineageHash.isBlank()) return false
        if (cert.confidenceHash.isBlank()) return false
        if (cert.rootCommitHash.isBlank()) return false

        // Git commit hash must be either 40-char (SHA-1) or 64-char (SHA-256) hex string
        val isGitHash = (cert.rootCommitHash.length == 40 || cert.rootCommitHash.length == 64) &&
                cert.rootCommitHash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        return isGitHash
    }
}
