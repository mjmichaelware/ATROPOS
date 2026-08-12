package atropos.core.agent

import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.PatchActionProposals
import atropos.core.territory.GrantResult
import atropos.core.territory.TerritoryGrantService
import java.nio.file.Files
import java.nio.file.Path

class AgentPatchApplyService(
    private val repoRoot: Path,
    private val patchDir: Path,
    private val extractor: AgentPatchExtractor,
    private val metadataWriter: AgentPatchMetadataWriter,
    private val territoryGrants: TerritoryGrantService,
    private val agencyGate: BoundedAgencyGate,
    private val auditGate: AgentPatchAuditGate,
    private val agencyRunner: AgentPatchAgencyRunner
) {
    fun applyPatch(reference: String, checkOnly: Boolean): AgentPatchApplyResult {
        val resolvedId = resolvePatchId(reference)
        val resolvedPath = resolvedId?.let { patchDir.resolve("$it.diff").normalize() }
        if (resolvedId == null || resolvedPath == null || !resolvedPath.startsWith(patchDir) || !Files.isRegularFile(resolvedPath)) {
            return AgentPatchApplyResult(
                patchId = resolvedId,
                patchFile = resolvedPath,
                checkOnly = checkOnly,
                applied = false,
                refusalReason = "patch not found: ${reference.trim()}"
            )
        }

        val metaFile = patchDir.resolve("$resolvedId.meta").normalize()
        val diffText = Files.readString(resolvedPath, java.nio.charset.StandardCharsets.UTF_8)
        val extraction = extractor.extract(diffText)
            ?: return AgentPatchApplyResult(
                patchId = resolvedId,
                patchFile = resolvedPath,
                checkOnly = checkOnly,
                applied = false,
                refusalReason = "stored patch is not a valid unified diff"
            )
        val snapshot = AgentPatchSnapshot(
            id = resolvedId,
            patchFile = resolvedPath,
            metaFile = metaFile,
            diffText = diffText,
            extraction = extraction
        )

        val patchActor = ActionActor.HierarchyNode(role = "patch", nodeId = snapshot.id)

        val patchFilePath = runCatching { repoRoot.relativize(snapshot.patchFile).toString() }
            .getOrElse { snapshot.patchFile.toString() }
        val grant = territoryGrants.grantToNode(
            dispatcher = ActionActor.HumanOwner,
            node = patchActor,
            requestedPrefixes = snapshot.extraction.touchedPaths + patchFilePath
        )
        if (grant is GrantResult.Refused) {
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = checkOnly,
                applied = false,
                refusalReason = grant.reason,
                disposition = AgencyDisposition.POLICY_BLOCKED,
                proposalId = null
            )
        }

        val mutationProposal = PatchActionProposals.applyStored(
            patchFile = snapshot.patchFile,
            repoRoot = repoRoot,
            touchedPaths = snapshot.extraction.touchedPaths,
            actor = patchActor
        )
        val mutationDecision = agencyGate.evaluate(mutationProposal)
        if (mutationDecision.disposition != AgencyDisposition.ALLOWED) {
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = checkOnly,
                applied = false,
                refusalReason = mutationDecision.reason,
                disposition = mutationDecision.disposition,
                proposalId = mutationProposal.id
            )
        }

        val validationFailure = extractor.validate(snapshot.extraction.diff)
        if (validationFailure != null) {
            val logFile = if (!checkOnly) {
                metadataWriter.writeApplyMeta(
                    snapshot = snapshot,
                    checkOnly = false,
                    checkResult = AgentPatchCheckResult(passed = false, exitCode = 0, output = validationFailure),
                    applyResult = null,
                    refusalReason = validationFailure,
                    changedPaths = snapshot.extraction.touchedPaths
                )
            } else null
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = checkOnly,
                applied = false,
                refusalReason = validationFailure,
                logFile = logFile
            )
        }

        val checkResult = agencyRunner.runGitApplyCheck(snapshot.patchFile)
        if (!checkResult.passed) {
            val logFile = if (!checkOnly) {
                metadataWriter.writeApplyMeta(
                    snapshot = snapshot,
                    checkOnly = false,
                    checkResult = checkResult,
                    applyResult = null,
                    refusalReason = "git apply --check failed: ${checkResult.output}",
                    changedPaths = snapshot.extraction.touchedPaths
                )
            } else null
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = checkOnly,
                applied = false,
                checkResult = checkResult,
                refusalReason = "git apply --check failed: ${checkResult.output}",
                logFile = logFile
            )
        }

        val dirtyTargetStatus = agencyRunner.runGitStatusForPaths(snapshot.extraction.touchedPaths)
        if (dirtyTargetStatus.isNotBlank()) {
            val refusal = "target files have uncommitted changes: $dirtyTargetStatus"
            val logFile = if (!checkOnly) {
                metadataWriter.writeApplyMeta(
                    snapshot = snapshot,
                    checkOnly = false,
                    checkResult = checkResult,
                    applyResult = null,
                    refusalReason = refusal,
                    changedPaths = snapshot.extraction.touchedPaths
                )
            } else null
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = checkOnly,
                applied = false,
                checkResult = checkResult,
                refusalReason = refusal,
                logFile = logFile
            )
        }

        if (checkOnly) {
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = true,
                applied = false,
                checkResult = checkResult
            )
        }

        auditGate.refuseIfBlocked(snapshot, checkOnly = false)?.let { return it }

        val applyResult = agencyRunner.runGitApply(snapshot.patchFile)
        if (!applyResult.passed) {
            val refusal = "git apply failed: ${applyResult.output}"
            val logFile = metadataWriter.writeApplyMeta(
                snapshot = snapshot,
                checkOnly = false,
                checkResult = checkResult,
                applyResult = applyResult,
                refusalReason = refusal,
                changedPaths = snapshot.extraction.touchedPaths
            )
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = false,
                applied = false,
                checkResult = checkResult,
                applyExitCode = applyResult.exitCode,
                applyOutput = applyResult.output,
                refusalReason = refusal,
                logFile = logFile
            )
        }

        val logFile = metadataWriter.writeApplyMeta(
            snapshot = snapshot,
            checkOnly = false,
            checkResult = checkResult,
            applyResult = applyResult,
            refusalReason = null,
            changedPaths = snapshot.extraction.touchedPaths
        )

        return AgentPatchApplyResult(
            patchId = snapshot.id,
            patchFile = snapshot.patchFile,
            changedPaths = snapshot.extraction.touchedPaths,
            checkOnly = false,
            applied = true,
            checkResult = checkResult,
            applyExitCode = applyResult.exitCode,
            applyOutput = applyResult.output,
            logFile = logFile
        )
    }

    private fun resolvePatchId(reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("latest", ignoreCase = true)) return null

        val cleaned = trimmed
            .removeSuffix(".diff")
            .removeSuffix(".meta")
            .trim()

        if (cleaned.isBlank() || cleaned.contains('/') || cleaned.contains('\\')) return null
        return cleaned
    }
}
