package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.auditor.AuditorService
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.PatchActionProposals
import atropos.core.policy.TypedToolExecutor
import atropos.core.security.RedactionFilter
import atropos.core.territory.GrantResult
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stores and applies provider-authored patches.
 *
 * This store holds no execution authority. Every patch inspection, mutation and
 * status read is stated as an [ActionProposal] and handed to
 * [TypedToolExecutor]; the store never asks the policy engine anything itself.
 * That matters more here than anywhere else in the tree: a shell command is
 * typed by the operator, but a diff is written by a model, so this is the site
 * where "never execute raw provider prose" is actually enforced.
 */
class AgentPatchStore(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val clock: () -> Instant = { Instant.now() },
    private val extractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val diffNormalizer: AgentPatchDiffNormalizer = AgentPatchDiffNormalizer(repoRoot, extractor),
    private val territoryGrants: TerritoryGrantService =
        TerritoryGrantService(TerritoryService(TerritoryStore(repoRoot))),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot), territoryGrants),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    /**
     * A fresh auditor per apply. [AuditorService] accumulates findings in
     * mutable state, so a shared instance would let one patch's findings refuse
     * another's.
     */
    private val auditorFactory: () -> AuditorService = { AuditorService(repoRoot) },
    /** Shares [agencyGate], so exactly one policy engine serves this store. */
    private val agency: TypedToolExecutor = TypedToolExecutor(agencyGate),
    /**
     * Process spawn seam. Exists so a test can prove a refused proposal never
     * reaches a real [ProcessBuilder]; production always uses the default.
     */
    private val spawn: (List<String>, Path) -> Process = { command, directory ->
        ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start()
    }
) {
    private val patchDir = repoRoot.resolve(".atropos/agent/patches").normalize()
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())
    private val metadataWriter = AgentPatchMetadataWriter(patchDir, clock, formatter, redactionFilter)
    private val agencyRunner = AgentPatchAgencyRunner(repoRoot, agency, metadataWriter, spawn)
    private val auditGate = AgentPatchAuditGate(repoRoot, auditorFactory)

    fun createRecord(provider: String, task: String, contextBytes: Int, diff: String): AgentPatchRecord {
        Files.createDirectories(patchDir)
        val createdAt = clock()
        val id = nextPatchId(createdAt, provider)
        val diffFile = patchDir.resolve("$id.diff")
        val metaFile = patchDir.resolve("$id.meta")
        val renderedDiff = diff.trimEnd() + "\n"
        require(!redactionFilter.report(renderedDiff).changed) {
            "patch diff contains secret-bearing content and was refused before persistence"
        }
        Files.writeString(diffFile, renderedDiff, StandardCharsets.UTF_8)
        return AgentPatchRecord(
            id = id,
            provider = provider,
            createdAt = createdAt,
            task = redactionFilter.redact(task.trim()).take(8_000),
            contextBytes = contextBytes,
            diffBytes = renderedDiff.toByteArray(StandardCharsets.UTF_8).size,
            patchDir = patchDir,
            diffFile = diffFile,
            metaFile = metaFile
        )
    }

    fun writeMeta(record: AgentPatchRecord, check: AgentPatchCheckResult) =
        metadataWriter.writeMeta(record, check)

    fun latestPatchId(): String? {
        if (!Files.isDirectory(patchDir)) return null
        return try {
            Files.list(patchDir).use { stream ->
                stream
                    .map { it.fileName.toString() }
                    .filter { it.endsWith(".diff") }
                    .map { it.removeSuffix(".diff") }
                    .sorted()
                    .reduce { _, current -> current }
                    .orElse(null)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun patchDirectory(): Path = patchDir

    fun resolvePatchSnapshot(reference: String): AgentPatchSnapshot? {
        val patchId = resolvePatchId(reference) ?: return null
        val diffFile = patchDir.resolve("$patchId.diff").normalize()
        if (!diffFile.startsWith(patchDir) || !Files.isRegularFile(diffFile)) return null

        val metaFile = patchDir.resolve("$patchId.meta").normalize()
        val diffText = Files.readString(diffFile, StandardCharsets.UTF_8)
        val extraction = extractor.extract(diffText) ?: return null
        return AgentPatchSnapshot(
            id = patchId,
            patchFile = diffFile,
            metaFile = metaFile,
            diffText = diffText,
            extraction = extraction
        )
    }

    fun runGitApplyCheck(diffFile: Path): AgentPatchCheckResult =
        agencyRunner.runGitApplyCheck(diffFile)

    /**
     * The actor for patch work is the patch itself: it is model-authored, and
     * the patch id is the only identifier that actually exists at every call
     * site. It is carried in the diff's filename, `<id>.diff`.
     */
    private fun patchActor(diffFile: Path): ActionActor =
        ActionActor.HierarchyNode(
            role = "patch",
            nodeId = diffFile.fileName.toString().removeSuffix(".diff")
        )

    fun normalizeProviderDiff(diffText: String): String = diffNormalizer.normalize(diffText)

    fun runGitApply(diffFile: Path): AgentPatchCheckResult =
        agencyRunner.runGitApply(diffFile)

    fun runGitStatusForPaths(paths: List<String>): String = agencyRunner.runGitStatusForPaths(paths)

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
        val diffText = Files.readString(resolvedPath, StandardCharsets.UTF_8)
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

        // Pre-authorisation: the gate judges the mutation's blast radius before
        // anything is read, checked, or written.
        val patchActor = ActionActor.HierarchyNode(role = "patch", nodeId = snapshot.id)

        // Grant-on-dispatch: applying a stored patch is dispatched by the
        // operator, so the patch is granted exactly the paths its diff touches,
        // narrowed from the owner's territory and bound to this patch id.
        // The apply writes the touched paths and reads the stored diff, so the
        // grant must cover both. Granting only the touched paths left the
        // apply-check proposal — which declares the diff file as its target —
        // refused by its own territory.
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

        val checkResult = runGitApplyCheck(snapshot.patchFile)
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

        val dirtyTargetStatus = runGitStatusForPaths(snapshot.extraction.touchedPaths)
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

        // The Auditor reviews what the patch would put in the tree, immediately
        // before the mutation. Territory, policy and diff validation have all
        // already allowed this apply; the Auditor can only subtract from that,
        // never grant it. A check-only run mutates nothing and is not audited.
        auditGate.refuseIfBlocked(snapshot, checkOnly = false)?.let { return it }

        val applyResult = runGitApply(snapshot.patchFile)
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

    private fun nextPatchId(createdAt: Instant, provider: String): String {
        val timestamp = formatter.format(createdAt)
        val providerSlug = provider.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        var candidate = "patch-$timestamp-$providerSlug"
        var suffix = 2
        while (Files.exists(patchDir.resolve("$candidate.diff")) || Files.exists(patchDir.resolve("$candidate.meta"))) {
            candidate = "patch-$timestamp-$providerSlug-$suffix"
            suffix++
        }
        return candidate
    }

    private fun resolvePatchId(reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("latest", ignoreCase = true)) return latestPatchId()

        val cleaned = trimmed
            .removeSuffix(".diff")
            .removeSuffix(".meta")
            .trim()

        if (cleaned.isBlank() || cleaned.contains('/') || cleaned.contains('\\')) return null
        return cleaned
    }

}
