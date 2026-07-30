/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Tier 1 of the egress filter: exact membership, not pattern guessing.
 *
 * The measured ceiling for pattern-based secret scanners is about 88% recall, and
 * the misses are unbounded in principle — you cannot enumerate every credential
 * format that will ever exist. But ATROPOS only needs to protect the credentials
 * *it holds*, and for those the question is not "does this look like a secret?"
 * but "is this my secret?" — a membership test, which is exact.
 *
 * So for any enrolled value, in any representation from
 * [SecretEncodingClosure.variantsOf], this registry's recall is 1.0 and its false
 * positive rate is 0: an exact match of a real credential is by definition a real
 * leak. Patterns and entropy remain necessary for credentials the process does not
 * know (a key a user pastes into a prompt, or one a provider echoes back), which is
 * what [RedactionFilter]'s pattern tiers are for. The tiers compose; neither
 * replaces the other.
 *
 * ## Why digests instead of the plaintext
 *
 * The obvious implementation keeps the variant strings and calls `contains`. That
 * would mean this class holds a second copy of every credential for the life of
 * the process, so a heap dump or an accidental `toString` of the registry becomes
 * the leak it was built to prevent. Instead each variant is reduced to a digest
 * under a per-process random salt. The registry never stores a recoverable secret,
 * and because the salt is random per process the digests are not a lookup oracle
 * if they are ever written down.
 *
 * ## How scanning stays linear
 *
 * A digest cannot be substring-searched, so the scan slides a window of each
 * enrolled variant's length across the text and digests the window. Cost is
 * O(text length x number of distinct variant lengths); the second factor is tiny
 * and bounded by the credential count, not by the text. Hashing a window per
 * offset is deliberate: it is the price of not holding plaintext.
 */
class KnownSecretRegistry(
    private val salt: ByteArray = randomSalt()
) {
    /** Digest -> the label of the credential it belongs to, for evidence lines. */
    private val digestsByLength: MutableMap<Int, MutableMap<String, String>> = mutableMapOf()

    /** Labels only. Never a value — this is safe to log. */
    val enrolledLabels: MutableSet<String> = linkedSetOf()

    /**
     * Enrols one credential under a non-sensitive [label] (e.g. `GROQ_API_KEY`).
     *
     * @return the number of representations enrolled; 0 means the value was too
     * short to protect, which callers should treat as a refusal rather than success.
     */
    fun enroll(label: String, secret: String): Int {
        val variants = SecretEncodingClosure.variantsOf(secret)
        if (variants.isEmpty()) return 0
        enrolledLabels += label
        variants.forEach { variant ->
            digestsByLength
                .getOrPut(variant.length) { mutableMapOf() }[digest(variant)] = label
        }
        return variants.size
    }

    /** Enrols every non-blank entry, keyed by its own name. */
    fun enrollAll(secrets: Map<String, String>): Int =
        secrets.entries.sumOf { (label, value) ->
            if (value.isBlank()) 0 else enroll(label, value)
        }

    fun isEmpty(): Boolean = digestsByLength.isEmpty()

    /**
     * The labels of every enrolled credential present in [text], in any enrolled
     * representation. Empty means no enrolled credential is present.
     */
    fun findLeaks(text: String): Set<String> {
        if (digestsByLength.isEmpty() || text.isEmpty()) return emptySet()
        val found = linkedSetOf<String>()
        scan(text, found)
        // Also scan with whitespace removed, so a secret broken across a wrapped
        // terminal line or a collated log record is still caught.
        val stripped = SecretEncodingClosure.whitespaceStripped(text)
        if (stripped.length != text.length) scan(stripped, found)
        return found
    }

    /**
     * Replaces every enrolled credential occurrence with a labelled marker.
     *
     * The marker names the credential so an operator can tell *which* key leaked
     * without the value being shown — the evidence requirement and the secrecy
     * requirement are both satisfied.
     */
    fun redact(text: String): String {
        if (digestsByLength.isEmpty() || text.isEmpty()) return text
        var result = text
        for ((length, byDigest) in digestsByLength.entries.sortedByDescending { it.key }) {
            if (length > result.length) continue
            var index = 0
            val builder = StringBuilder(result.length)
            while (index <= result.length - length) {
                val window = result.substring(index, index + length)
                val label = byDigest[digest(window)]
                if (label != null) {
                    builder.append("<redacted:$label>")
                    index += length
                } else {
                    builder.append(result[index])
                    index += 1
                }
            }
            builder.append(result.substring(index))
            result = builder.toString()
        }
        return result
    }

    private fun scan(text: String, into: MutableSet<String>) {
        for ((length, byDigest) in digestsByLength) {
            if (length > text.length) continue
            for (start in 0..(text.length - length)) {
                val label = byDigest[digest(text.substring(start, start + length))]
                if (label != null) into += label
            }
        }
    }

    private fun digest(value: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(value.toByteArray(StandardCharsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        fun randomSalt(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    }
}
