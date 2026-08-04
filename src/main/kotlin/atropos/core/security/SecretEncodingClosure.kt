/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * The set of forms one secret can take on its way out of the process.
 *
 * Why this exists: published measurements put the best pattern-based secret
 * scanners at roughly 88% recall, and text-level filters are evadable by
 * re-encoding — a key that survives redaction as raw text is caught, the same key
 * base64'd into a JSON body is not. Any filter that inspects only the literal
 * spelling of a secret therefore has an unbounded false-negative tail.
 *
 * ATROPOS does not have to guess. It loads its own credentials, so it knows their
 * exact values, and can enumerate the transforms that realistically occur between
 * "value in memory" and "bytes on a screen or in a log": base64 in HTTP bodies,
 * hex in signatures, percent-encoding in URLs, backslash-escaping in JSON, and
 * whitespace mangling in wrapped terminal output.
 *
 * That is the whole trick, and it is why the guarantee is stated over a *closure*
 * rather than over "secrets": recall is exactly 1.0 for any representation in this
 * set and exactly 0 for anything outside it. The set is explicit, enumerable and
 * testable, so the residual risk is nameable instead of unknown — which is the
 * property a percentage-based claim can never give you.
 *
 * Deliberately NOT included, and each omission is a real limitation:
 * - encryption or keyed hashing of the secret (unpredictable output by design),
 * - compression (gzip of a JSON body containing the key),
 * - chunked leakage, where a secret is split across several outputs,
 * - homoglyph substitution inside the secret itself.
 * Tiers 2 and 3 of the egress filter exist to cover part of that tail; nothing
 * covers all of it, and this KDoc is the honest statement of where the boundary is.
 */
object SecretEncodingClosure {

    /** Secrets shorter than this are not enrolled: see [variantsOf]. */
    const val MINIMUM_ENROLLABLE_LENGTH: Int = 8

    /**
     * Every representation of [secret] this guard commits to catching.
     *
     * Returns an empty set for a value too short to enrol. A 4-character "secret"
     * would match constantly in ordinary prose, and a guard that fires on
     * everything gets switched off, so short values are refused rather than
     * silently making the filter useless.
     */
    fun variantsOf(secret: String): Set<String> {
        val trimmed = secret.trim()
        if (trimmed.length < MINIMUM_ENROLLABLE_LENGTH) return emptySet()

        val bytes = trimmed.toByteArray(StandardCharsets.UTF_8)
        val variants = linkedSetOf<String>()

        variants += trimmed
        variants += Base64.getEncoder().encodeToString(bytes)
        variants += Base64.getEncoder().withoutPadding().encodeToString(bytes)
        variants += Base64.getUrlEncoder().encodeToString(bytes)
        variants += Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        variants += bytes.joinToString("") { "%02x".format(it) }
        variants += bytes.joinToString("") { "%02X".format(it) }
        variants += runCatching {
            URLEncoder.encode(trimmed, StandardCharsets.UTF_8)
        }.getOrDefault(trimmed)
        variants += jsonEscaped(trimmed)

        // Terminal wrapping and log collation insert whitespace inside long
        // tokens. A secret split by a line break is still a leaked secret, so the
        // whitespace-free form is enrolled and the scanner also checks output with
        // whitespace removed.
        variants += trimmed.filterNot(Char::isWhitespace)

        return variants.filter { it.length >= MINIMUM_ENROLLABLE_LENGTH }.toSet()
    }

    /**
     * The scan-side counterpart: output with whitespace stripped.
     *
     * Enrolling a whitespace-free variant only helps if the text being scanned is
     * also normalised, otherwise `sk-ab\ncd` matches nothing.
     */
    fun whitespaceStripped(text: String): String = text.filterNot(Char::isWhitespace)

    private fun jsonEscaped(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
