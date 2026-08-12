package atropos.core.security

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

enum class SourceSecretClassification {
    REAL_SECRET,
    PLACEHOLDER_TEST_VALUE,
    UI_LABEL,
    DETECTOR_DEFINITION,
    DOCUMENTATION_EXAMPLE,
    STALE_NONCANONICAL_PATH,
    UNKNOWN
}

data class SourceSecretFinding(
    val path: String,
    val line: Int,
    val ruleId: String,
    val redactedSpan: String,
    val classification: SourceSecretClassification
)

/** Scans current candidate bytes, not Git history or deleted path names. */
class SourceSecretScanner(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun scan(root: Path, candidatePaths: Iterable<String>): List<SourceSecretFinding> {
        val normalizedRoot = root.toAbsolutePath().normalize()
        return candidatePaths
            .map { it.trim().removePrefix("./") }
            .filter { it.isNotBlank() }
            .distinct()
            .flatMap { relative ->
                val file = normalizedRoot.resolve(relative).normalize()
                if (!file.startsWith(normalizedRoot) || !Files.isRegularFile(file)) {
                    emptyList()
                } else {
                    scanFile(normalizedRoot, file)
                }
            }
    }

    private fun scanFile(root: Path, file: Path): List<SourceSecretFinding> {
        val bytes = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return emptyList()
        if (bytes.size > MAX_FILE_BYTES || bytes.any { it == 0.toByte() }) return emptyList()
        val relative = root.relativize(file).toString().replace(file.fileSystem.separator, "/")
        val findings = mutableListOf<SourceSecretFinding>()
        val text = String(bytes, StandardCharsets.UTF_8)
        PRIVATE_KEY.findAll(text).forEach { match ->
            findings += finding(
                relative,
                text.substring(0, match.range.first).count { it == '\n' } + 1,
                "private-key",
                match.value,
                SourceSecretClassification.REAL_SECRET
            )
        }
        text.lineSequence().forEachIndexed { index, line ->
            findings += find(line, relative, index + 1)
        }
        return findings
    }

    private fun find(line: String, path: String, lineNumber: Int): List<SourceSecretFinding> {
        val findings = mutableListOf<SourceSecretFinding>()
        BEARER.findAll(line).forEach {
            findings += finding(path, lineNumber, "bearer-token", it.value, classify(path, line, it.value))
        }
        JWT.findAll(line).forEach {
            findings += finding(path, lineNumber, "jwt", it.value, classify(path, line, it.value))
        }
        API_KEY.findAll(line).forEach { match ->
            val value = match.groupValues[2]
            if (!isDynamicReference(value)) {
                findings += finding(path, lineNumber, "credential-assignment", match.value, classify(path, line, value))
            }
        }
        OPENAI_KEY.findAll(line).forEach {
            findings += finding(path, lineNumber, "api-key", it.value, classify(path, line, it.value))
        }
        return findings
    }

    private fun classify(path: String, line: String, value: String): SourceSecretClassification {
        val lowerPath = path.lowercase()
        val normalized = value.trim().trim('"', '\'').lowercase()
        if (normalized in TEST_PLACEHOLDERS && isTestPath(lowerPath)) {
            return SourceSecretClassification.PLACEHOLDER_TEST_VALUE
        }
        if (isDetectorDefinition(path, line)) return SourceSecretClassification.DETECTOR_DEFINITION
        if (isDocumentationPath(lowerPath) && normalized in DOCUMENTATION_VALUES) {
            return SourceSecretClassification.DOCUMENTATION_EXAMPLE
        }
        return SourceSecretClassification.REAL_SECRET
    }

    private fun finding(
        path: String,
        line: Int,
        rule: String,
        span: String,
        classification: SourceSecretClassification
    ) = SourceSecretFinding(path, line, rule, redactionFilter.compact(span, 120), classification)

    private companion object {
        const val MAX_FILE_BYTES = 2 * 1024 * 1024
        val PRIVATE_KEY = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")
        val BEARER = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._-]{12,}")
        val JWT = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")
        val OPENAI_KEY = Regex("\\bsk-[A-Za-z0-9_-]{8,}")
        val API_KEY = Regex("(?i)\\b(api[_-]?key|token|secret|password|service[_-]?role)\\b\\s*[:=]\\s*[\\\"']?([A-Za-z0-9_./+=-]{8,})")
        val TEST_PLACEHOLDERS = setOf(
            "test", "test-token", "test-token-value", "placeholder", "placeholder-token",
            "example", "example-token", "dummy", "dummy-token", "fake", "redacted", "changeme"
        )
        val DOCUMENTATION_VALUES = setOf("example", "placeholder", "redacted", "your-token-here", "your-api-key")

        fun isTestPath(path: String): Boolean =
            path.contains("/test/") || path.contains("/tests/") ||
                path.contains(".test.") || path.contains(".spec.") || path.endsWith("/fixtures")

        fun isDocumentationPath(path: String): Boolean =
            path.startsWith("docs/") || path.endsWith("readme.md") || path.contains("/docs/")

        fun isDynamicReference(value: String): Boolean {
            val normalized = value.lowercase()
            return normalized.startsWith("system.getenv") || normalized.startsWith("process.env") ||
                normalized.startsWith("os.environ") || normalized.startsWith("env(") ||
                normalized.startsWith("${'$'}{") || normalized.startsWith("${'$'}")
        }

        fun isDetectorDefinition(path: String, line: String): Boolean {
            val lower = path.lowercase()
            return (lower.endsWith("redactionfilter.kt") || lower.contains("secretscanner") ||
            lower.endsWith("knownsecretregistry.kt")) &&
                (line.contains("Regex(") || line.contains("Pattern.compile"))
        }
    }
}
