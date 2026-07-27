package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.OllamaHealthProbe
import atropos.core.provider.ProviderCascadeOrder

data class AgentProviderSelection(
    val askOrder: List<String>,
    val patchOrder: List<String>,
    val doctorTruthSource: String,
    val knownActiveProviders: List<String>,
    val paidAutomaticModeLocked: Boolean = true,
    val localFallbackEnabled: Boolean = true
)

class AgentProviderSelector(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val ollamaProbe: () -> Boolean = { OllamaHealthProbe().probe().online }
) {
    private val sourceDocOrder = listOf(
        "groq",
        "github_models",
        "cloudflare_ai",
        "sambanova",
        "ollama"
    )

    private val patchDocOrder = listOf(
        "github_models",
        "sambanova",
        "cloudflare_ai",
        "groq"
    )

    private val doctorTruthSource = "30-provider doctor (2026-06-29)"
    private val doctorActiveProviders = listOf(
        "github_models",
        "cloudflare_workers_ai",
        "huggingface",
        "sambanova",
        "github_actions",
        "cloudflare_workers",
        "pinecone",
        "openai",
        "deepseek_direct"
    )

    fun select(
        activeProviderName: String = config.runtime.defaultProvider,
        patchProviderOverride: String? = null
    ): AgentProviderSelection {
        val configured = linkedSetOf<String>()

        if (config.keys.groq.isNotBlank()) configured += "groq"
        if (System.getenv("GITHUB_MODELS_TOKEN").isNullOrBlank().not()) configured += "github_models"
        if (System.getenv("CLOUDFLARE_API_TOKEN").isNullOrBlank().not() &&
            System.getenv("CLOUDFLARE_ACCOUNT_ID").isNullOrBlank().not()) {
            configured += "cloudflare_ai"
        }
        if (System.getenv("SAMBANOVA_API_KEY").isNullOrBlank().not()) configured += "sambanova"
        if (ollamaProbe()) configured += "ollama"

        val finalOrder = buildList {
            sourceDocOrder.forEach { provider ->
                if (provider in configured && provider !in this) add(provider)
            }
        }

        val patchConfigured = linkedSetOf<String>()
        if (System.getenv("GITHUB_MODELS_TOKEN").isNullOrBlank().not()) patchConfigured += "github_models"
        if (System.getenv("SAMBANOVA_API_KEY").isNullOrBlank().not()) patchConfigured += "sambanova"
        if (System.getenv("CLOUDFLARE_API_TOKEN").isNullOrBlank().not() &&
            System.getenv("CLOUDFLARE_ACCOUNT_ID").isNullOrBlank().not()) {
            patchConfigured += "cloudflare_ai"
        }
        if (config.keys.groq.isNotBlank()) patchConfigured += "groq"
        // Local-first applies to patch generation as well. The previous patch
        // order named no local provider at all, so a working local model was
        // never asked even when every remote one was unreachable.
        if (ollamaProbe()) patchConfigured += "ollama"

        val requestedPatchProvider = patchProviderOverride?.trim()?.lowercase().orEmpty()
        val patchOrder = buildList {
            if (requestedPatchProvider.isNotBlank()) {
                add(requestedPatchProvider)
            }
            patchDocOrder.forEach { provider ->
                if (provider in patchConfigured && provider !in this && provider != requestedPatchProvider) {
                    add(provider)
                }
            }
        }.ifEmpty {
            if (requestedPatchProvider.isNotBlank()) listOf(requestedPatchProvider) else emptyList()
        }

        val activeCandidate = activeProviderName.trim().lowercase()
        val knownActive = (doctorActiveProviders + activeCandidate)
            .filter { it.isNotBlank() }
            .distinct()

        // Local-first, then free-first. The hand-written lists above express a
        // preference between peers; cost ordering outranks that preference, and
        // paid-locked providers are removed rather than attempted.
        val orderedAsk = ProviderCascadeOrder.order(finalOrder).ifEmpty { listOf("ollama") }
        val orderedPatch = if (requestedPatchProvider.isNotBlank()) {
            // An explicit override stays first: the operator named it.
            listOf(requestedPatchProvider) +
                ProviderCascadeOrder.order(patchOrder.filterNot { it == requestedPatchProvider })
        } else {
            ProviderCascadeOrder.order(patchOrder)
        }

        return AgentProviderSelection(
            askOrder = orderedAsk,
            patchOrder = orderedPatch,
            doctorTruthSource = doctorTruthSource,
            knownActiveProviders = knownActive
        )
    }
}
