/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * The eleven named fallback chains from Source Doc 2 §.300 §6, as data.
 *
 * The document writes them as literals:
 *
 * ```
 * CHAT_CHAIN=groq → gemini → openrouter_free → github_models → cloudflare_ai → ollama → paid_emergency
 * ```
 *
 * They existed in the codebase only as branches inside routing code, which made
 * two things impossible. A chain could not be *shown* — an operator asking why a
 * call went to Gemini got a decision, not the ordering that produced it — and a
 * chain could not be *changed* without a code edit, which is how a routing table
 * and the document that specifies it drift apart.
 *
 * Eight of the eleven root at [ChainLink.LOCAL_TOOLCHAIN], which is provider id 0 in the
 * grid and was not a registered provider at all. That is why it is declared here
 * as a first-class link rather than assumed: a chain whose root is missing
 * silently starts at its second entry, and the local-first guarantee that makes
 * ATROPOS cheap becomes a preference nothing enforces.
 *
 * Terminal links are declared too. `paid_emergency` is a real position in seven
 * of these chains and is *not* reachable without an explicit unlock — modelling
 * it as a link rather than omitting it is what lets the route explanation say
 * "and then it would have stopped", which is the honest answer.
 */
enum class FallbackChain(
    val canonical: String,
    val capability: ApiCapability,
    val links: List<String>
) {
    CHAT(
        "CHAT_CHAIN", ApiCapability.CHAT,
        listOf("groq", "gemini", "openrouter_free", "github_models", "cloudflare_ai", "ollama", ChainLink.PAID_EMERGENCY)
    ),
    CODE(
        "CODE_CHAIN", ApiCapability.CODE,
        listOf(
            "groq", "openrouter_free_coder", "github_models", "nvidia", "deepinfra",
            "siliconflow", "gemini", "ollama", ChainLink.PAID_EMERGENCY
        )
    ),
    REPAIR(
        "REPAIR_CHAIN", ApiCapability.REPAIR,
        listOf(ChainLink.LOCAL_TOOLCHAIN, "groq", "openrouter_free", "gemini", "github_models", ChainLink.QUEUED, ChainLink.PAID_EMERGENCY)
    ),
    PLANNING(
        "PLANNING_CHAIN", ApiCapability.PLAN,
        listOf("gemini", "groq", "github_models", "openrouter_free", ChainLink.QUEUED, ChainLink.PAID_EMERGENCY)
    ),
    DOCS(
        "DOCS_CHAIN", ApiCapability.LARGE_CONTEXT,
        listOf(ChainLink.LOCAL_TOOLCHAIN, "gemini", "jina", "github_models", ChainLink.QUEUED, ChainLink.PAID_EMERGENCY)
    ),
    SEARCH(
        "SEARCH_CHAIN", ApiCapability.WEB,
        listOf(ChainLink.LOCAL_TOOLCHAIN, "jina", "serpapi", ChainLink.MANUAL)
    ),
    EMBED(
        "EMBED_CHAIN", ApiCapability.EMBED,
        listOf(ChainLink.LOCAL_TOOLCHAIN, "jina", "huggingface", "cloudflare_ai", ChainLink.LOCAL_ONLY)
    ),
    MEMORY(
        "MEMORY_CHAIN", ApiCapability.VECTOR_DB,
        listOf(ChainLink.LOCAL_TOOLCHAIN, "jsonl_snapshot", "pinecone", "supabase")
    ),
    SECRET(
        "SECRET_CHAIN", ApiCapability.SECRET,
        listOf(ChainLink.LOCAL_TOOLCHAIN, "env_vars", "google_cloud_free", "github_actions", "cloudflare_workers")
    ),
    EDGE(
        "EDGE_CHAIN", ApiCapability.EDGE,
        listOf(ChainLink.LOCAL_TOOLCHAIN, "cloudflare_workers", "github_actions", "supabase", "google_cloud_free")
    ),
    ASSET(
        "ASSET_CHAIN", ApiCapability.ASSET,
        listOf(ChainLink.LOCAL_TOOLCHAIN, "huggingface", "fal", "replicate", ChainLink.SKIP)
    );

    /** True when this chain begins locally, which eight of the eleven do. */
    val localFirst: Boolean get() = links.firstOrNull() == ChainLink.LOCAL_TOOLCHAIN

    /** Links that are providers, excluding the terminal pseudo-links below. */
    fun providerLinks(): List<String> = links.filterNot { it in ChainLink.TERMINAL }

    /** Where a provider sits in this chain, or -1. Lower is preferred. */
    fun positionOf(providerId: String): Int = links.indexOf(providerId)

    /**
     * The chain as the document writes it, for a route explanation.
     *
     * Rendering the whole ordering rather than the chosen link is deliberate:
     * Blueprint Phase 3 requires route explanations to show "every selected and
     * skipped provider", and a decision without its alternatives is not an
     * explanation.
     */
    fun render(): String = canonical + "=" + links.joinToString(" -> ")

    companion object {
        private val BY_CANONICAL = entries.associateBy { it.canonical }
        private val BY_CAPABILITY = entries.associateBy { it.capability }

        fun of(canonical: String): FallbackChain? = BY_CANONICAL[canonical.trim().uppercase()]

        /** The chain serving a capability, or null when none does. */
        fun forCapability(capability: ApiCapability): FallbackChain? = BY_CAPABILITY[capability]

        /** Every chain a provider appears in, for an impact view. */
        fun containing(providerId: String): List<FallbackChain> =
            entries.filter { providerId in it.links }

        /** Chains whose declared root is absent from [registered]. */
        fun brokenRoots(registered: Set<String>): List<FallbackChain> =
            entries.filter { chain ->
                val root = chain.links.firstOrNull() ?: return@filter true
                ChainLink.isProvider(root) && root !in registered
            }
    }
}
