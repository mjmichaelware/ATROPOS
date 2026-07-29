/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.ContextAttestationRenderer
import atropos.cli.ui.MarkdownRenderer
import atropos.cli.ui.TerminalTheme
import atropos.core.AIProvider
import atropos.core.AtroposConfig
import atropos.core.ProviderDecisionEngine
import atropos.core.agent.AgentPromptContract
import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.provider.ProviderResponseContextParser
import java.nio.file.Path

class ProviderChatDispatcher(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val sessionTracker: QuotaSessionTracker,
    private val providerResolver: (String) -> AIProvider,
    private val rateResolver: (String) -> Double,
    private val cwd: () -> String,
    private val markdownRenderer: MarkdownRenderer = MarkdownRenderer(),
    private val attestationRenderer: ContextAttestationRenderer =
        ContextAttestationRenderer(TerminalTheme(atropos.cli.config.ConfigurationManager()))
) {
    fun dispatch(prompt: String, currentProviderName: String) {
        sessionTracker.recordPrompt(prompt, rateResolver(currentProviderName))
        uiEngine.startSpinner("Thinking")
        try {
            val routedProvider = routeProvider(prompt, currentProviderName)
            val provider = providerResolver(routedProvider)
            val repoRoot = Path.of(cwd()).toAbsolutePath().normalize()
            val mythologyRequested = isExplicitMythologyRequest(prompt)
            val envelope = ContextEnvelopeFactory.createSimple(
                providerId = routedProvider,
                modelId = "",
                task = prompt,
                repoRoot = repoRoot
            )
            val context = AgentPromptContract.build(
                context = "",
                providerId = routedProvider,
                task = prompt,
                repoRoot = repoRoot,
                explicitMythologyRequest = mythologyRequested
            )

            val response = provider.complete(prompt, context)
            renderVerifiedResponse(
                prompt = prompt,
                context = context,
                response = response,
                provider = provider,
                envelope = envelope,
                mythologyRequested = mythologyRequested
            )
        } catch (failure: Exception) {
            uiEngine.renderError(failure.message ?: "provider dispatch failed")
        } finally {
            uiEngine.stopSpinner()
        }
    }

    private fun routeProvider(prompt: String, currentProviderName: String): String =
        if (currentProviderName.lowercase() == "auto") {
            val decision = ProviderDecisionEngine().decide(prompt, config)
            uiEngine.renderNotice("route: ${decision.taskClass.name.lowercase()} -> ${decision.provider} (${decision.reason})")
            decision.provider
        } else {
            currentProviderName
        }

    private fun renderVerifiedResponse(
        prompt: String,
        context: String,
        response: String,
        provider: AIProvider,
        envelope: atropos.core.provider.ContextEnvelope,
        mythologyRequested: Boolean
    ) {
        when (val verified = ContextAttestationService.verify(envelope, response)) {
            is ContextAttestationService.VerifiedResult.Accepted ->
                uiEngine.renderNotice(markdownRenderer.render(verified.cleanedResponse))

            is ContextAttestationService.VerifiedResult.Rejected ->
                renderRejected(prompt, context, response, provider, envelope, mythologyRequested, verified)
        }
    }

    private fun renderRejected(
        prompt: String,
        context: String,
        response: String,
        provider: AIProvider,
        envelope: atropos.core.provider.ContextEnvelope,
        mythologyRequested: Boolean,
        verified: ContextAttestationService.VerifiedResult.Rejected
    ) {
        if (mythologyRequested) {
            val shownMyth = ProviderResponseContextParser.parse(response, envelope).cleanedResponse
            uiEngine.renderNotice(markdownRenderer.render(shownMyth))
            return
        }

        val corrective = buildString {
            appendLine(context)
            appendLine()
            appendLine(
                "Your previous reply did not satisfy the ATROPOS context contract. " +
                    "ATROPOS is this software repository and runtime, not the Greek " +
                    "mythological figure. Answer the task in that context and include " +
                    "the attestation block exactly as specified."
            )
        }
        val retry = runCatching { provider.complete(prompt, corrective) }.getOrNull()
        val retryVerified = retry?.let { ContextAttestationService.verify(envelope, it) }

        if (retryVerified is ContextAttestationService.VerifiedResult.Accepted) {
            uiEngine.renderNotice(markdownRenderer.render(retryVerified.cleanedResponse))
        } else {
            uiEngine.renderNotice(attestationRenderer.renderAdvisory(verified.failure, ATTESTATION_WIDTH))
            val shown = ProviderResponseContextParser.parse(retry ?: response, envelope).cleanedResponse
            uiEngine.renderNotice(markdownRenderer.render(shown))
        }
    }

    private fun isExplicitMythologyRequest(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return (lower.contains("greek") || lower.contains("mythology") ||
            lower.contains("myth") || lower.contains("moirai") || lower.contains("fates")) &&
            lower.contains("atropos")
    }

    private companion object { const val ATTESTATION_WIDTH = 80 }
}
