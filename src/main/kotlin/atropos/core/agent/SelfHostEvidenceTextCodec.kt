package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Owns redacted text encoding for self-host evidence, separate from bundle storage. */
class SelfHostEvidenceTextCodec(
    private val redactionFilter: RedactionFilter
) {
    fun clean(value: String): String = redactionFilter.redact(value)

    fun sha256Text(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun escapeMarkdown(value: String): String = value.replace("`", "'")

    fun json(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    fun evidenceClass(evidence: List<String>, vararg markers: String): List<String> =
        evidence.filter { entry -> markers.any { marker -> entry.contains(marker, ignoreCase = true) } }

    fun appendEvidenceClassMarkdown(output: StringBuilder, title: String, entries: List<String>) {
        output.appendLine()
        output.appendLine("### $title")
        if (entries.isEmpty()) {
            output.appendLine("- none")
        } else {
            entries.forEach { entry ->
                val safe = clean(entry)
                output.appendLine("- `${escapeMarkdown(safe)}` sha256 `${sha256Text(safe)}`")
            }
        }
    }

    fun renderEvidenceClassJson(entries: List<String>): String =
        entries.joinToString(",") { entry ->
            val safe = clean(entry)
            "{\"text\": ${json(safe)}, \"sha256\": ${json(sha256Text(safe))}}"
        }
}
