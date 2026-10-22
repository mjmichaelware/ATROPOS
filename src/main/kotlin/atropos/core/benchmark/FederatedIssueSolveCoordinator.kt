/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.benchmark

import atropos.core.provider.CrossProviderCritiqueEngine
import atropos.core.provider.DynamicIssueRouter
import java.nio.file.Path

enum class AgentRoleType {
    REPO_SCOUT,
    BUG_REPRODUCER,
    PATCH_SYNTHESIZER,
    REGRESSION_AUDITOR
}

data class RoleFederationState(
    val issueId: String,
    val currentRole: AgentRoleType,
    val contextFiles: List<String>,
    val reproducerVerified: Boolean,
    val candidatePatchDiff: String?,
    val auditPassed: Boolean
)

class FederatedIssueSolveCoordinator(
    val router: DynamicIssueRouter = DynamicIssueRouter(),
    val critiqueEngine: CrossProviderCritiqueEngine = CrossProviderCritiqueEngine(),
    val reproducerSynthesizer: ReproducingTestSynthesizer = ReproducingTestSynthesizer(Path.of("."))
) {
    fun stepScout(issueId: String, problemStatement: String): List<String> {
        val tokens = problemStatement.split(Regex("[^a-zA-Z0-9_.-]")).filter { it.contains(".") || it.contains("/") }
        return if (tokens.isNotEmpty()) tokens.distinct() else listOf("src/main/kotlin/Main.kt")
    }

    fun stepReproduce(issueId: String, contextFiles: List<String>, problemStatement: String): String {
        return reproducerSynthesizer.synthesizeMinimalReproducer(issueId, problemStatement, contextFiles)
    }

    fun stepSynthesize(issueId: String, reproducerScript: String, contextFiles: List<String>): String {
        val targetFile = contextFiles.firstOrNull() ?: "target.py"
        val rawDiff = """
            --- a/$targetFile
            +++ b/$targetFile
            @@ -1,3 +1,3 @@
            - # baseline logic
            + # patched logic resolving $issueId
        """.trimIndent()
        return critiqueEngine.filterStrictMinimalPatch(rawDiff)
    }

    fun stepAudit(issueId: String, patchDiff: String): Boolean {
        val packet = critiqueEngine.prepareDensePacket(
            context = mapOf("patch" to patchDiff),
            proposedDiff = patchDiff
        )
        val verdict = critiqueEngine.dispatchAudit(packet, "claude-sonnet")
        return verdict.approved
    }

    fun executeFullPipeline(issueId: String, problemStatement: String): String {
        val scoutedFiles = stepScout(issueId, problemStatement)
        val reproducer = stepReproduce(issueId, scoutedFiles, problemStatement)
        val patch = stepSynthesize(issueId, reproducer, scoutedFiles)
        val auditPassed = stepAudit(issueId, patch)
        if (!auditPassed) {
            throw IllegalStateException("Federated solve audit failed regression critique for $issueId")
        }
        return patch
    }
}
