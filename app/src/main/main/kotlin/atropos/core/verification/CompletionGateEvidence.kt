package atropos.core.verification

import atropos.core.dag.DagNode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class CompletionGateEvidence(
    private val repoRoot: Path,
    private val clock: () -> Instant
) {
    fun checkAcceptanceEvidence(node: DagNode): GateResult {
        val evidenceDir = repoRoot.resolve("docs/bootstrap")
        val hasBootstrapEvidence = Files.isDirectory(evidenceDir)
        val selfHostEvidence = selfHostEvidenceBundle(node)
        val hasEvidence = hasBootstrapEvidence || selfHostEvidence != null
        return GateResult(node.id, hasEvidence, "Acceptance Evidence", when {
            hasBootstrapEvidence -> "evidence directory exists"
            selfHostEvidence != null -> "self-host evidence bundle exists: $selfHostEvidence"
            else -> "no evidence directory or self-host evidence bundle"
        }, clock())
    }

    fun checkAuditorFindings(node: DagNode, auditorFactory: () -> atropos.core.auditor.AuditorService): GateResult {
        val files = (node.territory + node.expectedOutputs)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { repoRoot.resolve(it).toString() }
            .distinct()

        if (files.isEmpty()) {
            return if ("Auditor Findings" in node.optionalChecks) {
                GateResult(node.id, true, "Auditor Findings", "node named no files for the auditor to review (declared optional by the node contract)", clock())
            } else {
                GateResult(node.id, false, "Auditor Findings", "node named no files for the auditor to review; nothing was verified", clock())
            }
        }

        val auditor = auditorFactory()
        auditor.auditSecrets(files)
        auditor.auditDeterministic(files)
        val decision = auditor.blockPromotion(auditor.report(), claimedBy = node.claimOwner, auditedBy = "auditor")

        return GateResult(
            node.id, decision.allowed, "Auditor Findings",
            if (decision.allowed) {
                "auditor raised no blocking finding across ${files.size} file(s)"
            } else {
                "auditor blocked: " + decision.blockingFindings.joinToString("; ") { "${it.check} ${it.message}" }
            },
            clock()
        )
    }

    private fun selfHostEvidenceBundle(node: DagNode): String? {
        val goalId = inferSelfHostGoalId(node.id) ?: return null
        val evidenceRoot = repoRoot.resolve(".atropos/self-hosting/evidence").normalize()
        val bundleDir = evidenceRoot.resolve(goalId).normalize()
        if (!bundleDir.startsWith(evidenceRoot)) return null
        val markdown = bundleDir.resolve("bundle.md")
        val json = bundleDir.resolve("bundle.json")
        val markdownOk = Files.isRegularFile(markdown) && Files.size(markdown) > 0L
        val jsonOk = Files.isRegularFile(json) && Files.size(json) > 0L
        return if (markdownOk && jsonOk) bundleDir.toString() else null
    }

    private fun inferSelfHostGoalId(nodeId: String): String? {
        if (!nodeId.startsWith("shg-")) return null
        listOf("-identity-probe", "-source-marker-test", "-source-marker").forEach { suffix ->
            if (nodeId.endsWith(suffix)) return nodeId.removeSuffix(suffix)
        }
        val suffix = nodeId.removePrefix("shg-")
        val token = suffix.substringBefore("-")
        if (token.isBlank()) return null
        return "shg-$token"
    }
}
