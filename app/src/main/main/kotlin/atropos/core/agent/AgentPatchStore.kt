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
     * Optional process seam for refusal tests. Production uses the shared
     * bounded process owner, so this store never owns process construction.
     */
    private val spawn: ((List<String>, Path) -> Process)? = null
) {
    private val patchDir = repoRoot.resolve(".atropos/agent/patches").normalize()
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())
    private val metadataWriter = AgentPatchMetadataWriter(patchDir, clock, formatter, redactionFilter)
    private val agencyRunner = AgentPatchAgencyRunner(
        repoRoot = repoRoot,
        agency = agency,
        metadataWriter = metadataWriter,
        processRunner = spawn?.let { seam ->
            atropos.core.policy.BoundedProcessRunner { command, directory, _, _ -> seam(command, directory) }
        } ?: atropos.core.policy.BoundedProcessRunner()
    )
    private val auditGate = AgentPatchAuditGate(repoRoot, auditorFactory)
    private val applyService = AgentPatchApplyService(repoRoot, patchDir, extractor, metadataWriter, territoryGrants, agencyGate, auditGate, agencyRunner)

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
        val patchId = if (reference == "latest") latestPatchId() else reference
        if (patchId == null) return null
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

    fun applyPatch(reference: String, checkOnly: Boolean): AgentPatchApplyResult =
        applyService.applyPatch(reference, checkOnly)

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
}
