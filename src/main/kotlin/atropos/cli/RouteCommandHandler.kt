/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.provider.FileQuotaLedger
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.adapter.AdapterRouteFacade
import java.io.File

class RouteCommandHandler(
    private val uiEngine: AnsiTerminalEngine
) {
    fun execute(tokens: List<String>): RouterOutcome {
        val prompt = tokens.drop(1).joinToString(" ").trim()
        if (prompt.isBlank()) {
            uiEngine.renderError("/route requires a prompt")
        } else {
            val registry = StaticProviderDescriptorRegistry()
            val ledger = FileQuotaLedger(
                File(".atropos/provider/quota-ledger.tsv"),
                FileQuotaLedger.seedFromDescriptors(registry)
            )
            uiEngine.renderNotice(AdapterRouteFacade(descriptorRegistry = registry, ledger = ledger).renderRoute(prompt))
        }
        return RouterOutcome.CONTINUE
    }
}
