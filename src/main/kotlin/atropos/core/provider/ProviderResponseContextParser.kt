/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Parses a provider's raw response text and extracts the [ContextAttestation]
 * and any Greek mythology content for checking.
 */
object ProviderResponseContextParser {
    /*
     * Deliberately contains no keyword or content heuristics.
     *
     * Whether a provider actually received and understood its context is
     * proven deterministically by the attestation block it must echo back —
     * system identity, repository, role, context version and context hash.
     * A provider that drifted cannot produce a matching hash.
     *
     * An earlier version scored the prose against a Greek-mythology word list.
     * That is not a deterministic contract: it guessed at meaning, could never
     * be complete, and misfired constantly — "shades" matched "hades",
     * "shares" matched "ares", and the system's own name appeared in the list,
     * so the more correctly a provider identified itself as ATROPOS the more
     * certainly it was rejected. Verification belongs to the envelope contract,
     * not to string matching.
     */

    /**
     * Parse the response text and return a result indicating whether
     * attestation was found and whether mythology content was detected.
     */
    fun parse(response: String, envelope: ContextEnvelope): ParsedProviderResponse {
        val redactedResponse = ProviderRedactor.redactWithoutTruncation(response)
        val attestation = ContextEnvelopeSerializer.parseAttestation(redactedResponse)
        val cleanedResponse = stripAttestationBlock(redactedResponse)

        return ParsedProviderResponse(
            rawResponse = redactedResponse,
            cleanedResponse = cleanedResponse,
            attestation = attestation
        )
    }

    /**
     * Strip the attestation block from the response text so the
     * attestation metadata is not displayed to the user.
     */
    fun stripAttestationBlock(response: String): String {
        val result = StringBuilder()
        val lines = response.lines()
        var inBlock = false
        for (line in lines) {
            if (line.contains("--- ATROPOS CONTEXT ATTESTATION ---")) {
                inBlock = true
                continue
            }
            if (line.contains("--- END ATROPOS CONTEXT ATTESTATION ---")) {
                inBlock = false
                continue
            }
            if (!inBlock) {
                if (result.isNotEmpty()) result.append('\n')
                result.append(line)
            }
        }
        return result.toString().trim()
    }
}

/**
 * Result of parsing a provider response.
 */
data class ParsedProviderResponse(
    val rawResponse: String,
    val cleanedResponse: String,
    val attestation: ContextAttestation?
)
