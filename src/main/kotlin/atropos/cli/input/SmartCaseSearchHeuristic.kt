/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * ATOM W9: Smart Case-Sensitive Search Heuristic.
 *
 * Automatically executes case-insensitive matching when the search query is entirely lowercase,
 * switching strictly to case-sensitive matching the moment any uppercase character is typed.
 *
 * ## Research: Boyer-Moore-Horspool Dynamic Skip Tables
 * Researches pre-computing case-folded vs exact character skip tables based on smart-case
 * queries to optimize string scanning throughput across long buffer streams.
 */
object SmartCaseSearchHeuristic {

    fun shouldMatchCaseSensitively(query: String): Boolean {
        return query.any { it.isUpperCase() }
    }

    fun matches(target: String, query: String, smartCaseEnabled: Boolean = true): Boolean {
        if (query.isEmpty()) return true
        if (!smartCaseEnabled) {
            return target.contains(query, ignoreCase = true)
        }

        val caseSensitive = shouldMatchCaseSensitively(query)
        return target.contains(query, ignoreCase = !caseSensitive)
    }
}
