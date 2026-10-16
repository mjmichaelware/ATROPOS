/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.research

import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind

enum class ResearchChannel {
    ST_MEM,
    LT_MEM,
    DLOI_LAKEHOUSE,
    BOUNDED_WEB,
    PROVIDER_SUGGESTIONS
}

data class ResearchChannelReceipt(
    val channel: ResearchChannel,
    val hit: Boolean,
    val skipReason: String?,
    val findings: List<String>
)

class SoftFailResearchStack(
    private val memoryStore: LocalMemoryStore
) {
    fun executeStack(prompt: PromptArtifact, allowSuggestions: Boolean): List<ResearchChannelReceipt> {
        val receipts = mutableListOf<ResearchChannelReceipt>()

        // 1. ST_MEM (Short-Term Memory)
        receipts.add(
            runCatching {
                val hits = memoryStore.search(prompt.rawPromptText, 5)
                if (hits.isNotEmpty()) {
                    ResearchChannelReceipt(
                        channel = ResearchChannel.ST_MEM,
                        hit = true,
                        skipReason = null,
                        findings = hits.map { it.record.title + ": " + it.record.body.take(120) }
                    )
                } else {
                    ResearchChannelReceipt(
                        channel = ResearchChannel.ST_MEM,
                        hit = false,
                        skipReason = "no short-term memory records matched prompt",
                        findings = emptyList()
                    )
                }
            }.getOrElse { e ->
                ResearchChannelReceipt(
                    channel = ResearchChannel.ST_MEM,
                    hit = false,
                    skipReason = "st_mem channel failure: ${e.message}",
                    findings = emptyList()
                )
            }
        )

        // 2. LT_MEM (Long-Term Memory scoped by User/Repo/Project)
        receipts.add(
            runCatching {
                val hits = memoryStore.findBySubject("project", prompt.projectId, 5)
                if (hits.isNotEmpty()) {
                    ResearchChannelReceipt(
                        channel = ResearchChannel.LT_MEM,
                        hit = true,
                        skipReason = null,
                        findings = hits.map { it.title + ": " + it.body.take(120) }
                    )
                } else {
                    ResearchChannelReceipt(
                        channel = ResearchChannel.LT_MEM,
                        hit = false,
                        skipReason = "no project records found for project ${prompt.projectId}",
                        findings = emptyList()
                    )
                }
            }.getOrElse { e ->
                ResearchChannelReceipt(
                    channel = ResearchChannel.LT_MEM,
                    hit = false,
                    skipReason = "lt_mem channel failure: ${e.message}",
                    findings = emptyList()
                )
            }
        )

        // 3. DLOI_LAKEHOUSE
        receipts.add(
            runCatching {
                val summaries = memoryStore.latestByKind(MemoryKind.SOURCE, 5)
                if (summaries.isNotEmpty()) {
                    ResearchChannelReceipt(
                        channel = ResearchChannel.DLOI_LAKEHOUSE,
                        hit = true,
                        skipReason = null,
                        findings = summaries.map { it.title + ": " + it.body.take(120) }
                    )
                } else {
                    ResearchChannelReceipt(
                        channel = ResearchChannel.DLOI_LAKEHOUSE,
                        hit = false,
                        skipReason = "lakehouse index empty or unpopulated",
                        findings = emptyList()
                    )
                }
            }.getOrElse { e ->
                ResearchChannelReceipt(
                    channel = ResearchChannel.DLOI_LAKEHOUSE,
                    hit = false,
                    skipReason = "dloi lakehouse channel failure: ${e.message}",
                    findings = emptyList()
                )
            }
        )

        // 4. BOUNDED_WEB
        receipts.add(
            runCatching {
                ResearchChannelReceipt(
                    channel = ResearchChannel.BOUNDED_WEB,
                    hit = false,
                    skipReason = "bounded web search offline / air-gapped policy active",
                    findings = emptyList()
                )
            }.getOrElse { e ->
                ResearchChannelReceipt(
                    channel = ResearchChannel.BOUNDED_WEB,
                    hit = false,
                    skipReason = "bounded web channel failure: ${e.message}",
                    findings = emptyList()
                )
            }
        )

        // 5. PROVIDER_SUGGESTIONS
        receipts.add(
            runCatching {
                if (allowSuggestions) {
                    val suggestionFindings = prompt.spans
                        .filter { it.type == SpanType.FEATURE || it.type == SpanType.FUNCTION }
                        .map { "Suggestion for ${it.type.name}: implement ${it.text}" }
                    if (suggestionFindings.isNotEmpty()) {
                        ResearchChannelReceipt(
                            channel = ResearchChannel.PROVIDER_SUGGESTIONS,
                            hit = true,
                            skipReason = null,
                            findings = suggestionFindings
                        )
                    } else {
                        ResearchChannelReceipt(
                            channel = ResearchChannel.PROVIDER_SUGGESTIONS,
                            hit = true,
                            skipReason = null,
                            findings = listOf("Baseline architectural suggestions for prompt ${prompt.h0.take(8)}")
                        )
                    }
                } else {
                    ResearchChannelReceipt(
                        channel = ResearchChannel.PROVIDER_SUGGESTIONS,
                        hit = false,
                        skipReason = "provider suggestions disallowed by caller policy",
                        findings = emptyList()
                    )
                }
            }.getOrElse { e ->
                ResearchChannelReceipt(
                    channel = ResearchChannel.PROVIDER_SUGGESTIONS,
                    hit = false,
                    skipReason = "provider suggestions channel failure: ${e.message}",
                    findings = emptyList()
                )
            }
        )

        return receipts
    }
}
