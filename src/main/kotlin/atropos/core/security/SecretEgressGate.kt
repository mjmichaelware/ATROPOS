/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.time.Instant

data class EgressViolation(
    val patternName: String,
    val matchedFragment: String,
    val timestamp: Instant = Instant.now()
)

object SecretEgressGate {
    private val blacklistedPatterns = mutableMapOf<String, String>()

    fun registerCanary(secret: String, patternName: String) {
        if (secret.trim().length >= 4) {
            blacklistedPatterns[secret.trim().lowercase()] = patternName
        }
    }

    // Retained for backward compatibility
    fun registerCanary(pattern: String) {
        registerCanary(pattern, "LegacyCanary")
    }

    fun scan(output: String): List<EgressViolation> {
        val lower = output.lowercase()
        val violations = mutableListOf<EgressViolation>()
        for ((pattern, name) in blacklistedPatterns) {
            if (lower.contains(pattern)) {
                // In a real implementation we would extract the exact match or context
                violations.add(EgressViolation(name, "Matched pattern $name"))
            }
        }
        return violations
    }

    // Retained for backward compatibility
    fun hasSecretLeak(output: String): Boolean {
        return scan(output).isNotEmpty()
    }

    fun clearCanaries() {
        blacklistedPatterns.clear()
    }
}
