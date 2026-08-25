/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.StatusProviderDescriptorRenderer
import atropos.cli.input.TerminalModeManager
import atropos.core.AtroposConfig
import atropos.core.provider.ProviderActivationService
import atropos.core.provider.ProviderDescriptorReport
import atropos.core.provider.ProviderDescriptorValidator
import atropos.core.provider.ProviderTruthService
import atropos.core.provider.RoutedTask
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.ProviderOnboardingService
import atropos.core.security.RedactionFilter

class ProviderCommandHandler(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val secretReader: (String) -> CharArray? = ::readSecretFromTerminal,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService()
) {
    fun execute(tokens: List<String>, currentProviderName: String) {
        val expanded = tokens.any { it.equals("--full", ignoreCase = true) }
        when (tokens.getOrNull(1)?.lowercase()) {
            "list" -> uiEngine.renderBlock(onboarding.render().lines())
            "refresh" -> uiEngine.renderBlock(onboarding.refresh().map { "${it.providerId} health=${it.health.name.lowercase()}" })
            "test" -> renderLiveTest(tokens)
            "prefer" -> renderPreference(onboarding, tokens)
            "disable" -> renderDisable(onboarding, tokens)
            "enable" -> renderEnable(onboarding, tokens)
            "connect" -> renderConnect(onboarding, tokens)
            "inventory" -> uiEngine.renderNotice(
                ProviderTruthService(config).snapshot(currentProviderName).renderInventory(expanded)
            )
            "descriptors" -> {
                val registry = StaticProviderDescriptorRegistry()
                val list = StatusProviderDescriptorRenderer(registry).renderList(currentProviderName, expanded, uiEngine.viewportWidth)
                uiEngine.renderBlock(list)
            }
            "matrix" -> uiEngine.renderNotice(
                RoutedTask.entries.joinToString("\n") { it.render() }
            )
            "validate" -> renderValidation()
            "verify" -> renderVerify(tokens)
            "live-test" -> renderLiveTest(tokens)
            else -> uiEngine.renderNotice(
                ProviderTruthService(config).snapshot(currentProviderName).renderInventory(expanded)
            )
        }
    }

    private fun renderPreference(onboarding: ProviderOnboardingService, tokens: List<String>) {
        val id = tokens.getOrNull(2)
        if (id == null) uiEngine.renderError("usage: /providers prefer <provider>")
        else runCatching { onboarding.prefer(id); uiEngine.renderNotice("preferred provider: $id") }
            .onFailure { uiEngine.renderError(redactionFilter.compact(it.message ?: "provider preference failed")) }
    }

    private fun renderDisable(onboarding: ProviderOnboardingService, tokens: List<String>) {
        val id = tokens.getOrNull(2)
        if (id == null) uiEngine.renderError("usage: /providers disable <provider>")
        else runCatching { onboarding.disable(id); uiEngine.renderNotice("disabled provider: $id") }
            .onFailure { uiEngine.renderError(redactionFilter.compact(it.message ?: "provider disable failed")) }
    }

    private fun renderEnable(onboarding: ProviderOnboardingService, tokens: List<String>) {
        val id = tokens.getOrNull(2)
        if (id == null) uiEngine.renderError("usage: /providers enable <provider>")
        else runCatching { onboarding.enable(id); uiEngine.renderNotice("enabled provider: $id") }
            .onFailure { uiEngine.renderError(redactionFilter.compact(it.message ?: "provider enable failed")) }
    }

    private fun renderConnect(onboarding: ProviderOnboardingService, tokens: List<String>) {
        val providerId = tokens.getOrNull(2)?.trim()?.lowercase()
        if (providerId.isNullOrBlank()) {
            uiEngine.renderError("usage: /providers connect <provider>; the key is requested privately and is never a command argument")
            return
        }
        val envName = onboarding.defaultEnvName(providerId)
        val secret = secretReader("$providerId key ($envName), input hidden: ")
        if (secret == null || secret.isEmpty()) {
            uiEngine.renderError("provider connect cancelled; no key was stored")
            return
        }
        try {
            val path = onboarding.connectToVault(providerId, String(secret), envName)
            uiEngine.renderNotice("provider connected locally: $providerId source=local_vault path=${path.fileName}")
        } catch (failure: RuntimeException) {
            uiEngine.renderError("provider connect failed: ${redactionFilter.compact(failure.message ?: "local vault refused the key")}")
        } finally {
            secret.fill('\u0000')
        }
    }

    private companion object {
        fun readSecretFromTerminal(prompt: String): CharArray? {
            System.console()?.let { return it.readPassword(prompt) }
            // Codespaces and some Termux launchers expose no java.io.Console even
            // though /dev/tty is available. Reuse the sole terminal-mode owner so
            // the fallback never reads a secret with terminal echo enabled.
            val terminal = TerminalModeManager()
            if (!terminal.enableRawMode()) return null
            return try {
                System.err.print(prompt)
                val value = StringBuilder()
                while (true) {
                    when (val code = System.`in`.read()) {
                        -1, '\n'.code, '\r'.code -> break
                        3 -> return null // Ctrl-C: cancel without storing anything.
                        8, 127 -> if (value.isNotEmpty()) value.deleteAt(value.length - 1)
                        else -> value.append(code.toChar())
                    }
                }
                value.toString().toCharArray()
            } finally {
                System.err.println()
                terminal.close()
            }
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
            val service = ProviderActivationService(
                config = config,
                liveTestHealthReporter = { testedId, healthy ->
                    onboarding.recordLiveTest(testedId, healthy)
                }
            )
            uiEngine.renderNotice(service.liveTest(providerId).render())
        }
    }
}
