/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Parses a provider's raw response text and extracts the [ContextAttestation]
 * and any Greek mythology content for checking.
 */
object ProviderResponseContextParser {

    /**
     * Parse the response text and return a result indicating whether
     * attestation was found and whether mythology content was detected.
     */
    fun parse(response: String, envelope: ContextEnvelope): ParsedProviderResponse {
        val attestation = ContextEnvelopeSerializer.parseAttestation(response)
        val mythologyDetected = detectMythology(response, envelope)
        val cleanedResponse = stripAttestationBlock(response)

        return ParsedProviderResponse(
            rawResponse = response,
            cleanedResponse = cleanedResponse,
            attestation = attestation,
            mythologyDetected = mythologyDetected
        )
    }

    /**
     * Detect unsolicited Greek mythology references in the response.
     * Returns true if the response contains Greek mythology content
     * that was NOT explicitly requested.
     */
    private fun detectMythology(response: String, envelope: ContextEnvelope): Boolean {
        val lower = response.lowercase()
        val mythologyTerms = listOf(
            "greek mythology", "greek god", "greek goddess",
            "zeus", "hades", "poseidon", "athena", "apollo", "artemis",
            "ares", "aphrodite", "hermes", "dionysus", "demeter", "hestia",
            "the three fates", "clotho", "lachesis",
            "moirai", "titan", "olympus", "mount olympus",
            "son of zeus", "daughter of zeus", "wife of zeus",
            "trident", "underworld", "river styx"
        )

        // Check for "ATROPOS" specifically in a mythological context
        val atroposMythology = run {
            if (!lower.contains("atropos")) return@run false
            // "Atropos" in mythology context but not as the software engine
            val mythologyIndicators = listOf(
                "greek mythology", "the three fates", "the fates",
                "moirai", "cut the thread", "thread of life",
                "goddess of fate", "daughter of zeus", "daughter of the night",
                "she who cannot be turned"
            )
            mythologyIndicators.any { indicator ->
                // Find Atropos near the indicator
                val atroposIdx = lower.indexOf("atropos")
                val indicatorIdx = lower.indexOf(indicator)
                atroposIdx >= 0 && indicatorIdx >= 0 &&
                    kotlin.math.abs(atroposIdx - indicatorIdx) < 200
            }
        }

        // Corroboration required. A single incidental term (a provider naming
        // a library "athena", or discussing a "titan" instance size) must not
        // reject an otherwise valid engineering answer. Unambiguous phrases
        // stand alone; weak single tokens do not.
        val strongTerms = listOf(
            "greek mythology", "greek god", "greek goddess",
            "the three fates", "moirai", "mount olympus",
            "son of zeus", "daughter of zeus", "wife of zeus", "river styx"
        )
        val strongMythology = strongTerms.any { lower.contains(it) }
        val weakHits = mythologyTerms.count { lower.contains(it) }

        return atroposMythology || strongMythology || weakHits >= 2
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
    val attestation: ContextAttestation?,
    val mythologyDetected: Boolean
)
