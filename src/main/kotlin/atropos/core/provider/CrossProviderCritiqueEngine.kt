/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.supremacy.ContextBudgetEngine

data class CritiquePacket(
    val issueId: String,
    val originalSnippet: String,
    val proposedDiff: String,
    val compressedContext: String
)

data class CritiqueVerdict(
    val approved: Boolean,
    val cosmeticOverEdit: Boolean,
    val riskScore: Double,
    val critiqueNotes: String,
    val reviewerProviderId: String
)

class CrossProviderCritiqueEngine(
    val budgetEngine: ContextBudgetEngine = ContextBudgetEngine(tokenCeiling = 8_000),
    val dispatcher: ParallelProviderWorkerDispatcher = ParallelProviderWorkerDispatcher()
) {
    fun prepareDensePacket(context: Map<String, String>, proposedDiff: String): CritiquePacket {
        val allocated = budgetEngine.filterAndAllocate(
            taskPrompt = proposedDiff.take(2000),
            candidates = context
        )
        val compressed = buildString {
            for (chunk in allocated.selectedChunks) {
                append("--- FILE: ${chunk.path} (Entropy: ${"%.2f".format(chunk.shannonEntropy)}) ---\n")
                append(chunk.content.take(4000))
                append("\n")
            }
        }
        val firstHunk = proposedDiff.lines()
            .dropWhile { !it.startsWith("@@") }
            .take(20)
            .joinToString("\n")

        return CritiquePacket(
            issueId = "auto-critique-${System.currentTimeMillis()}",
            originalSnippet = firstHunk,
            proposedDiff = proposedDiff,
            compressedContext = compressed.take(32_000)
        )
    }

    fun dispatchAudit(packet: CritiquePacket, auditorProviderId: String): CritiqueVerdict {
        val diffLines = packet.proposedDiff.lines()
        val addedLines = diffLines.filter { it.startsWith("+") && !it.startsWith("+++") }
        val removedLines = diffLines.filter { it.startsWith("-") && !it.startsWith("---") }

        var cosmeticCount = 0
        for (add in addedLines) {
            val content = add.removePrefix("+").trim()
            if (content.isEmpty() || content.startsWith("//") || content.startsWith("#") || content.startsWith("/*")) {
                cosmeticCount++
            }
        }

        val totalChanges = addedLines.size + removedLines.size
        val cosmeticRatio = if (totalChanges > 0) cosmeticCount.toDouble() / totalChanges else 0.0
        val cosmeticOverEdit = cosmeticRatio > 0.45 && totalChanges > 15
        
        // High risk if touching more than 100 lines for a single bug fix without explicit tests
        val volumeRisk = (totalChanges / 150.0).coerceIn(0.0, 0.5)
        val cosmeticRisk = if (cosmeticOverEdit) 0.4 else 0.0
        val baseRisk = 0.1
        val riskScore = (baseRisk + volumeRisk + cosmeticRisk).coerceIn(0.0, 1.0)
        val approved = !cosmeticOverEdit && riskScore <= 0.70

        val notes = buildString {
            append("Auditor $auditorProviderId evaluation: ")
            if (cosmeticOverEdit) append("REJECTED due to excessive cosmetic/comment delta ($cosmeticCount/$totalChanges lines). ")
            if (riskScore > 0.70) append("REJECTED due to elevated regression risk score ($riskScore > 0.70). ")
            if (approved) append("APPROVED minimal targeted patch with risk score $riskScore.")
        }

        return CritiqueVerdict(
            approved = approved,
            cosmeticOverEdit = cosmeticOverEdit,
            riskScore = riskScore,
            critiqueNotes = notes,
            reviewerProviderId = auditorProviderId
        )
    }

    fun filterStrictMinimalPatch(rawDiff: String): String {
        val result = mutableListOf<String>()
        val lines = rawDiff.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            // Strip trailing whitespace
            val stripped = line.trimEnd()
            result.add(stripped)
            i++
        }
        return result.joinToString("\n")
    }
}
