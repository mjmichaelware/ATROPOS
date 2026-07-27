package atropos.core.security

import java.io.File
import java.security.MessageDigest

private val API_KEY_PATTERN = Regex("(?i)(api[_-]?key|token|secret|password)[\"']?\\s*[:=]\\s*[\"']?[^\"'\\s,}]+")
private val BEARER_PATTERN = Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]{12,}")
private val OPENAI_STYLE_PATTERN = Regex("\\b" + "s" + "k-" + "[A-Za-z0-9_\\-]{12,}")
private val PRIVATE_KEY_BLOCK_PATTERN = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")
private val SIGNED_URL_PATTERN = Regex("(?i)(https?://[^\\s]+)(X-Amz-Signature|Signature|sig|token|access_token)=([^\\s&]+)")
private val LOCAL_CREDENTIAL_PATH_PATTERN = Regex("(?i)(/[^\\s:]*?(client_secret|credentials|token)[^\\s:]*)")

data class RedactionFinding(val kind: String, val count: Int)

data class RedactionReport(
    val originalLength: Int,
    val redactedLength: Int,
    val changed: Boolean,
    val findings: List<RedactionFinding>,
    val redacted: String
) {
    fun summary(): String = findings.joinToString(", ") { "${it.kind}=${it.count}" }.ifBlank { "none" }
}

class RedactionFilter {
    fun redact(value: String): String = report(value).redacted

    fun report(value: String): RedactionReport {
        var text = value
        val findings = mutableListOf<RedactionFinding>()

        fun apply(kind: String, regex: Regex, replacement: (MatchResult) -> String) {
            val matches = regex.findAll(text).toList()
            if (matches.isEmpty()) return
            findings += RedactionFinding(kind, matches.size)
            text = regex.replace(text, replacement)
        }

        apply("private_key", PRIVATE_KEY_BLOCK_PATTERN) { "<redacted:private_key>" }
        apply("bearer", BEARER_PATTERN) { "Bearer <redacted:bearer>" }
        apply("api_key", OPENAI_STYLE_PATTERN) { "<redacted:api_key>" }
        apply("credential_assignment", API_KEY_PATTERN) { match ->
            val name = match.groupValues.getOrNull(1)?.ifBlank { "secret" } ?: "secret"
            "$name=<redacted:secret>"
        }
        apply("signed_url", SIGNED_URL_PATTERN) { match ->
            val prefix = match.groupValues.getOrNull(1) ?: "url"
            "${prefix}<redacted:signed_url>"
        }
        apply("credential_path", LOCAL_CREDENTIAL_PATH_PATTERN) { "<redacted:credential_path>" }

        return RedactionReport(value.length, text.length, text != value, findings, text.take(4000))
    }

    fun stableFingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(redact(value).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}

data class CredentialFileFinding(val path: String, val reason: String)

class CredentialDiffGuard(private val redactionFilter: RedactionFilter = RedactionFilter()) {
    private val riskyNames = Regex("(?i)(client_secret|credentials|token|private[_-]?key|service-account|service_account).*(\\.json|\\.pem|\\.key)?$")

    fun inspectPaths(paths: List<String>): List<CredentialFileFinding> = paths.mapNotNull { path ->
        val normalized = path.trim()
        when {
            normalized.isBlank() -> null
            riskyNames.containsMatchIn(File(normalized).name) -> CredentialFileFinding(normalized, "credential filename")
            normalized.contains(".env") -> CredentialFileFinding(normalized, "environment file")
            else -> null
        }
    }

    fun inspectText(label: String, text: String): RedactionReport = redactionFilter.report(text)
}
