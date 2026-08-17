/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Which model a provider should be asked for.
 *
 * Model names were literals inside each provider's `complete()`. That made a
 * vendor's release schedule into a source change: Groq retired
 * `llama-3.3-70b-versatile`, and an operator with a valid key and twenty-three
 * configured providers got `the model does not exist` with no way to correct it
 * short of editing Kotlin and rebuilding — on a phone that cannot compile the
 * tree. A model is configuration. It belongs where configuration lives.
 *
 * ## Precedence, highest first
 *
 * 1. `ATROPOS_MODEL_<PROVIDER>` — one provider, this run. The override an
 *    operator reaches for when a vendor retires a model at 1am.
 * 2. `.atropos/models/<provider>` — one provider, durably, in the workspace.
 * 3. The catalog default compiled in — a starting point, not a decision.
 *
 * The same shape as key resolution (`explicit > environment > local_file`), so
 * an operator who has learned one has learned both.
 *
 * Every layer is inspectable through [resolveWithSource], because "which model
 * did it actually use" is the first question asked when a provider refuses, and
 * a resolver that cannot answer it just moves the mystery.
 */
class ProviderModelResolver(
    private val env: (String) -> String? = System::getenv,
    private val workspaceFile: (String) -> String? = { provider ->
        runCatching {
            val path = java.nio.file.Path.of(".atropos", "models", provider)
            if (java.nio.file.Files.isRegularFile(path)) {
                java.nio.file.Files.readString(path).trim().ifBlank { null }
            } else {
                null
            }
        }.getOrNull()
    }
) {

    /** Where a resolved model name came from. */
    enum class Source { ENVIRONMENT, WORKSPACE_FILE, CATALOG_DEFAULT }

    data class Resolution(val model: String, val source: Source) {
        fun evidence(provider: String): String =
            "model provider=$provider model=$model source=${source.name.lowercase()}"
    }

    /**
     * @param catalogDefault what the provider was built knowing. Used only when
     *   nothing else answered, and never treated as authoritative — it is the
     *   value most likely to have expired.
     */
    fun resolveWithSource(provider: String, catalogDefault: String): Resolution {
        env(environmentKey(provider))?.trim()?.takeIf(String::isNotBlank)?.let {
            return Resolution(it, Source.ENVIRONMENT)
        }
        workspaceFile(provider)?.let { return Resolution(it, Source.WORKSPACE_FILE) }
        return Resolution(catalogDefault, Source.CATALOG_DEFAULT)
    }

    fun resolve(provider: String, catalogDefault: String): String =
        resolveWithSource(provider, catalogDefault).model

    /** `ATROPOS_MODEL_GROQ`, `ATROPOS_MODEL_OPENAI`, `ATROPOS_MODEL_GOOGLE_CLOUD_FREE`. */
    fun environmentKey(provider: String): String =
        "ATROPOS_MODEL_" + provider.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')

    companion object {
        /** One instance is enough; it holds no per-call state. */
        val DEFAULT = ProviderModelResolver()
    }
}
