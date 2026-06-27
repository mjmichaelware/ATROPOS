package atropos.cli.ui

import atropos.core.provider.adapter.AdapterRouteFacade
import atropos.core.provider.adapter.AdapterStatus

class StatusAdapterRenderer(
    private val facade: AdapterRouteFacade = AdapterRouteFacade()
) {
    fun render(): String {
        val statuses = facade.adapterStatus()
        val out = mutableListOf<String>()
        out += "adapters: ${statuses.size}"
        out += "implemented: ${statuses.count { it.implemented }}"
        out += "configured: ${statuses.count { it.configured }}"
        out += "dry_run: ${statuses.count { it.dryRunOnly }}"
        out += "columns: provider implemented configured dry_run models health detail"
        statuses.forEach { out += line(it) }
        return out.joinToString("\n")
    }

    private fun line(status: AdapterStatus): String =
        "  ${status.providerId.padEnd(18)} implemented=${status.implemented} configured=${status.configured} dry_run=${status.dryRunOnly} models=${status.modelCount} health=${status.health} detail=${status.detail}"
}
