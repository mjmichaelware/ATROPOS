package atropos.cli.ui

import atropos.core.provider.adapter.AdapterRouteFacade
import atropos.core.provider.adapter.AdapterStatus

class StatusAdapterRenderer(
    private val facade: AdapterRouteFacade = AdapterRouteFacade()
) {
    fun render(): String {
        val statuses = facade.adapterStatus()
        val implemented = statuses.count { it.implemented }
        val configured = statuses.count { it.configured }
        val dryRun = statuses.count { it.dryRunOnly }
        return buildString {
            appendLine("adapters:")
            appendLine("  total: ${statuses.size}")
            appendLine("  implemented: $implemented")
            appendLine("  configured: $configured")
            appendLine("  dry_run: $dryRun")
            appendLine("  kernel: fixture-backed, live network opt-in")
            appendLine("columns: provider implemented configured dry_run models health detail")
            statuses.forEach { appendLine(line(it)) }
        }
    }

    private fun line(status: AdapterStatus): String =
        "  ${status.providerId.padEnd(18)} implemented=${status.implemented} configured=${status.configured} dry_run=${status.dryRunOnly} models=${status.modelCount} health=${status.health} detail=${status.detail}"
}
