/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.util.regex.Pattern

/**
 * ATOM 24.2: Zero-Retention Password and Credential Protection.
 *
 * Ensures administrative passwords, master keys, and private tokens are never logged,
 * stored in source code, or echoed into agent prompts. Scrubs sensitive tokens from
 * all outbound model contexts and terminal output streams.
 *
 * Research Directive: Automated regex and entropy scanning pipelines with sub-millisecond
 * execution times for streaming token redaction.
 */
class DecoupledZeroRetentionSecretFilter {

    private val credentialPatterns = listOf(
        Pattern.compile("ghp_[a-zA-Z0-9]{36}"),
        Pattern.compile("github_pat_[a-zA-Z0-9_]{82}"),
        Pattern.compile("sk-[a-zA-Z0-9]{48}"),
        Pattern.compile("eyJh[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}"),
        Pattern.compile("password\\s*=\\s*['\"][^'\"]+['\"]", Pattern.CASE_INSENSITIVE)
    )

    fun scrubStream(rawText: String): String {
        var scrubbed = rawText
        for (pattern in credentialPatterns) {
            val matcher = pattern.matcher(scrubbed)
            if (matcher.find()) {
                scrubbed = matcher.replaceAll("[REDACTED_SECRET]")
            }
        }
        return scrubbed
    }

    fun containsHighEntropySecret(token: String): Boolean {
        if (token.length < 16) return false
        val charFrequencies = token.groupingBy { it }.eachCount()
        val length = token.length.toDouble()
        val entropy = charFrequencies.values.fold(0.0) { acc, count ->
            val p = count.toDouble() / length
            acc - (p * (Math.log(p) / Math.log(2.0)))
        }
        // Shannon entropy threshold >= 3.8 indicates high randomness / API key
        return entropy >= 3.8
    }
}
