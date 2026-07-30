package atropos.core.agent

import atropos.core.artifact.JarSwapResult
import atropos.core.director.DirectorPromotionAdvisory
import atropos.core.security.RedactionFilter
import atropos.core.verification.CompletionGateReport

class SelfHostPromotionEvidence(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val hasher: SelfHostFileHasher = SelfHostFileHasher()
) {
    fun gateReport(report: CompletionGateReport): String =
        redactionFilter.compact(
            buildString {
                append("promotion_gate node=${report.nodeId} canComplete=${report.canComplete}")
                report.gateResults.forEach {
                    append(" | ${it.gateName}=${if (it.passed) "PASS" else "FAIL"}:${it.detail}")
                }
            },
            maxChars = 1800
        )

    fun directorAdvisory(advisory: DirectorPromotionAdvisory): String =
        redactionFilter.compact(
            buildString {
                append("director_pre_promote allowed=${advisory.allowed}")
                append(" message=${advisory.message}")
                advisory.blockingObservations.forEach {
                    append(" | ${it.kind.name}=${it.severity.name}:${it.details}")
                }
            },
            maxChars = 1800
        )

    fun jarSwap(result: JarSwapResult): String =
        redactionFilter.compact(
            buildString {
                append("jar_swap promoted=${result.promoted}")
                append(" terminal=${if (result.promoted) "VERIFIED_COMPLETE" else "UNCHANGED"}")
                append(" candidate=${result.candidateJar.fileName} sha256=${hasher.sha256(result.candidateJar) ?: "missing"}")
                append(" target=${result.targetJar.fileName} sha256=${hasher.sha256(result.targetJar) ?: "missing"}")
                append(" backup=${result.backupJar?.fileName ?: "none"}")
                append(" backupSha256=${result.backupJar?.let { hasher.sha256(it) } ?: "none"}")
                append(" message=${result.message}")
                result.evidence.forEach {
                    append(" | ${it.kind}=${if (it.passed) "PASS" else "FAIL"}:${it.detail}")
                }
            },
            maxChars = 1800
        )
}
