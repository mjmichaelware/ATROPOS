package atropos.core.supremacy

enum class SecurityLabel(val rank: Int) {
    PUBLIC(0),
    INTERNAL(1),
    CONFIDENTIAL(2),
    SECRET(3);

    fun canFlowTo(target: SecurityLabel): Boolean = this.rank <= target.rank
}

data class LabeledFlow<T>(val payload: T, val label: SecurityLabel)

class InformationFlowViolationException(message: String) : SecurityException(message)

class SecretNonInterferenceGuard {
    private val keyPatterns = listOf(
        Regex("""(?i)(?:sk-[a-zA-Z0-9_-]{20,})"""),
        Regex("""(?i)(?:ghp_[a-zA-Z0-9]{36,})"""),
        Regex("""(?i)(?:xox[baprs]-[a-zA-Z0-9]{10,})""")
    )

    fun <T> checkFlow(source: SecurityLabel, destination: SecurityLabel, payload: T): LabeledFlow<T> {
        if (!source.canFlowTo(destination)) {
            throw InformationFlowViolationException(
                "Illegal Information Flow: Source label [$source] cannot flow to sink [$destination]"
            )
        }
        return LabeledFlow(payload, source)
    }

    fun assertSinkSafe(payloadLabel: SecurityLabel, sinkAcceptsMax: SecurityLabel) {
        if (!payloadLabel.canFlowTo(sinkAcceptsMax)) {
            throw InformationFlowViolationException(
                "Information Leakage Blocked: Payload label [$payloadLabel] exceeds sink capacity [$sinkAcceptsMax]"
            )
        }
    }

    fun sanitizeToLabel(rawString: String, currentLabel: SecurityLabel): LabeledFlow<String> {
        var sanitized = rawString
        var elevatedLabel = currentLabel

        for (pattern in keyPatterns) {
            if (pattern.containsMatchIn(sanitized)) {
                elevatedLabel = SecurityLabel.SECRET
                sanitized = pattern.replace(sanitized, "[REDACTED_SECRET]")
            }
        }

        return LabeledFlow(sanitized, elevatedLabel)
    }
}
