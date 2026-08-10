package atropos.core.provider.adapter

import atropos.core.AIProvider
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderTaskClassifier

/**
 * Keeps the legacy [AIProvider] boundary source-compatible while routing
 * provider IDs that only have a canonical kernel adapter through that adapter.
 */
class ProviderAdapterAiBridge(
    private val adapter: ProviderAdapter,
    private val liveNetworkAllowed: Boolean = System.getenv("ATROPOS_LIVE_PROVIDER_TESTS") == "1"
) : AIProvider {
    private val classifier = ProviderTaskClassifier()

    override val name: String
        get() = adapter.providerId

    override fun complete(prompt: String, context: String): String {
        val task = classifier.classify(prompt)
        return when (val result = adapter.complete(
            AdapterRequest(
                task = task,
                prompt = prompt,
                context = context,
                dryRun = false,
                liveNetworkAllowed = liveNetworkAllowed
            )
        )) {
            is ProviderCallResult.Success -> result.content
            is ProviderCallResult.LocalOnly -> result.content
            is ProviderCallResult.Failure -> throw IllegalStateException(result.failure.cleanSummary)
            is ProviderCallResult.Queued -> throw IllegalStateException(result.reason)
        }
    }
}
