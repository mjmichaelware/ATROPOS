package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Breakpoint
import atropos.cli.ui.design.Health
import atropos.core.provider.adapter.AdapterKernelFixtures
import atropos.core.provider.adapter.AdapterRouteFacade
import atropos.core.provider.adapter.AdapterStatus
import atropos.core.provider.adapter.AssetProviderCatalog
import atropos.core.provider.adapter.AssetProviderFixtures
import atropos.core.provider.adapter.DataInfraKernelFixtures
import atropos.core.provider.adapter.DataInfraResearchProviderCatalog
import atropos.core.provider.adapter.NonOpenAiFreeProviderCatalog
import atropos.core.provider.adapter.NonOpenAiKernelFixtures
import atropos.core.provider.adapter.OpenAiCompatibleProviderCatalog

class StatusAdapterRenderer(
    private val facade: AdapterRouteFacade = AdapterRouteFacade(),
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun render(): String = render(DEFAULT_WIDTH)

    fun render(width: Int): String {
        val statuses = facade.adapterStatus()
        val openAiIds = OpenAiCompatibleProviderCatalog.all().map { it.providerId }.toSet()
        val nonOpenAiIds = NonOpenAiFreeProviderCatalog.all().map { it.providerId }.toSet()
        val dataInfraIds = DataInfraResearchProviderCatalog.all().map { it.providerId }.toSet()
        val assetIds = AssetProviderCatalog.all().map { it.providerId }.toSet()
        val fixtureFailures =
            AdapterKernelFixtures.runOpenAiCompatibleFamily().filterNot { it.passed } +
                NonOpenAiKernelFixtures.runNonOpenAiFreeFamily().filterNot { it.passed } +
                DataInfraKernelFixtures.runDataInfraResearchFamily().filterNot { it.passed } +
                AssetProviderFixtures.runAssetFamily().filterNot { it.passed }

        val lines = buildList {
            add(surface.sectionHeading("ADAPTERS", width))
            add(surface.row("total", statuses.size.toString(), width))
            add(surface.statusRow("implemented", "${statuses.count { it.implemented }}", Health.VERIFIED, width))
            add(surface.statusRow("configured", "${statuses.count { it.configured }}", Health.VERIFIED, width))
            add(surface.statusRow("dry run", "${statuses.count { it.dryRunOnly }}", Health.PENDING, width))
            add(family("openai", statuses, openAiIds, width))
            add(family("non-openai", statuses, nonOpenAiIds, width))
            add(family("data infra", statuses, dataInfraIds, width))
            add(family("assets", statuses, assetIds, width))
            add(
                surface.statusRow(
                    "fixtures",
                    if (fixtureFailures.isEmpty()) "all passing" else "${fixtureFailures.size} failing",
                    if (fixtureFailures.isEmpty()) Health.VERIFIED else Health.ERROR,
                    width
                )
            )
            add(surface.hint("fixture-backed transports · live tests opt-in", width))
            add("")

            if (statuses.isEmpty()) {
                addAll(surface.emptyState("no adapters registered", "/providers descriptors", width))
            } else {
                addAll(detail(statuses, width))
            }
        }

        return lines.joinToString("\n").trimEnd()
    }

    /** Stacks at phone width, tabulates when there is room. */
    private fun detail(statuses: List<AdapterStatus>, width: Int): List<String> =
        if (Breakpoint.of(width) == Breakpoint.COMPACT) {
            statuses.flatMap { status ->
                listOf(
                    TerminalText.ellipsize(
                        surface.badge(healthLabel(status), health(status)) + " " + status.providerId,
                        width
                    ),
                    surface.hint("  ${status.modelCount} models · ${status.detail}", width)
                )
            }
        } else {
            surface.table(
                headers = listOf("PROVIDER", "STATE", "MODELS", "DETAIL"),
                rows = statuses.map { status ->
                    listOf(
                        status.providerId,
                        surface.badge(healthLabel(status), health(status)),
                        status.modelCount.toString(),
                        status.detail
                    )
                },
                widths = listOf(20, 14, 8, (width - 44).coerceAtLeast(10)),
                totalWidth = width
            )
        }

    private fun family(
        label: String,
        statuses: List<AdapterStatus>,
        ids: Set<String>,
        width: Int
    ): String {
        val ready = statuses.count { it.providerId in ids && it.implemented }
        return surface.statusRow(
            label,
            "$ready/${ids.size}",
            when {
                ids.isEmpty() -> Health.UNKNOWN
                ready == ids.size -> Health.VERIFIED
                ready == 0 -> Health.ERROR
                else -> Health.PENDING
            },
            width
        )
    }

    private fun health(status: AdapterStatus): Health = when {
        !status.implemented -> Health.ERROR
        status.configured -> Health.VERIFIED
        else -> Health.PENDING
    }

    private fun healthLabel(status: AdapterStatus): String = when {
        !status.implemented -> "not built"
        status.dryRunOnly -> "dry run"
        status.configured -> "configured"
        else -> "no key"
    }

    private companion object {
        const val DEFAULT_WIDTH = 80
    }
}
