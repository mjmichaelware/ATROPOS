/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.research

import atropos.core.confidence.ConfidenceAction
import atropos.core.confidence.ConfidenceCalculator
import atropos.core.confidence.HysteresisConfidenceEngine
import atropos.core.confidence.ParetoConfidence
import java.security.MessageDigest

data class ResearchDocument(
    val hDoc: String,
    val h0: String,
    val receipts: List<ResearchChannelReceipt>,
    val confidence: ParetoConfidence
)

class ResearchOrchestrator(
    private val stack: SoftFailResearchStack
) {
    val hysteresisEngine: HysteresisConfidenceEngine = HysteresisConfidenceEngine()

    fun research(prompt: PromptArtifact): ResearchDocument {
        val receipts = stack.executeStack(prompt, allowSuggestions = true)
        val confidence = ConfidenceCalculator.calculate(prompt, receipts)

        val canonicalJson = canonicalSerialize(prompt.h0, receipts, confidence)
        val hDoc = sha256(canonicalJson)

        return ResearchDocument(
            hDoc = hDoc,
            h0 = prompt.h0,
            receipts = receipts,
            confidence = confidence
        )
    }

    fun evaluateAction(doc: ResearchDocument): ConfidenceAction = hysteresisEngine.evaluate(doc)

    private fun canonicalSerialize(
        h0: String,
        receipts: List<ResearchChannelReceipt>,
        confidence: ParetoConfidence
    ): String {
        val receiptsJson = receipts.joinToString(",") { r ->
            val findingsJson = r.findings.joinToString(",") { "\"${escape(it)}\"" }
            val skip = if (r.skipReason == null) "null" else "\"${escape(r.skipReason)}\""
            """{"channel":"${r.channel.name}","hit":${r.hit},"skipReason":$skip,"findings":[$findingsJson]}"""
        }
        val confJson = """{"clarity":${confidence.clarity},"surfaceCoverage":${confidence.surfaceCoverage},"knownOwnerDensity":${confidence.knownOwnerDensity},"openCriticalGaps":${confidence.openCriticalGaps},"score":${confidence.score}}"""
        return """{"h0":"${escape(h0)}","receipts":[$receiptsJson],"confidence":$confJson}"""
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
