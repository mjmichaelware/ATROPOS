package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.security.DefaultSecretSource
import atropos.core.security.KeySetupHelper
import atropos.core.security.RedactionFilter

class StatusSecurityRenderer(
    private val filter: RedactionFilter = RedactionFilter(),
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun render(): String = render(80).joinToString("\n")

    fun render(width: Int): List<String> {
        val source = DefaultSecretSource.create()
        val status = source.status(listOf("GROQ_API_KEY", "OPENROUTER_API_KEY", "GEMINI_API_KEY", "GITHUB_MODELS_TOKEN", "GOOGLE_APPLICATION_CREDENTIALS"))
        val body = listOf(
            surface.statusRow("redaction", "ready", Health.VERIFIED, width),
            surface.row("precedence", "explicit > environment > local_file", width),
            surface.row("requested", status.requested.toString(), width),
            surface.statusRow(
                "configured",
                status.configured.toString(),
                if (status.configured > 0) Health.VERIFIED else Health.PENDING,
                width
            ),
            surface.statusRow(
                "missing",
                status.missing.size.toString(),
                if (status.missing.isEmpty()) Health.VERIFIED else Health.PENDING,
                width
            ),
            surface.row("display", "redacted only", width),
            surface.hint("prompt policy: credentials are never sent to providers in raw form", width)
        )
        return surface.block("SECURITY", body, width, Role.BRAND)
    }

    fun renderRedaction(value: String): String = renderRedaction(value, 80).joinToString("\n")

    fun renderRedaction(value: String, width: Int): List<String> {
        val report = filter.report(value)
        val body = listOf(
            surface.statusRow("changed", report.changed.toString(), if (report.changed) Health.PENDING else Health.VERIFIED, width),
            surface.row("findings", report.summary(), width),
            surface.row("fingerprint", filter.stableFingerprint(value), width),
            surface.row("text", report.redacted, width)
        )
        return surface.block("REDACTION REPORT", body, width, Role.BRAND)
    }

    fun renderKeysSetup(): String = renderKeysSetup(80).joinToString("\n")

    fun renderKeysSetup(width: Int): List<String> {
        val result = KeySetupHelper().setup()
        val body = listOf(
            surface.row("root", result.root.name, width),
            surface.row("template", result.template.name, width),
            surface.row("readme", result.readme.name, width),
            surface.row("names", result.names.size.toString(), width),
            surface.hint("raw values: never written by setup", width)
        )
        return surface.block("KEYS SETUP", body, width, Role.BRAND)
    }

    fun renderKeysStatus(): String = renderKeysStatus(80).joinToString("\n")

    fun renderKeysStatus(width: Int): List<String> {
        val status = DefaultSecretSource.create().status(listOf("GROQ_API_KEY", "OPENROUTER_API_KEY", "GEMINI_API_KEY", "GITHUB_MODELS_TOKEN"))
        val body = status.redactedLines.map { line ->
            val parts = line.split("=", limit = 2)
            val key = parts.getOrNull(0)?.trim().orEmpty()
            val value = parts.getOrNull(1)?.trim().orEmpty()
            val health = if (value.contains("missing")) Health.ERROR else Health.VERIFIED
            surface.statusRow(key, value, health, width)
        } + listOf(surface.hint("raw values: never printed in plaintext", width))
        return surface.block("KEYS STATUS", body, width, Role.BRAND)
    }

    fun renderKeysDoctor(service: atropos.core.security.KeyDoctorService, width: Int): List<String> {
        val entries = service.entries()
        val configured = entries.count { it.configured }
        val body = listOf(
            surface.row("precedence", "explicit > environment > local_file", width),
            surface.row("requested", entries.size.toString(), width),
            surface.row("configured", configured.toString(), width),
            surface.row("missing", (entries.size - configured).toString(), width)
        ) + entries.map { entry ->
            val health = if (entry.configured) Health.VERIFIED else Health.ERROR
            val detail = "src=${entry.source} providers=${entry.impactedProviders.joinToString(",").ifBlank { "none" }}"
            surface.statusRow(entry.name, detail, health, width)
        } + listOf(surface.hint("remediation: configure missing names through env or .atropos/secrets/*.secret", width))
        return surface.block("KEYS DOCTOR", body, width, Role.BRAND)
    }
}
