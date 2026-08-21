package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.execution.LocalWorkQueue

class StatusCiRenderer(
    private val queue: LocalWorkQueue = LocalWorkQueue(),
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun render(): String = render(80).joinToString("\n")

    fun render(width: Int): List<String> {
        val status = queue.status()
        val body = listOf(
            surface.statusRow("local process queue", "ready", Health.VERIFIED, width),
            surface.row("queue root", status.root.name, width),
            surface.row(
                "jobs",
                "total=${status.total} queued=${status.queued} running=${status.running} succeeded=${status.succeeded} failed=${status.failed}",
                width
            ),
            surface.statusRow(
                "github actions",
                if (status.githubActionsConfigured) "configured" else "off",
                if (status.githubActionsConfigured) Health.VERIFIED else Health.UNKNOWN,
                width
            ),
            surface.statusRow(
                "cloudflare workers",
                if (status.cloudflareConfigured) "configured" else "off",
                if (status.cloudflareConfigured) Health.VERIFIED else Health.UNKNOWN,
                width
            ),
            surface.statusRow(
                "supabase edge",
                if (status.supabaseEdgeConfigured) "configured" else "off",
                if (status.supabaseEdgeConfigured) Health.VERIFIED else Health.UNKNOWN,
                width
            ),
            surface.statusRow(
                "google cloud",
                if (status.googleCloudConfigured) "configured" else "off",
                if (status.googleCloudConfigured) Health.VERIFIED else Health.UNKNOWN,
                width
            ),
            surface.hint("policy: local compile first · remote CI for reproducibility", width)
        )
        return surface.block("CI / EDGE EXECUTION", body, width, Role.BRAND)
    }
}
