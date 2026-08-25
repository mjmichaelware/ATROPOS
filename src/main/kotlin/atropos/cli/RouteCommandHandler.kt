/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.provider.FileQuotaLedger
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.adapter.AdapterRouteFacade
import java.io.File

class RouteCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val onboarding: atropos.core.provider.ProviderOnboardingService =
        atropos.core.provider.ProviderOnboardingService()
) {
    private val renderer = atropos.cli.ui.StatusRouteRenderer()

    fun execute(tokens: List<String>): RouterOutcome {
        val prompt = tokens.drop(1).joinToString(" ").trim()
        if (prompt.isBlank()) {
            uiEngine.renderError("/route requires a prompt")
        } else {
            val registry = StaticProviderDescriptorRegistry()
            val ledger = FileQuotaLedger(
                atropos.core.provider.ProviderQuotaPaths.defaultLedger(),
                FileQuotaLedger.seedFromDescriptors(registry)
            )
            val facade = AdapterRouteFacade(
                descriptorRegistry = registry,
                ledger = ledger,
                onboarding = onboarding
            )
            val result = facade.decide(prompt, dryRun = true)
            uiEngine.renderBlock(renderer.renderRoute(result, uiEngine.viewportWidth))
        }
        return RouterOutcome.CONTINUE
    }
}
