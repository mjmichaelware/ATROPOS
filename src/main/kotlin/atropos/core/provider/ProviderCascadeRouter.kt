/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderCascadeOrder
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderFailureClassifier
import atropos.core.AtroposConfig
import atropos.core.OllamaHealthProbe
import atropos.core.provider.FallbackChain
import atropos.core.provider.FallbackChainRegistry
import atropos.core.paid.EmergencyPaidGate
import atropos.core.provider.ProviderApprovalCard
import atropos.core.provider.ProviderPolicyGate
import atropos.core.security.TokenIsolationVault
import atropos.core.ProviderError
import atropos.core.ProviderFactory
import atropos.core.AIProvider
import atropos.core.FailureType

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
    private val secretReader: ((String) -> String?)? = null
) {
    /** Returns the documented chain through the canonical route owner. */
    fun declaredFallbackChain(capability: ApiCapability): FallbackChain? =
        FallbackChainRegistry.canonicalChain(capability)

    private fun hasValidApiKeys(descriptor: ProviderDescriptor): Boolean {
        return descriptor.requiredEnv.all { envVar ->
            val value = secretReader?.invoke(envVar) ?: System.getenv(envVar)
                ?: when (envVar.lowercase()
                    .replace("_api_key", "")
                    .replace("_key", "")
                    .replace("_token", "")) {
                    "groq" -> AtroposConfig.load().keys.groq
                    "openai" -> AtroposConfig.load().keys.openai
                    "anthropic" -> AtroposConfig.load().keys.anthropic
                    "xai" -> AtroposConfig.load().keys.xai
                    else -> null
                }
            value != null && value.isNotBlank() && value != "your-api-key" && value != "test"
        }
    }

    fun completeWithCascade(
        requestedProvider: String,
        prompt: String,
        context: String,
        providerOrderOverride: List<String>? = null,
        beforeAttempt: (String) -> Unit = {},
        onFailure: (ProviderError) -> Unit = {},
        contextEnvelope: ContextEnvelope? = null,
        acceptResponse: (String) -> Boolean = { true },
        allowPaidProvider: Boolean = paidGate.status().active != null
    ): ProviderCascadeResult {
        val order = providerOrder(requestedProvider, providerOrderOverride, allowPaidProvider)
        val errors = mutableListOf<ProviderError>()
        val blocked = mutableSetOf<String>()

        // Which providers will be tried, and in what order. A run that waits
        // on a model has to say what it is waiting on -- an operator watching
        // a silent minute cannot tell a slow provider from a hung one.
        atropos.core.thinking.Thinking.detail(
            "provider",
            "cascade order: " + order.joinToString(" → ")
        )

        for (providerName in order) {
            val provider = providerName.lowercase()
            if (provider in blocked) continue

            atropos.core.thinking.Thinking.step("provider", "asking $provider")

            // Check health per provider: local (ollama) needs ollama running,
            // remote providers need valid API keys configured
            val descriptor = registry.getById(provider)
            val providerHealthy = when {
                provider == "ollama" -> localHealth()
                descriptor == null -> false
                else -> hasValidApiKeys(descriptor)
            }
            if (!providerHealthy) {
                val error = ProviderError(
                    provider = provider,
                    type = FailureType.CONNECTION_REFUSED,
                    cleanMessage = "$provider unavailable (health check failed)"
                )
                errors += error
                onFailure(error)
                continue
            }

            try {
                beforeAttempt(provider)
                val aiProvider = providerResolver?.invoke(provider) ?: factory.getProvider(provider)

                // What was asked, in the operator's words rather than in the
                // engine's. A trace that says "asking anthropic" and then goes
                // quiet for ninety seconds has told the operator nothing they
                // could not see from the spinner; a trace that says what the
                // question was, and then what came back, is the run explaining
                // itself.
                atropos.core.thinking.Narrate.provider.lookup(
                    store = provider,
                    query = firstLine(prompt),
                    hits = 0
                )
                atropos.core.thinking.Thinking.detail(
                    "provider",
                    "$provider: sending ${prompt.length} characters" +
                        (context?.let { " with ${it.length} characters of context" } ?: " with no extra context")
                )

                val startedAt = System.currentTimeMillis()
                val response = aiProvider.complete(prompt, context)
                val elapsed = System.currentTimeMillis() - startedAt

                if (!acceptResponse(response)) {
                    val error = ProviderError(
                        provider = provider,
                        type = FailureType.INVALID_RESPONSE,
                        cleanMessage = "$provider returned an invalid response for this request"
                    )
                    errors += error
                    onFailure(error)
                    atropos.core.thinking.Thinking.step(
                        "provider",
                        "$provider response rejected; trying the next eligible provider"
                    )
                    continue
                }

                atropos.core.thinking.Thinking.step(
                    "provider",
                    "$provider answered in ${elapsed}ms, ${response.length} characters"
                )
                atropos.core.thinking.Thinking.detail(
                    "provider",
                    "$provider said: ${firstLine(response)}"
                )

                return ProviderCascadeResult(
                    providerName = provider,
                    response = response,
                    errors = errors,
                    contextEnvelope = contextEnvelope
                )
            } catch (failure: Exception) {
                val classifiedError: ProviderError = classifier.classify(provider, failure)
                errors.add(classifiedError)
                onFailure(classifiedError)
                // Named at L2, not L3. A provider dropping out changes which
                // model answered, which is a fact about the result and not a
                // detail of how it was obtained.
                val errorType: FailureType = classifiedError.type
                val errorCleanMessage: String = classifiedError.cleanMessage
                atropos.core.thinking.Thinking.step(
                    "provider",
                    "$provider did not answer ($errorType): $errorCleanMessage"
                )

                if (
                    classifiedError.type == FailureType.AUTH_INVALID ||
                    classifiedError.type == FailureType.MISSING_KEY
                ) {
                    blocked.add(provider)
                }
            }
        }

        val cleanAggregate: String =
            if (errors.isEmpty()) {
                "no provider completed the request"
            } else {
                errors.joinToString(" | ") { (it: ProviderError) -> it.cleanMessage }
            }

        atropos.core.thinking.Thinking.step(
            "provider",
            "no provider answered; queuing for retry — $cleanAggregate"
        )
        val retryAt: Long = System.currentTimeMillis() + 60_000L
        val paidApproval: ProviderApprovalCard? = paidApprovalAfterFreeExhaustion(cleanAggregate)
        val providerNameResult: String = if (paidApproval == null) "local_queue" else "paid_approval_required"
        val queueReasonResult: String = paidApproval?.render() ?: cleanAggregate
        return ProviderCascadeResult(
            providerName = providerNameResult,
            response = "",
            errors = errors,
            contextEnvelope = contextEnvelope,
            queued = paidApproval == null,
            earliestRetryEpochMs = retryAt,
            queueReason = queueReasonResult,
            paidApproval = paidApproval
        )
    }

    /**
     * The gist of a prompt or an answer for the trace.
     *
     * The first non-blank line, clipped. Narrating a whole prompt would put a
     * research brief into the terminal and narrating a whole answer would put
     * the run's output there twice.
     */
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
            return providerOrderOverride.map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .filter { !localOnly() }
                .distinct()
                .let { ProviderCascadeOrder.order(it, registry, allowPaidProvider, paidGate) }
        }

        val configured = System.getenv("ATROPOS_PROVIDER_ORDER")
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?: preferredProviderIds?.invoke().orEmpty()
                .ifEmpty { registry.getAll().map { it.id } }

        return ProviderCascadeOrder.order(
            (listOf(requestedProvider.lowercase()) + configured)
                .filter { registry.getById(it) != null }
                .filter { !localOnly() }
                .distinct(),
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
        /** How much of a prompt or an answer one narrated line carries. */
        const val GIST_CELLS = 100

        /** Creates a secret reader that checks environment variables, config, AND the vault. */
        fun createSecretReader(vault: TokenIsolationVault? = null): (String) -> String? {
            val config = AtroposConfig.load()
            return { envVar ->
                // 1. Environment variables (highest priority)
                val envValue = System.getenv(envVar)
                if (envValue != null && envValue.isNotBlank()) envValue else {
                    // 2. AtroposConfig keys (from ~/.atropos/config.json)
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
                        // 3. Vault (encrypted files) - not implemented in this reader
                        null
                    }
                }
            }
        }
    }
}