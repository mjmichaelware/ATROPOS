/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

/**
 * Sealed result type for DLOI exact-address lookups.
 *
 * When resolution succeeds the caller receives [Resolved] containing the
 * full [DloiResolution].  When resolution fails the caller receives [NoMatch]
 * with a human-readable reason – never a guessed, nearest-neighbour,
 * cosine-similarity-fallback, or fabricated answer.
 */
sealed interface DloiLookupResult {
    data class Resolved(val resolution: DloiResolution) : DloiLookupResult
    data class NoMatch(val query: String, val reason: String) : DloiLookupResult
}

/**
 * HIGZeroGuard – the Hallucination In Guard = 0 contract.
 *
 * This guard wraps [DloiService] and guarantees that every exact-address
 * resolution either succeeds with the authoritative source excerpt or fails
 * with a typed [DloiLookupResult.NoMatch].  No blind cosine/RAG semantic
 * fallback, no nearest-neighbour guess, no fabricated source content is ever
 * substituted for a failed exact lookup.
 *
 * Usage:
 * ```
 * val guard = HigZeroGuard(service)
 * when (val result = guard.resolve("authority#S0008@L1-10")) {
 *     is DloiLookupResult.Resolved -> render(result.resolution)
 *     is DloiLookupResult.NoMatch  -> report("unresolved: ${result.reason}")
 * }
 * ```
 */
class HigZeroGuard(private val service: DloiService) {

    /**
     * Resolve an exact DLOI address.
     *
     * Delegates to [DloiService.lookup] and converts any resolution failure
     * into a typed [DloiLookupResult.NoMatch].  The guard never falls through
     * to semantic cosine search, nearest-neighbour embedding retrieval, or any
     * other guessed-content mechanism.
     */
    fun resolve(address: String): DloiLookupResult {
        val query = address.trim()
        if (query.isBlank()) return DloiLookupResult.NoMatch(address, "DLOI address is blank")
        return runCatching { service.lookup(query).validated() }
            .map { resolution -> DloiLookupResult.Resolved(resolution) }
            .getOrElse { failure ->
                DloiLookupResult.NoMatch(
                    query = query,
                    reason = failure.message ?: failure.javaClass.simpleName
                )
            }
    }

    /**
     * Resolve a human-readable task description to its authoritative source
     * section.
     *
     * Delegates to [DloiService.resolveTask] and converts any resolution
     * failure (including the fuzzy-title-mismatch case) into a typed
     * [DloiLookupResult.NoMatch].  No guessed section, no nearest-title
     * fuzzy fallback is ever returned.
     */
    fun resolveTask(task: String): DloiLookupResult {
        val query = task.trim()
        if (query.isBlank()) return DloiLookupResult.NoMatch(task, "DLOI task is blank")
        return runCatching { service.resolveTask(query).validated() }
            .map { resolution -> DloiLookupResult.Resolved(resolution) }
            .getOrElse { failure ->
                DloiLookupResult.NoMatch(
                    query = query,
                    reason = failure.message ?: failure.javaClass.simpleName
                )
            }
    }

    private fun DloiResolution.validated(): DloiResolution {
        require(coordinate.lineStart > 0 && coordinate.lineEnd >= coordinate.lineStart) {
            "DLOI resolution has invalid line coordinates"
        }
        require(excerpt.isNotBlank()) { "DLOI resolution returned an empty excerpt" }
        require(provenance.isNotBlank()) { "DLOI resolution has no provenance" }
        return this
    }
}
