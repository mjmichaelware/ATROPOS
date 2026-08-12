package atropos.core.agent

import atropos.core.auditor.AuditSeverity
import atropos.core.auditor.AuditorService
import atropos.core.policy.AgencyDisposition
import java.nio.file.Path

class AgentPatchAuditGate(
    private val repoRoot: Path,
    private val auditorFactory: () -> AuditorService = { AuditorService(repoRoot) }
) {
    fun refuseIfBlocked(snapshot: AgentPatchSnapshot, checkOnly: Boolean): AgentPatchApplyResult? {
        val files = snapshot.extraction.touchedPaths
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { repoRoot.resolve(it).toString() }
            .distinct()
        if (files.isEmpty()) return null

        val auditor = auditorFactory()
        auditor.auditSecretText(snapshot.patchFile.toString(), snapshot.diffText)
        auditor.auditSecrets(files)
        val blocking = auditor.report().findings.filter {
            it.severity == AuditSeverity.FAILURE || it.severity == AuditSeverity.CRITICAL
        }
        if (blocking.isEmpty()) return null

        val reason = "auditor blocked apply: " + blocking.joinToString("; ") { "${it.check} ${it.message}" }
        return AgentPatchApplyResult(
            patchId = snapshot.id,
            patchFile = snapshot.patchFile,
            changedPaths = snapshot.extraction.touchedPaths,
            checkOnly = checkOnly,
            applied = false,
            refusalReason = reason,
            disposition = AgencyDisposition.POLICY_BLOCKED
        )
    }
}
