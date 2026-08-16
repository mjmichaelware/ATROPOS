/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.StatusProviderDescriptorRenderer
import atropos.core.AtroposConfig
import atropos.core.ProviderDecisionEngine
import atropos.core.provider.ProviderActivationService
import atropos.core.provider.ProviderDescriptorReport
import atropos.core.provider.ProviderDescriptorValidator
import atropos.core.provider.ProviderTruthService
import atropos.core.provider.RoutedTask
import atropos.core.provider.StaticProviderDescriptorRegistry

class ProviderCommandHandler(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine
) {
    fun execute(tokens: List<String>, currentProviderName: String) {
        when (tokens.getOrNull(1)?.lowercase()) {
            "inventory" -> uiEngine.renderNotice(
                ProviderTruthService(config).snapshot(currentProviderName).renderInventory()
            )
            "descriptors" -> uiEngine.renderNotice(
                StatusProviderDescriptorRenderer(StaticProviderDescriptorRegistry()).render(currentProviderName) +
                    "\n" + ProviderDescriptorReport(StaticProviderDescriptorRegistry()).generate()
            )
            "matrix" -> uiEngine.renderNotice(
                RoutedTask.entries.joinToString("\n") { it.render() }
            )
            "validate" -> renderValidation()
            "verify" -> renderVerify(tokens)
            "live-test" -> renderLiveTest(tokens)
            else -> uiEngine.renderNotice(
                "active provider: $currentProviderName\n" + ProviderDecisionEngine().providersReport(config)
            )
        }
    }

    private fun renderValidation() {
        val violations = ProviderDescriptorValidator(StaticProviderDescriptorRegistry()).validate()
        if (violations.isEmpty()) {
            uiEngine.renderNotice("PROVIDER DESCRIPTORS: VALID")
        } else {
            uiEngine.renderNotice("PROVIDER DESCRIPTORS: INVALID")
            violations.forEach { uiEngine.renderNotice("  - ${it.id}: ${it.message}") }
        }
    }

    private fun renderVerify(tokens: List<String>) {
        val service = ProviderActivationService(config = config)
        val reference = tokens.getOrNull(2)
        when {
            reference == null -> uiEngine.renderError("usage: /providers verify <id|all>")
            reference.equals("all", ignoreCase = true) -> uiEngine.renderNotice(service.renderVerifyAll())
            else -> uiEngine.renderNotice(service.verify(reference).render())
        }
    }

    private fun renderLiveTest(tokens: List<String>) {
        val providerId = tokens.getOrNull(2)
        if (providerId == null) {
            uiEngine.renderError("usage: /providers live-test <id>")
        } else {
            uiEngine.renderNotice(ProviderActivationService(config = config).liveTest(providerId).render())
        }
    }
}
