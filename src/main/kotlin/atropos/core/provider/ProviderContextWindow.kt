/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Single authoritative lookup for a provider's usable context window.
 *
 * The descriptor registry owns provider metadata; this object translates it
 * into a context-window budget the cascade can rank and fit-check against.
 */
object ProviderContextWindow {
    const val DEFAULT_CONTEXT_WINDOW_TOKENS = 8_192
    private const val CHARS_PER_TOKEN = 4

    fun of(providerId: String, registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()): Int {
        val clean = providerId.trim().lowercase()
        return registry.getById(clean)?.contextWindowTokens?.takeIf { it > 0 }
            ?: DEFAULT_CONTEXT_WINDOW_TOKENS
    }

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        return (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
    }

    fun fits(providerId: String, estimatedTokens: Int, registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()): Boolean {
        return of(providerId, registry) >= estimatedTokens
    }
}
