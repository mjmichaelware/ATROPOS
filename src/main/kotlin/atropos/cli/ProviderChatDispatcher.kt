/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.ProviderDecisionEngine
import atropos.core.provider.ImmutablePrompt
import atropos.core.provider.PromptRole
import atropos.core.security.SecretEgressGate
import atropos.core.security.TokenIsolationVault

class ProviderChatDispatcher(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val sessionTracker: QuotaSessionTracker,
    private val rateResolver: (String) -> Double,
    private val redactionFilter: atropos.core.security.RedactionFilter =
        atropos.core.security.RedactionFilter(),
    private val providerRelay: atropos.cli.ui.ProviderRelay =
        atropos.cli.ui.ProviderRelay(atropos.cli.ui.TerminalTheme(atropos.cli.config.ConfigurationManager())),
    private val alignmentSignal: (Boolean) -> Unit = {},
    private val onboarding: atropos.core.provider.ProviderOnboardingService =
        atropos.core.provider.ProviderOnboardingService(),
    /**
     * The chain walker. Injected so a test can drive the fallback without a
     * network, and shared with AgentService rather than reimplemented -- there
     * is one answer to "which provider next" and this is not a second one.
     */
    private val cascadeRouter: atropos.core.provider.ProviderCascadeRouter =
        atropos.core.provider.ProviderCascadeRouter(
            atropos.core.ProviderFactory(config),
            healthyProviderIds = { onboarding.healthyProviderIds() },
            preferredProviderIds = { onboarding.preferredProviderIds() },
            localOnly = { config.runtime.localOnly },
            secretReader = atropos.core.provider.ProviderCascadeRouter.createSecretReader(TokenIsolationVault())
        )
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
            // Through the cascade, not one provider.
            //
            // This called `provider.complete` directly and let the catch below
            // end the turn, so a single refusal was fatal: an operator with
            // twenty-three configured providers lost the turn because Groq had
            // retired one model. ProviderCascadeRouter already knew how to walk
            // the declared chain -- AgentService and AgentRepairService have
            // used it all along -- so chat was the one path that gave up.
            //
            // Each attempt is announced, so the operator can see the fallback
            // happening rather than wondering why the answer came from
            // somewhere other than the provider in the status bar.
            val cascade = cascadeRouter.completeWithCascade(
                requestedProvider = routedProvider,
                prompt = immutablePrompt.text,
                context = "",
                beforeAttempt = { candidate ->
                    if (candidate != routedProvider) {
                        uiEngine.renderExecutionEvent("provider", "falling back to $candidate")
                    }
                },
                onFailure = { error ->
                    // Reported per attempt rather than only at the end: a chain
                    // that quietly tried six providers and failed looks like one
                    // that never tried, and the operator cannot tell which key
                    // is the broken one.
                    uiEngine.renderExecutionEvent(
                        "provider",
                        "${error.provider} refused: ${redactionFilter.compact(error.cleanMessage, 120)}"
                    )
                }
            )
            if (cascade.paidApproval != null) {
                val tried = cascade.errors.map { it.provider }.distinct()
                uiEngine.renderError(
                    "no free or local provider answered" +
                        (if (tried.isEmpty()) "" else " (tried ${tried.joinToString(", ")})") +
                        "; paid providers stay locked for conversation. " +
                        "Run /providers to inspect health, /providers connect <provider> to add a key, " +
                        "or /paid unlock <provider> <duration> to approve paid explicitly."
                )
                alignmentSignal(false)
                return
            }
            if (cascade.providerName != routedProvider) {
                // Drawn as a relay rather than counted in a sentence.
                //
                // "answered by groq after 2 refusal(s)" tells an operator that
                // something happened and not what: which providers were tried,
                // why each dropped out, and therefore whether the answer they
                // are reading came from the model they chose or from a
                // fallback whose output they might weigh differently. The
                // cascade is the most distinctive thing this engine does and it
                // was reaching them as a number.
                val legs = cascade.errors.map {
                    atropos.cli.ui.ProviderRelay.Leg(it.provider, redactionFilter.compact(it.cleanMessage, 60))
                } + atropos.cli.ui.ProviderRelay.Leg(cascade.providerName)
                uiEngine.renderBlock(providerRelay.render(legs, uiEngine.viewportWidth))
            }
            val response = cascade.response
            // Always render the cascade response (success or error) as an assistant message
            // so the user sees what happened persistently in the conversation.
            if (response.isBlank()) {
                uiEngine.renderError("provider returned empty response")
                alignmentSignal(false)
            } else {
                uiEngine.renderExecutionEvent("response", "provider returned output")
                val egress = SecretEgressGate.scan(response)
                if (egress.isNotEmpty()) {
                    uiEngine.renderError("provider response refused by secret egress gate")
                    alignmentSignal(false)
                } else {
                    uiEngine.renderAssistant(cascade.providerName, response)
                    alignmentSignal(true)
                }
            }
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
            val decision = ProviderDecisionEngine(onboarding).decide(prompt, config)
            uiEngine.renderNotice("route: ${decision.taskClass.name.lowercase()} -> ${decision.provider} (${decision.reason})")
            decision.provider
        } else {
            currentProviderName
        }

    private companion object {
        /** Bounds a provider failure line so a huge error body cannot fill the screen. */
        const val MAX_FAILURE_CHARS = 400
    }
}
