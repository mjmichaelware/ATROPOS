package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.provider.CostMode
import atropos.core.provider.ProviderDescriptorRegistry

class StatusProviderDescriptorRenderer(
    private val registry: ProviderDescriptorRegistry,
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun render(currentProviderName: String? = null, expanded: Boolean = false): String =
        renderList(currentProviderName, expanded, 80).joinToString("\n")

    fun renderList(currentProviderName: String? = null, expanded: Boolean = false, width: Int): List<String> {
        val grouped = registry.getAll().groupBy { it.costMode }
        val body = buildList {
            currentProviderName?.let { add(surface.statusRow("active provider", it, Health.VERIFIED, width)) }
            add(surface.row("total", registry.getAll().size.toString(), width))
            add(surface.row("free eligible", registry.getFreeEligible().size.toString(), width))
            add(surface.row("paid locked", registry.getPaidLocked().size.toString(), width))
            add(surface.hint("legend: > active · q quota tier · caps capabilities · env required variables", width))

            grouped.toSortedMap(compareBy { it.ordinal }).forEach { (costMode, descriptors) ->
                add(surface.sectionHeading(costModeLabel(costMode).uppercase(), width))
                descriptors.sortedWith(compareBy({ it.id != currentProviderName }, { it.quotaTier }, { it.id })).forEach { descriptor ->
                    val isActive = descriptor.id == currentProviderName
                    val prefix = if (isActive) "> " else "  "
                    val label = "$prefix${descriptor.id}"
                    val details = "q=${descriptor.quotaTier} caps=${descriptor.capabilities.size} env=${descriptor.requiredEnv.size}"
                    add(surface.statusRow(label, details, if (isActive) Health.VERIFIED else Health.PENDING, width))
                    if (expanded) {
                        add(surface.hint("    ${descriptor.displayName} · endpoint=${descriptor.endpointId ?: "none"}", width))
                        if (descriptor.capabilities.isNotEmpty()) {
                            add(surface.hint("    capabilities: ${descriptor.capabilities.joinToString(", ") { it.name.lowercase() }}", width))
                        }
                        if (descriptor.requiredEnv.isNotEmpty()) {
                            add(surface.hint("    requirements: ${descriptor.requiredEnv.joinToString(", ")}", width))
                        }
                    }
                }
            }
            add("")
            add(surface.hint(if (expanded) "end of descriptor inventory" else "details: /providers descriptors --full", width))
        }
        return surface.block("PROVIDER DESCRIPTORS", body, width, Role.BRAND)
    }

    private fun costModeLabel(costMode: CostMode): String =
        costMode.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
}
