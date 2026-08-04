/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
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

class StatusCommandHandler(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val sessionTracker: QuotaSessionTracker
) {
    fun execute(tokens: List<String>, activeProviderName: String) {
        val quotaRegistry = StaticProviderDescriptorRegistry()
        val statusRenderer = StatusQuotaRenderer(
            registry = quotaRegistry,
            ledger = FileQuotaLedger(
                java.io.File(".atropos/provider/quota-ledger.tsv"),
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
            "adapters" -> uiEngine.renderNotice(StatusAdapterRenderer().render())
            "memory" -> uiEngine.renderNotice(StatusMemoryRenderer().render())
            "ci", "queue" -> uiEngine.renderNotice(StatusCiRenderer().render())
            "assets" -> uiEngine.renderNotice(StatusAssetsRenderer().render())
            "paid" -> uiEngine.renderNotice(StatusPaidEmergencyRenderer().render())
            "factory" -> uiEngine.renderNotice(AppFactoryPlanRenderer().renderStatus())
            "security" -> uiEngine.renderNotice(StatusSecurityRenderer().render())
            "tests" -> uiEngine.renderNotice(TestMatrixRenderer().render())
            "ops" -> uiEngine.renderNotice(StatusOpsRenderer().render())
            null -> {
                uiEngine.renderStatusMatrix(config, activeProviderName)
                uiEngine.renderNotice("usage ${sessionTracker.promptCount} prompts | ~${sessionTracker.estimatedTokens} tokens")
                uiEngine.renderNotice(statusRenderer.renderDefaultStatusSummary())
            }
            else -> uiEngine.renderError("usage: /status [quota|route <task>|failures|adapters|assets|paid|factory|memory|ci|queue|security|tests|ops|endpoints]")
        }
    }

    private fun renderRoute(tokens: List<String>, statusRenderer: StatusQuotaRenderer) {
        val expanded = tokens.any { it.equals("--full", ignoreCase = true) || it.equals("--expanded", ignoreCase = true) }
        val task = tokens.drop(2).filterNot { it.equals("--full", ignoreCase = true) || it.equals("--expanded", ignoreCase = true) }.joinToString(" ").trim()
        if (task.isBlank()) uiEngine.renderError("/status route requires a task")
        else uiEngine.renderNotice(statusRenderer.renderRoute(task, expanded))
    }

}
