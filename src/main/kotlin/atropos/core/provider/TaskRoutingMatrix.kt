/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Source Doc 2 §.300 §5, the fourteen-row routing table, as data.
 *
 * The document gives it as a table: fourteen task types against First, Second,
 * Third, Fourth, Degraded/Queue and Paid Emergency. In the codebase it existed
 * as `when` branches, which means a change to the table was a change to control
 * flow — and a table expressed as control flow cannot be printed, diffed, or
 * checked against the document it came from.
 *
 * Every degraded column is populated. That column is the one that gets dropped
 * when a table becomes code, because it is the path nobody exercises during
 * development, and dropping it is how a run with all free providers exhausted
 * silently escalates to paid instead of queueing.
 *
 * `paid_emergency` entries are recorded and are not reachable without an
 * explicit unlock. They are here so the route explanation can say what *would*
 * have been next, which Blueprint Phase 3 requires.
 */
enum class RoutedTask(
    val canonical: String,
    val first: String,
    val second: String,
    val third: String,
    val fourth: String,
    val degraded: String,
    val paidEmergency: String?
) {
    CHAT_PROMPT("chat_prompt", "groq", "gemini", "openrouter_free", "github_models", "ollama_background", "openai"),
    FAST_CODE_DRAFT("fast_code_draft", "groq", "openrouter_free_coder", "github_models", "nvidia", "ollama_queue", "anthropic"),
    COMPILE_REPAIR("compile_repair", ChainLink.LOCAL_TOOLCHAIN, "groq", "openrouter_free", "gemini", "queued_localized_repair", "anthropic"),
    ARCHITECTURE_DAG("architecture_dag", "gemini", "groq", "github_models", "openrouter_free", "defer_blueprint", "anthropic"),
    LARGE_SOURCE_DOCS("large_source_docs", ChainLink.LOCAL_TOOLCHAIN, "gemini", "jina", "github_models", "chunk_and_queue", "anthropic"),
    WEB_DOCS_LOOKUP("web_docs_lookup", ChainLink.LOCAL_TOOLCHAIN, "jina", "serpapi", "gemini", "ask_user_for_url", null),
    EMBEDDINGS("embeddings", ChainLink.LOCAL_TOOLCHAIN, "jina", "huggingface", "cloudflare_ai", "store_unresolved", null),
    VECTOR_MEMORY("vector_memory", ChainLink.LOCAL_TOOLCHAIN, "pinecone", "supabase", "local_jsonl", "degrade_to_address_map", null),
    DATABASE_STATE("database_state", ChainLink.LOCAL_TOOLCHAIN, "jsonl_snapshots", "supabase", "firestore", "offline_local_only", null),
    EDGE_WORKER("edge_worker", ChainLink.LOCAL_TOOLCHAIN, "cloudflare_workers", "github_actions", "supabase", "retry_later", "google_cloud_free"),
    REMOTE_COMPILE("remote_compile", ChainLink.LOCAL_TOOLCHAIN, "github_actions", "cloudflare_workers", "manual_ci", "local_only", null),
    ASSET_GENERATION("asset_generation", ChainLink.LOCAL_TOOLCHAIN, "huggingface", "fal", "replicate", "skip_assets", "openai"),
    SCREENSHOT_REVIEW("screenshot_review", ChainLink.LOCAL_TOOLCHAIN, "gemini", "huggingface", "github_models", "ask_user", "openai"),
    SECRET_STORAGE("secret_storage", ChainLink.LOCAL_TOOLCHAIN, "env_vars", "google_cloud_free", "github_actions", "local_only", null);

    /** First through fourth, in order. The free ladder. */
    fun ladder(): List<String> = listOf(first, second, third, fourth)

    /**
     * The ladder, then the degraded position. Paid is deliberately excluded.
     *
     * A caller iterating this cannot fall into a paid provider by looping to
     * the end, which is the mechanical form of "forbid accidental paid calls"
     * from Blueprint Phase 3. Reaching paid requires asking for it by name.
     */
    fun freeRoute(): List<String> = ladder() + degraded

    /** True when this task begins on the local toolchain. */
    val localFirst: Boolean get() = first == ChainLink.LOCAL_TOOLCHAIN

    fun render(): String = buildString {
        append(canonical).append(": ")
        append(ladder().joinToString(" -> "))
        append(" | degraded=").append(degraded)
        paidEmergency?.let { append(" | paid=").append(it) }
    }

    companion object {
        private val BY_CANONICAL = entries.associateBy { it.canonical }

        fun of(canonical: String): RoutedTask? = BY_CANONICAL[canonical.trim().lowercase()]

        /** Rows that begin locally. Eleven of fourteen, by the document's design. */
        fun localFirstTasks(): List<RoutedTask> = entries.filter { it.localFirst }

        /**
         * Rows naming a provider that is not registered.
         *
         * A matrix row pointing at an unregistered provider does not fail
         * loudly; it silently skips to the next column. This is how that
         * becomes visible.
         */
        fun unresolvedProviders(registered: Set<String>): Map<RoutedTask, List<String>> =
            entries.associateWith { task ->
                task.ladder().filter { it != ChainLink.LOCAL_TOOLCHAIN && it !in registered }
            }.filterValues { it.isNotEmpty() }
    }
}
