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
import atropos.core.security.SecretEgressGate
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.provider.ProviderResponseContextParser
import atropos.core.provider.ImmutablePrompt
import atropos.core.provider.PromptRole
import atropos.core.dopamine.AlignmentTuner
import atropos.core.dopamine.RewardLogEntry
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
        ContextAttestationRenderer(TerminalTheme(atropos.cli.config.ConfigurationManager())),
    private val redactionFilter: atropos.core.security.RedactionFilter =
        atropos.core.security.RedactionFilter(),
    private val alignmentHistory: () -> List<RewardLogEntry> = { emptyList() },
    private val alignmentSignal: (Boolean) -> Unit = {}
) {

    fun dispatch(prompt: String, currentProviderName: String) {
        val immutablePrompt = ImmutablePrompt.of(prompt, PromptRole.TASK)
            ?: run {
                uiEngine.renderError("provider dispatch refused: prompt is blank")
                return
            }
        sessionTracker.recordPrompt(prompt, rateResolver(currentProviderName))
        uiEngine.renderExecutionEvent("accepted", "natural-language request received")
        uiEngine.startSpinner("Thinking")
        try {
            val routedProvider = routeProvider(prompt, currentProviderName)
            uiEngine.renderExecutionEvent("provider", "selected=$routedProvider")
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

            val tuning = AlignmentTuner.tune(alignmentHistory())
            val tunedPrompt = AlignmentTuner.apply(immutablePrompt.text, tuning)
            uiEngine.renderExecutionEvent("alignment", "prefix=${tuning.promptPrefix} examples=${tuning.fewShotExamples.size}")
            val response = provider.complete(
                tunedPrompt,
                context,
                atropos.core.GenerationParameters(
                    temperature = tuning.temperature,
                    topP = tuning.topP,
                    fewShotExamples = tuning.fewShotExamples
                )
            )
            uiEngine.renderExecutionEvent("response", "provider returned output")
            renderVerifiedResponse(
                prompt = prompt,
                context = context,
                response = response,
                provider = provider,
                envelope = envelope,
                mythologyRequested = mythologyRequested
            )
            alignmentSignal(true)
        } catch (failure: Exception) {
            // A provider exception is the most secret-dense string the CLI ever
            // renders: HTTP clients put the request URL and the Authorization
            // header into the message, and providers echo the offending key back
            // in error bodies. This used to paint `failure.message` verbatim.
            // ProviderCascadeFormatter.cleanError already existed to normalise
            // these — it just had no caller — and RedactionFilter strips whatever
            // survives normalisation.
            uiEngine.renderError(safeProviderFailure(failure, currentProviderName))
            alignmentSignal(false)
        } finally {
            uiEngine.renderExecutionEvent("complete", "provider execution finished")
            uiEngine.stopSpinner()
        }
    }

    /**
     * Normalises then redacts a provider failure, in that order.
     *
     * Order matters: [ProviderCascadeFormatter.cleanError] collapses a known
     * failure shape into a short operator-facing line, and redaction then covers
     * the unknown shapes it passes through unchanged. Redacting first would leave
     * `<redacted:…>` markers inside text the formatter tries to pattern-match.
     */
    internal fun safeProviderFailure(failure: Throwable, providerName: String): String {
        val raw = failure.message?.takeIf { it.isNotBlank() }
            ?: return "provider dispatch failed (${failure.javaClass.simpleName})"
        val normalized = runCatching {
            atropos.cli.ui.ProviderCascadeFormatter.cleanError(raw, providerName)
        }.getOrDefault(raw)
        return redactionFilter.compact(normalized, MAX_FAILURE_CHARS)
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
            is ContextAttestationService.VerifiedResult.Accepted -> {
                val egress = SecretEgressGate.scan(verified.cleanedResponse)
                if (egress.isNotEmpty()) {
                    uiEngine.renderError("provider response refused by secret egress gate")
                } else {
                    uiEngine.renderNotice(markdownRenderer.render(verified.cleanedResponse))
                }
            }

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
            val egress = SecretEgressGate.scan(retryVerified.cleanedResponse)
            if (egress.isNotEmpty()) {
                uiEngine.renderError("provider retry refused by secret egress gate")
            } else {
                uiEngine.renderNotice(markdownRenderer.render(retryVerified.cleanedResponse))
            }
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

    private companion object {
        const val ATTESTATION_WIDTH = 80

        /** Bounds a provider failure line so a huge error body cannot fill the screen. */
        const val MAX_FAILURE_CHARS = 400
    }
}
