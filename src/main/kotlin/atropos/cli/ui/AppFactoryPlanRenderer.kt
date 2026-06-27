package atropos.cli.ui

import atropos.core.factory.AppFactoryRouter

class AppFactoryPlanRenderer(
    private val router: AppFactoryRouter = AppFactoryRouter()
) {
    fun renderPlan(prompt: String): String = router.render(router.plan(prompt))
    fun renderRun(prompt: String): String = router.render(router.runLocal(prompt))
    fun renderStatus(): String {
        return buildString {
            appendLine("factory:")
            appendLine("  planner: local classifier ready")
            appendLine("  worker: free-first provider route")
            appendLine("  validator: local kotlinc first")
            appendLine("  repair: local stderr before LLM")
            appendLine("  assets: local text/ansi/svg primary; remote asset providers optional")
            appendLine("  memory: local memory root")
            appendLine("  ci: local queue, remote optional")
            appendLine("  final acceptance: ready")
        }
    }
}
