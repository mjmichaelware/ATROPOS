package atropos.cli.ui

import atropos.core.security.DefaultSecretSource
import atropos.core.security.KeySetupHelper
import atropos.core.security.RedactionFilter

class StatusSecurityRenderer(private val filter: RedactionFilter = RedactionFilter()) {
    fun render(): String {
        val source = DefaultSecretSource.create()
        val status = source.status(listOf("GROQ_API_KEY", "OPENROUTER_API_KEY", "GEMINI_API_KEY", "GITHUB_MODELS_TOKEN", "GOOGLE_APPLICATION_CREDENTIALS"))
        return buildString {
            appendLine("security:")
            appendLine("  redaction: ready")
            appendLine("  secret precedence: explicit > environment > local_file")
            appendLine("  requested secrets: ${status.requested}")
            appendLine("  configured secrets: ${status.configured}")
            appendLine("  missing secrets: ${status.missing.size}")
            appendLine("  display: redacted only")
            appendLine("  prompt policy: never include raw credentials")
        }
    }

    fun renderRedaction(value: String): String {
        val report = filter.report(value)
        return buildString {
            appendLine("redaction report:")
            appendLine("  changed: ${report.changed}")
            appendLine("  findings: ${report.summary()}")
            appendLine("  fingerprint: ${filter.stableFingerprint(value)}")
            appendLine("  text: ${report.redacted}")
        }
    }

    fun renderKeysSetup(): String {
        val result = KeySetupHelper().setup()
        return buildString {
            appendLine("keys setup:")
            appendLine("  root: ${result.root.path}")
            appendLine("  template: ${result.template.path}")
            appendLine("  readme: ${result.readme.path}")
            appendLine("  names: ${result.names.size}")
            appendLine("  raw values: never written by setup")
        }
    }

    fun renderKeysStatus(): String {
        val status = DefaultSecretSource.create().status(listOf("GROQ_API_KEY", "OPENROUTER_API_KEY", "GEMINI_API_KEY", "GITHUB_MODELS_TOKEN"))
        return buildString {
            appendLine("keys:")
            status.redactedLines.forEach { appendLine("  $it") }
            appendLine("  raw values: never printed")
        }
    }
}
