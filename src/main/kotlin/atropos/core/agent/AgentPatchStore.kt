package atropos.core.agent

import atropos.core.security.RedactionFilter
import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.PatchActionProposals
import atropos.core.policy.ToolExecutionResult
import atropos.core.policy.TypedToolExecutor
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AgentPatchRecord(
    val id: String,
    val provider: String,
    val createdAt: Instant,
    val task: String,
    val contextBytes: Int,
    val diffBytes: Int,
    val patchDir: Path,
    val diffFile: Path,
    val metaFile: Path
)

data class AgentPatchCheckResult(
    val passed: Boolean,
    val exitCode: Int,
    val output: String,
    /**
     * How bounded agency disposed of the proposal. Carried so a refusal is a
     * typed outcome a compositor can act on — an `APPROVAL_REQUIRED` patch is
     * something an operator could still authorise, which a bare exit code
     * cannot express. `null` where no proposal was made.
     */
    val disposition: AgencyDisposition? = null,
    val proposalId: String? = null
) {
    val statusText: String
        get() = if (passed) "OK" else "FAILED"
}

data class AgentPatchSnapshot(
    val id: String,
    val patchFile: Path,
    val metaFile: Path,
    val diffText: String,
    val extraction: AgentPatchExtraction
)

data class AgentPatchApplyResult(
    val patchId: String?,
    val patchFile: Path?,
    val changedPaths: List<String> = emptyList(),
    val checkOnly: Boolean,
    val applied: Boolean,
    val checkResult: AgentPatchCheckResult? = null,
    val verificationResult: AgentVerificationRunResult? = null,
    val applyExitCode: Int? = null,
    val applyOutput: String? = null,
    val refusalReason: String? = null,
    val logFile: Path? = null,
    /** Bounded-agency disposition of the mutation proposal; `null` if none was made. */
    val disposition: AgencyDisposition? = null,
    val proposalId: String? = null
) {
    fun render(): String = buildString {
        val filter = RedactionFilter()
        appendLine("Patch id: ${patchId ?: "none"}")
        appendLine("Patch path: ${patchFile ?: "none"}")
        appendLine("Changed paths: ${changedPaths.joinToString(", ") { filter.redact(it) }.ifBlank { "none" }}")
        if (checkOnly) {
            appendLine(
                if (checkResult?.passed == true && refusalReason.isNullOrBlank()) {
                    "APPLY CHECK OK"
                } else {
                    "APPLY CHECK FAILED: ${filter.redact(refusalReason ?: checkResult?.output ?: "unknown")}"
                }
            )
        } else {
            appendLine(
                if (applied) {
                    "APPLY OK"
                } else {
                    "APPLY REFUSED: ${filter.redact(refusalReason ?: checkResult?.output ?: "unknown")}"
                }
            )
        }
        checkResult?.let {
            appendLine("git apply --check: ${it.statusText}${it.output.takeIf { output -> output.isNotBlank() }?.let { output -> " :: ${filter.redact(output)}" } ?: ""}")
        }
        verificationResult?.let {
            appendLine("verification patch id: ${it.patchId ?: "none"}")
            it.verificationId?.let { id -> appendLine("verification id: $id") }
            it.command?.let { command -> appendLine("verification command: ${filter.redact(command)}") }
            appendLine("verification changed paths: ${it.changedPaths.joinToString(", ") { path -> filter.redact(path) }.ifBlank { "none" }}")
            it.exitCode?.let { exit -> appendLine("verification exit code: $exit") }
            if (it.durationMillis > 0) appendLine("verification duration ms: ${it.durationMillis}")
            appendLine("verification result: ${if (it.passed) "PASSED" else "FAILED"}")
            it.metaFile?.let { meta -> appendLine("verification metadata: $meta") }
            it.refusalReason?.takeIf { reason -> reason.isNotBlank() }?.let { reason -> appendLine("verification refusal reason: ${filter.redact(reason)}") }
        }
        applyExitCode?.let { appendLine("git apply exit code: $it") }
        logFile?.let { appendLine("Apply log: $it") }
        if (applied) {
            val verifyCommand = if (changedPaths.isNotEmpty()) {
                "git diff -- ${changedPaths.joinToString(" ")}"
            } else {
                "git status --short"
            }
            appendLine("Next command to verify: $verifyCommand")
        }
        refusalReason?.takeIf { it.isNotBlank() && !checkOnly }?.let { appendLine("Refusal reason: ${filter.redact(it)}") }
    }.trimEnd()
}

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
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val clock: () -> Instant = { Instant.now() },
    private val extractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
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

    fun writeMeta(record: AgentPatchRecord, check: AgentPatchCheckResult) {
        val content = buildString {
            appendLine("id=${record.id}")
            appendLine("provider=${record.provider}")
            appendLine("createdAt=${record.createdAt}")
            appendLine("task=${redactionFilter.compact(record.task.replace("\n", " ").trim(), 1_000)}")
            appendLine("contextBytes=${record.contextBytes}")
            appendLine("diffBytes=${record.diffBytes}")
            appendLine("gitApplyCheckStatus=${check.statusText}")
            appendLine("gitApplyCheckExitCode=${check.exitCode}")
            appendLine("gitApplyCheckOutput=${redactionFilter.compact(compactOutput(check.output), 2_000)}")
            appendLine("diffFile=${record.diffFile.fileName}")
        }
        Files.writeString(record.metaFile, content, StandardCharsets.UTF_8)
    }

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
        runThroughAgency(PatchActionProposals.applyCheck(diffFile, repoRoot, patchActor(diffFile)))

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

    /**
     * Runs a git proposal, but only if the system authorised it.
     *
     * The executor lambda is the only place a process can be born, and the gate
     * decides whether it is ever invoked.
     */
    private fun runThroughAgency(
        proposal: ActionProposal,
        /** Status reads returned full output before bounded agency; keep it that way. */
        compact: Boolean = true
    ): AgentPatchCheckResult {
        var executed: AgentPatchCheckResult? = null
        val outcome = agency.execute(proposal) {
            val process = spawn(proposal.command, repoRoot)
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            val result = AgentPatchCheckResult(
                passed = exitCode == 0,
                exitCode = exitCode,
                output = if (compact) compactOutput(output) else output,
                disposition = AgencyDisposition.ALLOWED,
                proposalId = proposal.id
            )
            executed = result
            result.output
        }
        return executed ?: refusedCheck(proposal, outcome)
    }

    /**
     * Refusal rendered from the system's decision.
     *
     * `APPROVAL_REQUIRED` keeps its own exit code: it is not a denial, it is a
     * mutation awaiting an authority nobody has asked yet, and collapsing it
     * into the blocked code would erase the difference an approval flow needs.
     */
    private fun refusedCheck(proposal: ActionProposal, outcome: ToolExecutionResult): AgentPatchCheckResult =
        AgentPatchCheckResult(
            passed = false,
            exitCode = when (outcome.disposition) {
                AgencyDisposition.APPROVAL_REQUIRED -> EXIT_APPROVAL_REQUIRED
                else -> EXIT_POLICY_BLOCKED
            },
            output = outcome.refusalReason ?: outcome.policyDecision.reason,
            disposition = outcome.disposition,
            proposalId = proposal.id
        )

    fun normalizeProviderDiff(diffText: String): String {
        val extraction = extractor.extract(diffText) ?: return diffText.trimEnd() + "\n"
        val diff = extraction.diff
        if (!isContextlessAddOnlyPatch(diff, extraction.touchedPaths)) {
            return diff.trimEnd() + "\n"
        }

        val path = extraction.touchedPaths.singleOrNull()?.let(::normalizeRelativePath) ?: return diff.trimEnd() + "\n"
        val target = repoRoot.resolve(path).normalize()
        if (!target.startsWith(repoRoot) || !Files.isRegularFile(target)) {
            return diff.trimEnd() + "\n"
        }

        val addedLines = diff.lineSequence()
            .filter { line -> line.startsWith("+") && !line.startsWith("+++") }
            .map { line -> line.removePrefix("+") }
            .toList()
        if (addedLines.isEmpty()) {
            return diff.trimEnd() + "\n"
        }

        val originalLines = runCatching { Files.readAllLines(target, StandardCharsets.UTF_8) }
            .getOrElse { return diff.trimEnd() + "\n" }

        return buildAppendPatch(path, originalLines, addedLines)
    }

    fun runGitApply(diffFile: Path): AgentPatchCheckResult =
        runThroughAgency(PatchActionProposals.apply(diffFile, repoRoot, patchActor(diffFile)))

    fun runGitStatusForPaths(paths: List<String>): String {
        val cleanPaths = paths.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanPaths.isEmpty()) return ""

        return runThroughAgency(
            PatchActionProposals.statusForPaths(
                cleanPaths,
                repoRoot,
                ActionActor.HierarchyNode(role = "patch", nodeId = "status")
            ),
            compact = false
        ).output
    }

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
        val mutationProposal = PatchActionProposals.applyStored(
            patchFile = snapshot.patchFile,
            repoRoot = repoRoot,
            touchedPaths = snapshot.extraction.touchedPaths,
            actor = ActionActor.HierarchyNode(role = "patch", nodeId = snapshot.id)
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
                writeApplyMeta(
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
                writeApplyMeta(
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
                writeApplyMeta(
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

        val applyResult = runGitApply(snapshot.patchFile)
        if (!applyResult.passed) {
            val refusal = "git apply failed: ${applyResult.output}"
            val logFile = writeApplyMeta(
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

        val logFile = writeApplyMeta(
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

    private fun writeApplyMeta(
        snapshot: AgentPatchSnapshot,
        checkOnly: Boolean,
        checkResult: AgentPatchCheckResult,
        applyResult: AgentPatchCheckResult?,
        refusalReason: String?,
        changedPaths: List<String>
    ): Path {
        Files.createDirectories(patchDir)
        val createdAt = clock()
        val logFile = patchDir.resolve("apply-${formatter.format(createdAt)}-${snapshot.id}.meta")
        val content = buildString {
            appendLine("patchId=${snapshot.id}")
            appendLine("patchFile=${snapshot.patchFile.fileName}")
            appendLine("checkOnly=$checkOnly")
            appendLine("applied=${applyResult?.passed == true && refusalReason == null}")
            appendLine("changedPaths=${changedPaths.joinToString(",")}")
            appendLine("gitApplyCheckStatus=${checkResult.statusText}")
            appendLine("gitApplyCheckExitCode=${checkResult.exitCode}")
            appendLine("gitApplyCheckOutput=${compactOutput(checkResult.output)}")
            appendLine("gitApplyExitCode=${applyResult?.exitCode ?: ""}")
            appendLine("gitApplyOutput=${compactOutput(applyResult?.output.orEmpty())}")
            appendLine("refusalReason=${refusalReason ?: ""}")
        }
        Files.writeString(logFile, content, StandardCharsets.UTF_8)
        return logFile
    }

    private fun compactOutput(raw: String, maxLines: Int = 8, maxChars: Int = 1200): String {
        if (raw.isBlank()) return "no output"
        val lines = raw.lineSequence().take(maxLines).joinToString(" | ").trim()
        return if (lines.length <= maxChars) lines else lines.take(maxChars - 3) + "..."
    }

    private fun isContextlessAddOnlyPatch(diff: String, touchedPaths: List<String>): Boolean {
        if (touchedPaths.size != 1) return false

        var sawHunk = false
        for (line in diff.lineSequence()) {
            when {
                line.startsWith("@@") -> sawHunk = true
                line.startsWith("+") && !line.startsWith("+++") -> continue
                line.startsWith("--- ") || line.startsWith("+++ ") || line.startsWith("diff --git ") -> continue
                line.startsWith("\\ No newline at end of file") -> continue
                line.isBlank() -> continue
                sawHunk -> return false
                else -> return false
            }
        }

        return sawHunk
    }

    private fun normalizeRelativePath(path: String): String =
        path.removePrefix("a/").removePrefix("b/").trim().trim('"').trim('\'')

    private fun buildAppendPatch(
        relativePath: String,
        originalLines: List<String>,
        addedLines: List<String>
    ): String {
        val contextSize = minOf(3, originalLines.size)
        val contextLines = originalLines.takeLast(contextSize)
        val originalStartLine = if (originalLines.isEmpty()) 0 else originalLines.size - contextSize + 1
        val originalCount = contextLines.size
        val newCount = originalCount + addedLines.size

        return buildString {
            appendLine("--- a/$relativePath")
            appendLine("+++ b/$relativePath")
            appendLine("@@ -$originalStartLine,$originalCount +$originalStartLine,$newCount @@")
            contextLines.forEach { appendLine(" $it") }
            addedLines.forEach { appendLine("+$it") }
        }
    }

    private companion object {
        /** Refused by policy. Unchanged from before bounded agency. */
        const val EXIT_POLICY_BLOCKED = 126

        /** Withheld pending an authority that has not been asked. Distinct on purpose. */
        const val EXIT_APPROVAL_REQUIRED = 125
    }
}
