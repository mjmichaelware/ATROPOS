package atropos.cli.ui

import atropos.core.factory.AppFactoryRouter

class AppFactoryPlanRenderer(
    private val router: AppFactoryRouter = AppFactoryRouter()
) {
    fun renderPlan(prompt: String): String = router.render(router.plan(prompt))
    fun renderRun(prompt: String): String = router.render(router.runLocal(prompt))
    fun renderClarifiedRun(projectId: String, answers: List<Boolean>): String =
        router.render(router.runClarified(projectId, answers))

    /** Wave 1: resume a prior run from its durable lineage. */
    fun renderResume(runId: String): String = router.render(router.resumePlan(runId))
    fun renderStatusList(width: Int): List<String> = renderStatus().lines()
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
            appendLine("  final acceptance: source path ready; runtime verification pending")
        }
    }
}
