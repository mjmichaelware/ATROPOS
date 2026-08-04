package atropos.core.agent

import atropos.core.verification.CompletionGateReport

/**
 * Validates the promotion contract at the boundary where a completion report
 * becomes swap evidence. The completion gate remains the verification owner;
 * this contract prevents an injected or malformed report from becoming a
 * false promotion.
 */
class SelfHostPromotionGateContract {
    fun refusal(report: CompletionGateReport, expectedNodeId: String): String? {
        if (report.nodeId != expectedNodeId) {
            return "completion report node mismatch: expected=$expectedNodeId observed=${report.nodeId}"
        }
        if (!report.canComplete) {
            return "completion gate reported failure: ${report.message}"
        }
        if (report.gateResults.isEmpty()) {
            return "completion gate supplied no independent gate results"
        }
        if (report.gateResults.any { it.nodeId != expectedNodeId }) {
            return "completion gate contains evidence for another node: expected=$expectedNodeId"
        }
        val failed = report.gateResults.filterNot { it.passed }
        if (failed.isNotEmpty()) {
            return "completion gate contains failed results: ${failed.joinToString(",") { it.gateName }}"
        }
        if (report.gateResults.any { it.gateName.isBlank() || it.detail.isBlank() }) {
            return "completion gate contains an unnamed or unsupported result"
        }
        val unsafeEvidence = report.gateResults
            .flatMap { listOf(it.gateName, it.detail) }
            .firstOrNull { containsUnsafePromotionLanguage(it) }
        if (unsafeEvidence != null) {
            return "completion gate contains unsafe authorization language"
        }
        return null
    }

    private fun containsUnsafePromotionLanguage(value: String): Boolean {
        val normalized = value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return listOf(
            "self approve",
            "self approval",
            "self verify",
            "self verification",
            "approve own",
            "fake success",
            "placeholder green",
            "policy bypass",
            "bypass policy",
            "without verifiedcompletiongate"
        ).any { it in normalized }
    }
}
