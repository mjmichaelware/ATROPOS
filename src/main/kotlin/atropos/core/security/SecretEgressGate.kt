/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.time.Instant

data class EgressViolation(
    val patternName: String,
    val matchedFragment: String,
    val sink: SecretSinkKind = SecretSinkKind.MODEL_OUTPUT,
    val timestamp: Instant = Instant.now()
)

/** Named canary registration; the secret value is supplied only at enrollment. */
data class Canary(val label: String)

object SecretEgressGate {
    private val blacklistedPatterns = mutableMapOf<String, String>()
    private val leakageAccumulator = LeakageAccumulator()

    fun registerCanary(secret: String, patternName: String) {
        val trimmed = secret.trim()
        if (trimmed.length < SecretEncodingClosure.MINIMUM_ENROLLABLE_LENGTH) return
        SecretEncodingClosure.variantsOf(trimmed).forEach { variant ->
            blacklistedPatterns[variant.lowercase()] = patternName
        }
    }

    // Retained for backward compatibility
    fun registerCanary(pattern: String) {
        registerCanary(pattern, "LegacyCanary")
    }

    fun registerCanary(canary: Canary, secret: String) {
        registerCanary(secret, canary.label)
    }

    fun scan(output: String, sink: SecretSinkKind = SecretSinkKind.MODEL_OUTPUT): List<EgressViolation> {
        val lower = output.lowercase()
        val normalized = SecretEncodingClosure.whitespaceStripped(lower)
        val sinkPermitted = SecretSinkMatrix.isEgressPermitted(sink)
        val violations = mutableListOf<EgressViolation>()
        for ((pattern, name) in blacklistedPatterns) {
            if (lower.contains(pattern) || normalized.contains(pattern)) {
                val policyName = if (sinkPermitted) name else "blocked-sink:$name"
                violations.add(EgressViolation(policyName, "Matched pattern $name", sink = sink))
                break
            }
        }
        return violations
    }

    // Retained for backward compatibility
    fun hasSecretLeak(output: String): Boolean {
        return scan(output).isNotEmpty()
    }

    fun scanTurn(conversationId: String, output: String, sink: SecretSinkKind = SecretSinkKind.MODEL_OUTPUT): List<EgressViolation> =
        leakageAccumulator.scan(conversationId, output, sink)

    fun forgetConversation(conversationId: String) = leakageAccumulator.forget(conversationId)
    fun clearConversationState() = leakageAccumulator.clear()
    fun conversationTurnCount(conversationId: String): Int = leakageAccumulator.turnCount(conversationId)

    fun clearCanaries() {
        blacklistedPatterns.clear()
        leakageAccumulator.clear()
    }
}
