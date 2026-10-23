/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.agent.Utf8BoundedBuilder
import atropos.core.security.RedactionFilter
import atropos.core.security.SecretEgressGate
import java.util.regex.Pattern

/**
 * SD7-T1-TEL03: Telemetry Secret Sanitization Gate.
 *
 * Wraps SecretEgressGate and RedactionFilter to sanitize all outgoing telemetry records.
 * Strips API keys, auth tokens, user home paths, git author emails, and IP addresses
 * before spooling or network egress.
 */
class TelemetrySanitizationGate(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val emailPattern = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
    private val ipPattern = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val homePrefixPattern = Pattern.compile("(/root|/home/[a-zA-Z0-9_-]+|/data/data/[a-zA-Z0-9_.-]+/files/home)")
    private val tokenPattern = Pattern.compile("(?:ghp|gho|ghu|ghs|ghr|hf|sk|xox[baprs]|key)-[a-zA-Z0-9_-]{16,}")

    /**
     * Sanitizes raw text string, enforcing canonical placeholder replacement.
     */
    fun sanitize(rawText: String, maxBytes: Int = 1024 * 1024): String {
        if (rawText.isBlank()) return ""

        // 1. RedactionFilter standard passes
        var clean = redactionFilter.redact(rawText)

        // 2. SecretEgressGate check
        if (SecretEgressGate.hasSecretLeak(clean)) {
            clean = redactionFilter.redact(clean)
        }

        // 3. User home prefixes
        clean = homePrefixPattern.matcher(clean).replaceAll("[REDACTED_HOME]")

        // 4. Git emails & Personal emails
        clean = emailPattern.matcher(clean).replaceAll("[REDACTED_EMAIL]")

        // 5. IP addresses
        clean = ipPattern.matcher(clean).replaceAll("[REDACTED_IP]")

        // 6. Generic high-entropy tokens
        clean = tokenPattern.matcher(clean).replaceAll("[REDACTED_TOKEN]")

        // 7. Bounded buffer chunking via Utf8BoundedBuilder
        val sb = StringBuilder()
        val builder = Utf8BoundedBuilder(maxBytes)
        builder.append(sb, clean, false)
        return sb.toString()
    }

    /**
     * Sanitizes an entire telemetry record in place.
     */
    fun sanitizeRecord(record: FrontierSweTelemetryRecord): FrontierSweTelemetryRecord {
        val cleanPayload = sanitize(record.payloadJson)
        val cleanInstance = sanitize(record.instanceId)
        val cleanH0 = sanitize(record.h0PromptLineage)

        return record.copy(
            instanceId = cleanInstance,
            h0PromptLineage = cleanH0,
            payloadJson = cleanPayload,
            payloadHash = FrontierSweTelemetryRecord.sha256Hex(cleanPayload)
        )
    }
}
