/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderCascadeOrder

data class ProviderCascadeResult(
    val providerName: String,
    val response: String,
    val errors: List<ProviderError>,
    val contextEnvelope: ContextEnvelope? = null,
    val queued: Boolean = false,
    val earliestRetryEpochMs: Long? = null,
    val queueReason: String? = null
)

class ProviderCascadeRouter(
    private val factory: ProviderFactory,
    private val classifier: ProviderFailureClassifier = ProviderFailureClassifier(),
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val localHealth: () -> Boolean = { OllamaHealthProbe().probe().online }
) {
    fun completeWithCascade(
        requestedProvider: String,
        prompt: String,
        context: String,
        providerOrderOverride: List<String>? = null,
        beforeAttempt: (String) -> Unit = {},
        onFailure: (ProviderError) -> Unit = {},
        contextEnvelope: ContextEnvelope? = null
    ): ProviderCascadeResult {
        val order = providerOrder(requestedProvider, providerOrderOverride)
        val errors = mutableListOf<ProviderError>()
        val blocked = mutableSetOf<String>()

        for (providerName in order) {
            val provider = providerName.lowercase()
            if (provider in blocked) continue

            val descriptor = registry.getById(provider)
            if (descriptor?.isLocal == true && descriptor.hasCapability(ApiCapability.CHAT) && !localHealth()) {
                val error = ProviderError(
                    provider = provider,
                    type = FailureType.CONNECTION_REFUSED,
                    cleanMessage = "$provider unavailable"
                )
                errors += error
                onFailure(error)
                continue
            }

            try {
                beforeAttempt(provider)
                val aiProvider = factory.getProvider(provider)
                val response = aiProvider.complete(prompt, context)

                return ProviderCascadeResult(
                    providerName = provider,
                    response = response,
                    errors = errors,
                    contextEnvelope = contextEnvelope
                )
            } catch (failure: Exception) {
                val error = classifier.classify(provider, failure)
                errors += error
                onFailure(error)

                if (
                    error.type == FailureType.AUTH_INVALID ||
                    error.type == FailureType.MISSING_KEY
                ) {
                    blocked += provider
                }
            }
        }

        val cleanAggregate =
            if (errors.isEmpty()) {
                "no provider completed the request"
            } else {
                errors.joinToString(" | ") { it.cleanMessage }
            }

        val retryAt = System.currentTimeMillis() + 60_000L
        return ProviderCascadeResult(
            providerName = "local_queue",
            response = "",
            errors = errors,
            contextEnvelope = contextEnvelope,
            queued = true,
            earliestRetryEpochMs = retryAt,
            queueReason = cleanAggregate
        )
    }

    fun providerOrderPreview(requestedProvider: String, providerOrderOverride: List<String>? = null): List<String> =
        providerOrder(requestedProvider, providerOrderOverride)

    private fun providerOrder(requestedProvider: String, providerOrderOverride: List<String>? = null): List<String> {
        if (!providerOrderOverride.isNullOrEmpty()) {
            return providerOrderOverride.map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
        }

        val configured = System.getenv("ATROPOS_PROVIDER_ORDER")
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?: registry.getAll().map { it.id }

        return ProviderCascadeOrder.order(
            (listOf(requestedProvider.lowercase()) + configured)
                .filter { registry.getById(it) != null }
                .distinct(),
            registry
        )
    }
}
