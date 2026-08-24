package atropos.cli

import atropos.core.AtroposConfig
import atropos.core.integration.McpHostManager
import atropos.core.provider.ProviderOnboardingService

/** Read-only aggregate doctor composed from the canonical provider/MCP owners. */
class BackendDoctor(
    private val config: AtroposConfig,
    private val providers: ProviderOnboardingService = ProviderOnboardingService(),
    private val mcp: McpHostManager = McpHostManager(
        atropos.core.AtroposRepoRootLocator.resolve(),
        localOnly = config.runtime.localOnly
    )
) {
    fun render(): List<String> = buildList {
        add("ATROPOS DOCTOR")
        add("local_only=${config.runtime.localOnly}")
        add("zero_retention_research=${config.runtime.zeroRetentionResearch}")
        add("health=process-ready")
        add("providers:")
        providers.list().forEach { provider ->
            add("  ${provider.providerId} health=${provider.health.name.lowercase()} disabled=${provider.disabled}")
        }
        if (providers.list().none { it.health.name == "HEALTHY" && !it.disabled }) {
            add("  remediation: set one provider key, for example export GROQ_API_KEY=…")
        }
        add("mcp:")
        val mcpStatuses = mcp.statuses()
        if (mcpStatuses.isEmpty()) add("  none configured")
        else mcpStatuses.forEach { status ->
            add("  ${status.server.name} health=${status.health.name.lowercase()} reason=${status.reason}")
        }
    }
}
