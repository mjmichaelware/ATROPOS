/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import atropos.core.supremacy.ContextBudgetEngine

enum class ContextBracket {
    RANGE_8K,
    RANGE_32K,
    RANGE_128K,
    RANGE_OVER_128K
}

data class IssueRoutingTarget(
    val issueId: String,
    val tokenEstimate: Int,
    val bracket: ContextBracket,
    val selectedProviderId: String,
    val rationale: String
)

interface QuotaClient {
    fun isQuotaAvailable(providerId: String): Boolean
    fun getQuotaStatus(providerId: String): ProviderQuotaRecord?
}

class DefaultQuotaClient(
    private val repoRoot: Path? = null
) : QuotaClient {
    override fun isQuotaAvailable(providerId: String): Boolean {
        return true
    }

    override fun getQuotaStatus(providerId: String): ProviderQuotaRecord? {
        return null
    }
}

class DynamicIssueRouter(
    val quotaLedger: QuotaClient = DefaultQuotaClient(),
    val contextEngine: ContextBudgetEngine = ContextBudgetEngine()
) {
    private val activeDecisionCache = ConcurrentHashMap<String, IssueRoutingTarget>()

    fun estimateIssueTokens(repoRoot: Path, issueText: String, touchedFiles: List<String>): Int {
        var charCount = issueText.length
        for (relativePath in touchedFiles) {
            try {
                val resolved = repoRoot.resolve(relativePath)
                if (Files.exists(resolved) && Files.isRegularFile(resolved)) {
                    val size = Files.size(resolved)
                    // Bound file sampling to avoid OOM on huge binaries
                    charCount += minOf(size, 2_000_000L).toInt()
                }
            } catch (_: Exception) {
                // Ignore missing or unreadable files during preflight estimate
            }
        }
        val estimatedTokens = (charCount / 4).coerceAtLeast(100)
        return estimatedTokens
    }

    fun resolveBracket(tokenCount: Int): ContextBracket {
        return when {
            tokenCount <= 8_000 -> ContextBracket.RANGE_8K
            tokenCount in 8_001..32_000 -> ContextBracket.RANGE_32K
            tokenCount in 32_001..128_000 -> ContextBracket.RANGE_128K
            else -> ContextBracket.RANGE_OVER_128K
        }
    }

    fun selectNextProvider(
        issueId: String,
        tokenCount: Int,
        availableProviders: List<ProviderDescriptor>
    ): IssueRoutingTarget {
        val bracket = resolveBracket(tokenCount)
        val quotaFiltered = availableProviders.filter { quotaLedger.isQuotaAvailable(it.id) }
        val pool = if (quotaFiltered.isNotEmpty()) quotaFiltered else availableProviders

        val preferredCandidates = when (bracket) {
            ContextBracket.RANGE_8K -> listOf("groq", "cerebras")
            ContextBracket.RANGE_32K -> listOf("deepseek_direct", "deepseek", "anthropic", "claude")
            ContextBracket.RANGE_128K -> listOf("sambanova", "gemini", "gemini_flash")
            ContextBracket.RANGE_OVER_128K -> listOf("gemini", "gemini_pro", "gemini_2_0_flash")
        }

        var selected = pool.firstOrNull { provider ->
            preferredCandidates.any { candidate -> provider.id.lowercase().contains(candidate) }
        }

        if (selected == null) {
            // Context-window fitting fallback
            selected = pool.firstOrNull { it.contextWindowTokens >= tokenCount }
                ?: pool.maxByOrNull { it.contextWindowTokens }
                ?: availableProviders.firstOrNull()
        }

        val providerId = selected?.id ?: "gemini"
        val rationale = "Bracket ${bracket.name} for $tokenCount tokens resolved to provider '$providerId' (quota-checked=${quotaFiltered.isNotEmpty()})"

        val target = IssueRoutingTarget(
            issueId = issueId,
            tokenEstimate = tokenCount,
            bracket = bracket,
            selectedProviderId = providerId,
            rationale = rationale
        )
        activeDecisionCache[issueId] = target
        return target
    }

    fun evictStickyState() {
        activeDecisionCache.clear()
    }
}
