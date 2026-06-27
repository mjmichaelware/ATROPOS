package atropos.cli.ui

import atropos.core.provider.adapter.AdapterKernelFixtures
import atropos.core.provider.adapter.AdapterRouteFacade
import atropos.core.provider.adapter.AdapterStatus
import atropos.core.provider.adapter.NonOpenAiFreeProviderCatalog
import atropos.core.provider.adapter.NonOpenAiKernelFixtures
import atropos.core.provider.adapter.OpenAiCompatibleProviderCatalog

class StatusAdapterRenderer(
    private val facade: AdapterRouteFacade = AdapterRouteFacade()
) {
    fun render(): String {
        val statuses = facade.adapterStatus()
        val implemented = statuses.count { it.implemented }
        val configured = statuses.count { it.configured }
        val dryRunOnly = statuses.count { it.dryRunOnly }
        val openAiIds = OpenAiCompatibleProviderCatalog.all().map { it.providerId }.toSet()
        val nonOpenAiIds = NonOpenAiFreeProviderCatalog.all().map { it.providerId }.toSet()
        val openAiReady = statuses.filter { it.providerId in openAiIds && it.implemented }
        val nonOpenAiReady = statuses.filter { it.providerId in nonOpenAiIds && it.implemented }
        val fixtureFailures =
            AdapterKernelFixtures.runOpenAiCompatibleFamily().filterNot { it.passed } +
                NonOpenAiKernelFixtures.runNonOpenAiFreeFamily().filterNot { it.passed }

        return buildString {
            appendLine("adapters:")
            appendLine("  total: ${statuses.size}")
            appendLine("  implemented: $implemented")
            appendLine("  configured: $configured")
            appendLine("  dry_run_only: $dryRunOnly")
            appendLine("  openai_compatible: ${openAiReady.size}/${openAiIds.size}")
            appendLine("  non_openai_free: ${nonOpenAiReady.size}/${nonOpenAiIds.size}")
            appendLine("  fixture_failures: ${fixtureFailures.size}")
            appendLine("  kernel: fixture-backed transports, live tests opt-in")
            appendLine("columns: provider implemented configured dry_run_only models health detail")
            statuses.forEach { appendLine(line(it)) }
        }
    }

    private fun line(status: AdapterStatus): String =
        "  ${status.providerId.padEnd(18)} implemented=${status.implemented} configured=${status.configured} dry_run_only=${status.dryRunOnly} models=${status.modelCount} health=${status.health} detail=${status.detail}"
}
