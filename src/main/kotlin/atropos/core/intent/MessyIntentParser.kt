// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.intent

import kotlin.math.min

class MessyIntentParser(private val validIntents: Set<String>) {

    fun parse(input: String): String? {
        val normalized = input.trim().lowercase()
        
        // Exact match
        if (validIntents.contains(normalized)) {
            return normalized
        }
        
        // Prefix match if input is at least 3 chars (e.g. "statu" -> "status")
        if (normalized.length >= 3) {
            val prefixMatches = validIntents.filter { it.startsWith(normalized) }
            if (prefixMatches.size == 1) {
                return prefixMatches.first()
            }
        }
        
        // Levenshtein distance match
        var bestMatch: String? = null
        var bestDistance = Int.MAX_VALUE
        
        for (intent in validIntents) {
            val dist = levenshtein(normalized, intent)
            val maxAllowedDistance = when {
                intent.length <= 3 -> 0
                intent.length <= 5 -> 1
                else -> 2
            }
            
            if (dist <= maxAllowedDistance && dist < bestDistance) {
                bestDistance = dist
                bestMatch = intent
            }
        }
        
        return bestMatch
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) {
            for (j in 0..s2.length) {
                if (i == 0) {
                    dp[i][j] = j
                } else if (j == 0) {
                    dp[i][j] = i
                } else {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    dp[i][j] = min(
                        min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    )
                }
            }
        }
        return dp[s1.length][s2.length]
    }
}
