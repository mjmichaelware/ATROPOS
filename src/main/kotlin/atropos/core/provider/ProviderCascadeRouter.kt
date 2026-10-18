/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.OllamaHealthProbe
import atropos.core.paid.EmergencyPaidGate
import atropos.core.provider.ProviderApprovalCard
import atropos.core.provider.ProviderPolicyGate
import atropos.core.security.TokenIsolationVault
import atropos.core.ProviderError
import atropos.core.ProviderFactory
import atropos.core.AIProvider
import atropos.core.FailureType
import atropos.core.ProviderFailureClassifier

data class ProviderCascadeResult(
    val providerName: String,
    val response: String,
    val errors: List<ProviderError>,
    val contextEnvelope: ContextEnvelope? = null,
    val queued: Boolean = false,
    val earliestRetryEpochMs: Long? = null,
    val queueReason: String? = null,
    val paidApproval: ProviderApprovalCard? = null
)

/**
 * Orchestrates multi-provider fallback cascading with native payload formatting,
 * rate-limit backoff with buffer, and a mathematical 3-strike progress evaluation rule.
 */
class ProviderCascadeRouter(
    private val factory: ProviderFactory,
    private val classifier: ProviderFailureClassifier = ProviderFailureClassifier(),
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val localHealth: () -> Boolean = { OllamaHealthProbe().probe().online },
    private val providerResolver: ((String) -> AIProvider)? = null,
    private val healthyProviderIds: (() -> Set<String>)? = null,
    private val preferredProviderIds: (() -> List<String>)? = null,
    private val localOnly: () -> Boolean = { AtroposConfig.load().runtime.localOnly },
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    /** Reads secrets from environment, config, AND vault. Null defaults to env-only. */
    private val secretReader: ((String) -> String?)? = null,
    private val astDistanceCalculator: atropos.core.verification.AstEditDistanceCalculator = atropos.core.verification.AstEditDistanceCalculator()
) {
    fun arbitrateTriModelOutputs(outA: String, outB: String, outC: String): atropos.core.verification.TriModelDisputationResult =
        astDistanceCalculator.arbitrateTriModel(outA, outB, outC)

    /** Returns the documented chain through the canonical route owner. */
    fun declaredFallbackChain(capability: ApiCapability): FallbackChain? =
        FallbackChainRegistry.canonicalChain(capability)

    fun completeWithCascade(prompt: String, envelope: ContextEnvelope): ProviderCascadeResult {
        val targetProvider = when {
            envelope.providerId.contains("groq", ignoreCase = true) -> "groq"
            envelope.providerId.contains("gemini", ignoreCase = true) -> "gemini-2.5-pro"
            else -> envelope.providerId.ifBlank { "groq" }
        }
        return completeWithCascade(
            requestedProvider = targetProvider,
            prompt = prompt,
            context = envelope.task,
            contextEnvelope = envelope
        )
    }

    fun completeWithCascade(
        requestedProvider: String,
        prompt: String,
        context: String,
        providerOrderOverride: List<String>? = null,
        beforeAttempt: (String) -> Unit = {},
        onFailure: (ProviderError) -> Unit = {},
        contextEnvelope: ContextEnvelope? = null,
        acceptResponse: (String) -> Boolean = { it.isNotBlank() },
        allowPaidProvider: Boolean = paidGate.status().active != null
    ): ProviderCascadeResult {
        val order = providerOrder(requestedProvider, providerOrderOverride, allowPaidProvider)
        val candidates = ArrayDeque(order)
        val errors = mutableListOf<ProviderError>()
        val blocked = mutableSetOf<String>()
        val keyAttempts = mutableMapOf<String, Int>()

        atropos.core.thinking.Thinking.detail(
            "provider",
            "cascade order: " + order.joinToString(" → ")
        )

        while (candidates.isNotEmpty()) {
            val provider = candidates.removeFirst().lowercase()
            if (provider in blocked) continue

            atropos.core.thinking.Thinking.step("provider", "evaluating $provider")

            val descriptor = registry.getById(provider)
            if (descriptor != null && descriptor.hasCapability(ApiCapability.LOCAL_TOOL) && descriptor.hasCapability(ApiCapability.CHAT) && !localHealth()) {
                val error = ProviderError(
                    provider = provider,
                    type = FailureType.CONNECTION_REFUSED,
                    cleanMessage = "$provider unavailable"
                )
                errors += error
                onFailure(error)
                continue
            }

            var strike = 0
            var prevSnapshot: ExecutionSnapshot? = null

            while (strike < MathematicalRateLimitScheduler.MAX_STRIKES) {
                strike++
                atropos.core.thinking.Thinking.step(
                    "provider",
                    "asking $provider (strike $strike of ${MathematicalRateLimitScheduler.MAX_STRIKES})"
                )

                // 1. PER-PROVIDER FORMATTING ("Each According to Its Kind")
                val activeKey = secretReader?.invoke(provider)
                    ?: FallbackChainRegistry.getActiveKey(provider)
                    ?: UniversalRequestAdapter.resolveApiKey(provider)

                val adaptedPrompt = atropos.core.phase20.Phase20StructuralMetaAdapter.SHARED.adaptPrompt(provider, prompt)
                val formatted = UniversalRequestAdapter.translate(
                    providerId = provider,
                    prompt = adaptedPrompt,
                    context = context,
                    apiKey = activeKey
                )

                atropos.core.thinking.Thinking.detail(
                    "provider",
                    "Universal Request Adapter translated payload for $provider [schema=${formatted.schemaType}, model=${formatted.model}]"
                )

                beforeAttempt(provider)
                ActiveProvider.set(provider)

                atropos.core.thinking.Narrate.provider.lookup(
                    store = provider,
                    query = firstLine(adaptedPrompt),
                    hits = 0
                )
                atropos.core.thinking.Thinking.detail(
                    "provider",
                    "$provider: sending ${adaptedPrompt.length} chars with native ${formatted.schemaType} schema"
                )

                val startedAt = System.currentTimeMillis()
                val execResult: UniversalExecutionResult = try {
                    if (providerResolver != null) {
                        val aiProvider = providerResolver.invoke(provider)
                        val res = aiProvider.complete(adaptedPrompt, context)
                        UniversalExecutionResult.Success(
                            providerId = provider,
                            text = res,
                            rawResponse = res,
                            durationMs = System.currentTimeMillis() - startedAt
                        )
                    } else {
                        UniversalRequestAdapter.execute(formatted)
                    }
                } catch (failure: Exception) {
                    val universalError = UniversalErrorParser.parseAny(failure)
                    val is429 = universalError.category == UniversalErrorCategory.RATE_LIMIT_OVERLOAD ||
                        failure.message?.contains("429") == true ||
                        failure.message?.contains("rate limit", ignoreCase = true) == true
                    UniversalExecutionResult.Failure(
                        providerId = provider,
                        statusCode = universalError.httpCode ?: if (is429) 429 else 500,
                        message = failure.message ?: "$provider call failed",
                        headers = emptyMap(),
                        rawBody = universalError.raw,
                        isRateLimit = is429,
                        cause = failure
                    )
                }

                if (execResult is UniversalExecutionResult.Success && acceptResponse(execResult.text)) {
                    val elapsed = execResult.durationMs
                    atropos.core.dopamine.ProviderDopamineRewardTracker.SHARED.recordSuccess(
                        provider = provider,
                        action = "provider.chat"
                    )
                    atropos.core.thinking.Thinking.step(
                        "provider",
                        "$provider answered in ${elapsed}ms, ${execResult.text.length} characters"
                    )
                    atropos.core.thinking.Thinking.detail(
                        "provider",
                        "$provider said: ${firstLine(execResult.text)}"
                    )
                    return ProviderCascadeResult(
                        providerName = provider,
                        response = execResult.text,
                        errors = errors,
                        contextEnvelope = contextEnvelope
                    )
                }

                val isInvalid = execResult is UniversalExecutionResult.Success
                val outputText = if (isInvalid) (execResult as UniversalExecutionResult.Success).text else (execResult as UniversalExecutionResult.Failure).rawBody
                val currentSnapshot = ExecutionSnapshot(
                    attemptNumber = strike,
                    output = outputText,
                    error = if (isInvalid) "Response rejected by acceptance predicate" else (execResult as UniversalExecutionResult.Failure).message
                )

                val classifiedError = if (isInvalid) {
                    atropos.core.dopamine.ProviderDopamineRewardTracker.SHARED.recordFailure(
                        provider = provider,
                        action = "provider.chat",
                        errorType = "INVALID_RESPONSE",
                        exactMistake = "Response rejected by acceptance predicate"
                    )
                    ProviderError(
                        provider = provider,
                        type = FailureType.INVALID_RESPONSE,
                        cleanMessage = "$provider returned an invalid response for this request"
                    )
                } else {
                    val failure = execResult as UniversalExecutionResult.Failure
                    atropos.core.dopamine.ProviderDopamineRewardTracker.SHARED.recordFailure(
                        provider = provider,
                        action = "provider.chat",
                        errorType = "PROVIDER_EXECUTION_FAILURE",
                        exactMistake = failure.message
                    )
                    classifier.classify(provider, failure.cause ?: RuntimeException(failure.message))
                }

                // 2. RATE-LIMIT SLEEP WITH BUFFER
                val isRateLimit = !isInvalid && (
                    (execResult as UniversalExecutionResult.Failure).isRateLimit ||
                    classifiedError.type == FailureType.RATE_LIMIT
                )

                if (isRateLimit) {
                    val failure = execResult as UniversalExecutionResult.Failure
                    val retryAfterSec = MathematicalRateLimitScheduler.parseRetryAfterSeconds(
                        headers = failure.headers,
                        errorBody = failure.rawBody.ifBlank { failure.message }
                    )
                    atropos.core.thinking.Thinking.step(
                        "provider",
                        "HTTP 429 / OTPM rate limit on $provider: retry-after delta ${retryAfterSec}s. Sleeping ${retryAfterSec + MathematicalRateLimitScheduler.BUFFER_SECONDS}s (+10s buffer)..."
                    )
                    MathematicalRateLimitScheduler.sleepWithBuffer(retryAfterSec) { totalSec ->
                        atropos.core.thinking.Thinking.detail(
                            "provider",
                            "Completed rate-limit backoff sleep of ${totalSec}s for $provider"
                        )
                    }
                }

                // 3. MATHEMATICAL PROGRESS 3-STRIKE RULE
                if (strike == 1) {
                    prevSnapshot = currentSnapshot
                    atropos.core.thinking.Thinking.step(
                        "provider",
                        "Strike 1 failed on $provider ($classifiedError). Retrying SAME provider for strike 2..."
                    )
                    continue
                } else if (strike == 2) {
                    val progress = MathematicalRateLimitScheduler.computeProgress(
                        previous = prevSnapshot ?: ExecutionSnapshot(1),
                        current = currentSnapshot
                    )
                    atropos.core.thinking.Thinking.detail(
                        "provider",
                        "Strike 2 evaluation for $provider: ${progress.progressFormulaSummary}"
                    )
                    if (progress.hasProgress) {
                        atropos.core.thinking.Thinking.step(
                            "provider",
                            "Mathematical progress detected (> 0) on $provider. Granting third and final retry strike."
                        )
                        prevSnapshot = currentSnapshot
                        continue
                    } else {
                        atropos.core.thinking.Thinking.step(
                            "provider",
                            "No mathematical progress detected (Δ <= 0) on $provider after 2 strikes. Provider exhausted."
                        )
                        errors.add(classifiedError)
                        onFailure(classifiedError)
                        break
                    }
                } else {
                    atropos.core.thinking.Thinking.step(
                        "provider",
                        "Strike 3 failed on $provider. All 3 strikes exhausted."
                    )
                    errors.add(classifiedError)
                    onFailure(classifiedError)
                    break
                }
            }

            // 4. CASCADING RULES: Only cascade to the next provider after 3 failed strikes
            blocked.add(provider)
            val lastErr = errors.lastOrNull()
            val parsedError = FallbackChainRegistry.parseError(lastErr?.cleanMessage.orEmpty())
            var decision = FallbackChainRegistry.routeNext(provider, parsedError, candidates.toList())

            when (decision.action) {
                RoutingAction.ROUTE_HIGH_CONTEXT -> {
                    candidates.removeAll { it.lowercase() in FallbackChainRegistry.LOW_CONTEXT_MODELS }
                    val frontier = decision.targetProvider ?: "gemini"
                    if (frontier !in blocked) {
                        candidates.remove(frontier)
                        candidates.addFirst(frontier)
                        ActiveProvider.set(frontier)
                    }
                }
                RoutingAction.ROTATE_KEY -> {
                    val attempts = keyAttempts.getOrDefault(provider, 0) + 1
                    keyAttempts[provider] = attempts
                    if (attempts < 10 && FallbackChainRegistry.getActiveKey(provider) != null) {
                        blocked.remove(provider)
                        candidates.addFirst(provider)
                    }
                }
                RoutingAction.NEXT_PARALLEL_PROVIDER,
                RoutingAction.RELIABLE_FALLBACK -> {
                    decision.targetProvider?.let { next ->
                        if (next !in blocked && !candidates.contains(next)) {
                            candidates.addFirst(next)
                            ActiveProvider.set(next)
                        }
                    }
                }
                RoutingAction.PIPELINE_EXHAUSTED -> {
                    candidates.clear()
                    atropos.core.thinking.Thinking.step(
                        "provider",
                        "total pipeline failure: all configured fallback providers exhausted"
                    )
                }
            }
        }

        val cleanAggregate: String =
            if (errors.isEmpty()) {
                "no provider completed the request"
            } else {
                val errorMessages = mutableListOf<String>()
                for (e in errors) {
                    errorMessages.add(e.cleanMessage)
                }
                errorMessages.joinToString(" | ")
            }

        atropos.core.thinking.Thinking.step(
            "provider",
            "no provider answered; queuing for retry — $cleanAggregate"
        )
        val retryAt: Long = System.currentTimeMillis() + 60_000L
        val paidApproval: ProviderApprovalCard? = paidApprovalAfterFreeExhaustion(cleanAggregate)
        val providerNameResult: String = if (paidApproval == null) "local_queue" else "paid_approval_required"
        val queueReasonResult: String = paidApproval?.render() ?: cleanAggregate
        val errorResponse = "No provider answered. All configured providers failed:\n$cleanAggregate\n\nIf you need a paid provider, approve it first. Otherwise check your API keys and network."
        return ProviderCascadeResult(
            providerName = providerNameResult,
            response = errorResponse,
            errors = errors,
            contextEnvelope = contextEnvelope,
            queued = paidApproval == null,
            earliestRetryEpochMs = retryAt,
            queueReason = queueReasonResult,
            paidApproval = paidApproval
        )
    }

    private fun firstLine(text: String): String {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (line.length <= GIST_CELLS) line else line.take(GIST_CELLS) + "…"
    }

    fun providerOrderPreview(
        requestedProvider: String,
        providerOrderOverride: List<String>? = null,
        allowPaidProvider: Boolean = paidGate.status().active != null
    ): List<String> = providerOrder(requestedProvider, providerOrderOverride, allowPaidProvider)

    private fun providerOrder(
        requestedProvider: String,
        providerOrderOverride: List<String>? = null,
        allowPaidProvider: Boolean = false
    ): List<String> {
        if (!providerOrderOverride.isNullOrEmpty()) {
            val candidates = providerOrderOverride.map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .filter { !localOnly() || it == "ollama" }
                .distinct()
            val active = candidates.filter { !ModelDiscoveryEngine.isPruned(it) }
            val effective = if (active.isEmpty() && candidates.isNotEmpty()) candidates else active
            return ProviderCascadeOrder.order(effective, registry, allowPaidProvider, paidGate)
        }

        val configured = System.getenv("ATROPOS_PROVIDER_ORDER")
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?: preferredProviderIds?.invoke().orEmpty()
                .ifEmpty { registry.getAll().map { it.id } }

        val activeKeyProviders = FallbackChainRegistry.getAllActiveKeys().keys.map { it.lowercase() }

        val candidates = (listOf(requestedProvider.lowercase()) + configured + activeKeyProviders)
            .filter { registry.getById(it) != null }
            .filter { !localOnly() || it == "ollama" }
            .distinct()
        val active = candidates.filter { !ModelDiscoveryEngine.isPruned(it) }
        val effective = if (active.isEmpty() && candidates.isNotEmpty()) candidates else active

        return ProviderCascadeOrder.order(
            effective,
            registry,
            allowPaidProvider,
            paidGate
        )
    }

    private fun paidApprovalAfterFreeExhaustion(reason: String): ProviderApprovalCard? {
        if (localOnly() || healthyProviderIds == null) return null
        return ProviderPolicyGate(
            registry = registry,
            healthy = { healthyProviderIds.invoke() },
            paidGate = paidGate,
            localOnly = false
        ).paidApproval(ApiCapability.CHAT, "FREE/LOCAL cascade exhausted: $reason")
    }

    companion object {
        const val GIST_CELLS = 100

        fun createSecretReader(vault: TokenIsolationVault? = null): (String) -> String? {
            val config = AtroposConfig.load()
            val vaultInstance = vault ?: TokenIsolationVault()
            return { envVar ->
                val envValue = System.getenv(envVar)
                if (envValue != null && envValue.isNotBlank()) envValue else {
                    val configKeyName = envVar.lowercase()
                        .replace("_api_key", "")
                        .replace("_key", "")
                        .replace("_token", "")
                    val configValue = when (configKeyName) {
                        "groq" -> AtroposConfig.load().keys.groq
                        "openai" -> AtroposConfig.load().keys.openai
                        "anthropic" -> AtroposConfig.load().keys.anthropic
                        "xai" -> AtroposConfig.load().keys.xai
                        else -> null
                    }
                    if (configValue != null && configValue.isNotBlank()) configValue else {
                        runCatching { vaultInstance.readSecret(envVar) }.getOrNull()
                    }
                }
            }
        }
    }
}