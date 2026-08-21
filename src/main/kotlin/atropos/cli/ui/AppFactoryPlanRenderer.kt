package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.factory.AppFactoryRouter

class AppFactoryPlanRenderer(
    private val router: AppFactoryRouter = AppFactoryRouter(),
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun renderPlan(prompt: String): String = router.render(router.plan(prompt))
    fun renderRun(prompt: String): String = router.render(router.runLocal(prompt))
    fun renderClarifiedRun(projectId: String, answers: List<Boolean>): String =
        router.render(router.runClarified(projectId, answers))

    fun renderStatus(): String = renderStatusList(80).joinToString("\n")

    fun renderStatusList(width: Int): List<String> {
        val body = listOf(
            surface.statusRow("planner", "local classifier ready", Health.VERIFIED, width),
            surface.row("worker", "free-first provider route", width),
            surface.row("validator", "local kotlinc first", width),
            surface.row("repair", "local stderr before LLM", width),
            surface.row("assets", "local text/ansi/svg primary", width),
            surface.row("memory", "local memory root", width),
            surface.row("ci", "local queue", width),
            surface.statusRow("acceptance", "source path ready; runtime verification pending", Health.PENDING, width)
        )
        return surface.block("APP FACTORY ENGINE", body, width, Role.BRAND)
    }
}
