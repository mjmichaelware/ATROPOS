/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.QuotaFuelCellRenderer
import atropos.cli.ui.TerminalTheme
import atropos.cli.ui.AppFactoryPlanRenderer
import atropos.cli.ui.StatusAdapterRenderer
import atropos.cli.ui.StatusAssetsRenderer
import atropos.cli.ui.StatusCiRenderer
import atropos.cli.ui.StatusEndpointRenderer
import atropos.cli.ui.StatusMemoryRenderer
import atropos.cli.ui.StatusOpsRenderer
import atropos.cli.ui.StatusPaidEmergencyRenderer
import atropos.cli.ui.StatusQuotaRenderer
import atropos.cli.ui.StatusSecurityRenderer
import atropos.cli.ui.TestMatrixRenderer
import atropos.core.AtroposConfig
import atropos.core.provider.FileQuotaLedger
import atropos.core.provider.ProviderTruthService
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.ProviderQuotaPaths

class StatusCommandHandler(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val sessionTracker: QuotaSessionTracker,
    private val fuelCell: QuotaFuelCellRenderer =
        QuotaFuelCellRenderer(TerminalTheme(atropos.cli.config.ConfigurationManager())),
    /**
     * Where the session's token ceiling comes from, if the operator declared
     * one. Injected so a test does not depend on the ambient environment.
     */
    private val budgetSource: () -> String? = { System.getenv(TOKEN_BUDGET_ENV) }
) {

    /**
     * The session's spend against its declared ceiling.
     *
     * `limit` is zero when no ceiling was declared, and
     * [QuotaFuelCellRenderer] renders that as `[ no limit ]` rather than as a
     * full or an empty cell. That distinction is the reason this is worth
     * drawing at all: on a metered free tier, "you have used 40k tokens" and
     * "you have used 40k of your 50k" are different facts, and a gauge that
     * invented a denominator would state the second when it only knew the
     * first.
     */
    private fun sessionBudget(): QuotaFuelCellRenderer.QuotaState =
        QuotaFuelCellRenderer.QuotaState(
            used = sessionTracker.estimatedTokens.toDouble(),
            limit = budgetSource()?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
        )
    fun execute(tokens: List<String>, activeProviderName: String) {
        val quotaRegistry = StaticProviderDescriptorRegistry()
        val statusRenderer = StatusQuotaRenderer(
            registry = quotaRegistry,
            ledger = FileQuotaLedger(
                ProviderQuotaPaths.defaultLedger(),
                FileQuotaLedger.seedFromDescriptors(quotaRegistry)
            )
        )
        when (tokens.getOrNull(1)?.lowercase()) {
            "endpoints" -> uiEngine.renderNotice(
                StatusEndpointRenderer(ProviderTruthService(config).endpointRegistry()).render()
            )
            "quota" -> uiEngine.renderNotice(statusRenderer.renderQuota())
            "route" -> renderRoute(tokens, statusRenderer)
            "failures" -> uiEngine.renderNotice(statusRenderer.renderFailures())
            "adapters" -> uiEngine.renderBlock(StatusAdapterRenderer().renderList(uiEngine.viewportWidth))
            "memory" -> uiEngine.renderBlock(StatusMemoryRenderer().render(uiEngine.viewportWidth))
            "ci", "queue" -> uiEngine.renderBlock(StatusCiRenderer().render(uiEngine.viewportWidth))
            "assets" -> uiEngine.renderBlock(StatusAssetsRenderer().render(uiEngine.viewportWidth))
            "paid" -> uiEngine.renderBlock(StatusPaidEmergencyRenderer().render(uiEngine.viewportWidth))
            "factory" -> uiEngine.renderBlock(AppFactoryPlanRenderer().renderStatusList(uiEngine.viewportWidth))
            "security" -> uiEngine.renderNotice(StatusSecurityRenderer().render())
            "tests" -> uiEngine.renderNotice(TestMatrixRenderer().render())
            "ops" -> uiEngine.renderNotice(StatusOpsRenderer().render())
            null -> {
                uiEngine.renderStatusMatrix(config, activeProviderName)
                uiEngine.renderNotice(
                    "usage ${sessionTracker.promptCount} prompts | ~${sessionTracker.estimatedTokens} tokens " +
                        fuelCell.render(sessionBudget(), width = 24)
                )
                uiEngine.renderNotice(statusRenderer.renderDefaultStatusSummary())
            }
            else -> uiEngine.renderError("usage: /status [quota|route <task>|failures|adapters|assets|paid|factory|memory|ci|queue|security|tests|ops|evaluation|endpoints]")
        }
    }

    private fun renderRoute(tokens: List<String>, statusRenderer: StatusQuotaRenderer) {
        val task = tokens.drop(2).filterNot { it.equals("--full", ignoreCase = true) || it.equals("--expanded", ignoreCase = true) }.joinToString(" ").trim()
        if (task.isBlank()) {
            uiEngine.renderError("/status route requires a task")
        } else {
            val registry = StaticProviderDescriptorRegistry()
            val ledger = FileQuotaLedger(
                ProviderQuotaPaths.defaultLedger(),
                FileQuotaLedger.seedFromDescriptors(registry)
            )
            val facade = atropos.core.provider.adapter.AdapterRouteFacade(descriptorRegistry = registry, ledger = ledger)
            val result = facade.decide(task, dryRun = true)
            val routeRenderer = atropos.cli.ui.StatusRouteRenderer()
            uiEngine.renderBlock(routeRenderer.renderRoute(result, uiEngine.viewportWidth))
        }
    }


    private companion object {
        /** Operator-declared token ceiling for one session. Unset means unbounded. */
        const val TOKEN_BUDGET_ENV = "ATROPOS_TOKEN_BUDGET"
    }
}
