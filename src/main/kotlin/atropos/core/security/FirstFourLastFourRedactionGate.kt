/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.security.MessageDigest

/**
 * ATOM 18.2: First-Four Last-Four Key Masking and Redaction Gate.
 *
 * Enforces strict API key masking across all visual surfaces.
 * Reveals at most the first four and last four characters with bullet masking between.
 * Never exposes complete secrets in HTML attributes, JSON responses, or memory logs.
 *
 * ## Research: Client-Side Memory Scrubbing Techniques in Kotlin & JS
 * Researches memory scrubbing techniques where sensitive credential char arrays (`CharArray`)
 * are explicitly overwritten with zeroes (`Arrays.fill(chars, '\u0000')`) immediately after
 * cryptographic HMAC/digest calculation to prevent heap memory dump extraction.
 */
object FirstFourLastFourRedactionGate {

    fun maskFirstFourLastFour(secret: String): String {
        val trimmed = secret.trim()
        if (trimmed.length <= 8) {
            return "••••••••"
        }
        val prefix = trimmed.take(4)
        val suffix = trimmed.takeLast(4)
        return "$prefix••••••••$suffix"
    }

    fun stableFingerprint(secret: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(secret.toByteArray(Charsets.UTF_8))
        return "fp_" + digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
