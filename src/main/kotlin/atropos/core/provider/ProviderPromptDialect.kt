/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import org.json.JSONObject

/**
 * The textual dialect a provider expects its source context to arrive in.
 *
 * The cascade selects one of these per attempt so the same payload is
 * reshaped to match whichever provider is currently being tried, instead of
 * sending every provider an identical blob.
 */
enum class ProviderPromptDialect {
    /** Plain text, delivered verbatim (fast inference providers). */
    RAW,
    /** XML-tagged context (providers that parse tagged markup best). */
    XML,
    /** JSON-wrapped context (structured/schema-driven providers). */
    JSON;

    fun formatContext(context: String): String = when (this) {
        RAW -> context
        XML -> if (context.isBlank()) context else "<source_context>\n$context\n</source_context>"
        JSON -> if (context.isBlank()) context else JSONObject().put("source_context", context).toString()
    }

    companion object {
        fun forProvider(providerId: String): ProviderPromptDialect = when (providerId.trim().lowercase()) {
            "anthropic", "openai", "github_models" -> XML
            "openrouter", "cloudflare_ai", "gemini" -> JSON
            else -> RAW
        }
    }
}
