package atropos.core.factory

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator
import atropos.core.auditor.AuditorService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.director.DriftSeverity
import atropos.core.director.ObservationKind
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ShellActionProposals
import atropos.core.security.RedactionFilter
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.verification.FactoryCompletionInput
import atropos.core.verification.DeterministicVerifier
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation

data class GeneratedAppProject(
    val path: String,
    val spec: AppProjectSpec,
    val files: List<String>,
    val evidencePath: String,
    val commitId: String,
    val branch: String,
    val treeSha256: String,
    val exportPath: String,
    val planningDagId: String? = null,
    val plannedAtomIds: List<String> = emptyList()
)

class AppProjectGenerator(
    private val repoRoot: Path,
    private val parser: AppProjectSpecParser = AppProjectSpecParser(),
    private val scaffold: RepoScaffold = RepoScaffold(),
    private val mutationGate: AppProjectMutationAuthorizer = AppProjectMutationGate(repoRoot),
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val agencyGate: BoundedAgencyGate = localAgency(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val behaviorGuard: AppGeneratedBehaviorGuard = AppGeneratedBehaviorGuard(),
    private val hierarchyGate: FactoryHierarchyGate = FactoryHierarchyGate()
) {
    fun generateApp(prompt: String, projectId: String): GeneratedAppProject {
        return generateApp(parser.parse(prompt), projectId)
    }

    fun generateApp(
        spec: AppProjectSpec,
        projectId: String,
        planningDagId: String? = null,
        plannedAtomIds: List<String> = emptyList(),
        lineage: FactoryLineage? = null
    ): GeneratedAppProject {
        val effectiveLineage = lineage ?: FactoryLineage.prepare(repoRoot, projectId, spec.prompt, spec)
        // Generated repositories are durable evidence-bearing outputs, not
        // Gradle build products. Keep them in a policy-allowed ATROPOS area.
        val target = targetPath(repoRoot, spec, projectId)
        require(target.startsWith(repoRoot.normalize())) { "app target escaped repository root" }
        val targetExisted = Files.exists(target)
        require(!targetExisted || isEmptyDirectory(target)) { "app target already contains files: $target" }
        val director = DirectorService(DirectorStore(repoRoot), repoRoot)
        val relativeTarget = repoRoot.toAbsolutePath().normalize()
            .relativize(target.toAbsolutePath().normalize())
            .toString()
        director.observe(
            kind = ObservationKind.MEMORY_WATERMARK,
            severity = DriftSeverity.INFO,
            source = "factory",
            details = "factory node=${planningDagId ?: "factory-$projectId"} mutation target proposed for bounded generation",
            files = listOf(relativeTarget)
        )
        val hierarchyLease = hierarchyGate.dispatch(
            projectId = projectId,
            territory = relativeTarget,
            sourceCoordinate = "prompt:${effectiveLineage.promptFingerprint};dag:${planningDagId ?: "factory-$projectId"}",
            capabilities = listOf("app-factory", "code-generation")
        )
        mutationGate.requireAllowed(repoRoot, target)
        Files.createDirectories(target)
        val writtenFiles = mutableListOf<Path>()
        try {
        val files = scaffold.files(spec)
        val lineageFiles = effectiveLineage.projectFiles(planningDagId ?: "factory-$projectId", plannedAtomIds)
        val allFiles = LinkedHashMap(files)
        allFiles.putAll(lineageFiles)
        behaviorGuard.requireRealBehavior(spec, allFiles)
        allFiles.forEach { (relative, content) ->
            val file = target.resolve(relative).normalize()
            require(file.startsWith(target)) { "app file escaped target" }
            Files.createDirectories(file.parent)
            writeAtomic(file, content)
            writtenFiles += file
        }
        target.resolve("verify.sh").toFile().setExecutable(true)
        val verificationOutput = runVerify(target)
        val deterministicReport = DeterministicVerifier(repoRoot = target).verify(absolutePathsFor(target, allFiles.keys))
        check(deterministicReport.passed) {
            "generated deterministic verification failed: ${deterministicReport.render().take(800)}"
        }
        val completeVerificationOutput = buildString {
            appendLine(verificationOutput)
            appendLine(deterministicReport.render())
        }.trimEnd()
        val expectedBranch = branchName(spec, projectId)
        runGit(target, GitWorktreeOperation.INIT)
        runGit(target, GitWorktreeOperation.CHECKOUT_BRANCH, expectedBranch)
        runGit(target, GitWorktreeOperation.ADD_ALL)
        runGit(target, GitWorktreeOperation.COMMIT, "initial app scaffold")
        val initialCommit = runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
        val branch = runGit(target, GitWorktreeOperation.REV_PARSE_BRANCH).trim().ifBlank { "HEAD" }
        check(branch == expectedBranch) { "generated branch isolation failed: expected $expectedBranch, got $branch" }
        val relativePaths = allFiles.keys.toList()
        val absolutePaths = relativePaths.map { target.resolve(it).toAbsolutePath().toString() }
        val auditor = AuditorService(repoRoot)
        auditor.auditSecrets(absolutePaths)
        auditor.auditDeterministic(absolutePaths)
        val auditDecision = auditor.blockPromotion(claimedBy = "factory-generator", auditedBy = "auditor")
        check(auditDecision.allowed) { "factory audit blocked promotion: ${auditDecision.message}" }
        director.observe(ObservationKind.MEMORY_WATERMARK, DriftSeverity.INFO, "factory", "factory source and research prepared", files = relativePaths)
        val hashes = allFiles.keys.associateWith { sha256(target.resolve(it)) }
        val treeSha256 = treeDigest(hashes)
        val gate = VerifiedCompletionGate(repoRoot = repoRoot).evaluateFactory(
            FactoryCompletionInput(
                nodeId = planningDagId ?: "factory-$projectId",
                branch = branch,
                expectedBranch = expectedBranch,
                files = relativePaths,
                verificationOutput = completeVerificationOutput,
                auditorAllowed = auditDecision.allowed,
                promptSha256 = effectiveLineage.promptSha256,
                researchSha256 = effectiveLineage.researchSha256,
                sourceCommitId = initialCommit,
                sourceTreeSha256 = treeSha256
            )
        )
        check(gate.canComplete) { gate.message }
        val evidence = target.resolve(".atropos/evidence/app-manifest.txt")
        Files.createDirectories(evidence.parent)
        val export = target.parent.resolve("${safeName(spec.intent.name)}-${safeProjectId(projectId)}.tar")
        mutationGate.requireAllowed(repoRoot, export)
        Files.writeString(
            evidence,
            EvidenceManifest(
                projectPath = ".",
                commitId = initialCommit,
                branch = branch,
                files = relativePaths,
                verification = "generated-source-and-tests+deterministic",
                exportPath = export.fileName.toString(),
                treeSha256 = treeSha256,
                planningDagId = planningDagId,
                plannedAtomIds = plannedAtomIds,
                verificationOutputSha256 = sha256(completeVerificationOutput.toByteArray(StandardCharsets.UTF_8)),
                promptSha256 = effectiveLineage.promptSha256,
                promptFingerprint = effectiveLineage.promptFingerprint,
                researchSha256 = effectiveLineage.researchSha256,
                auditorDecision = auditDecision.message,
                completionGate = gate.message,
                promptSpans = effectiveLineage.promptSpans,
                researchChannels = effectiveLineage.researchChannels,
                contextHash = effectiveLineage.contextHash
            ).render(hashes),
            StandardCharsets.UTF_8
        )
        runGit(target, GitWorktreeOperation.ADD_ALL)
        runGit(target, GitWorktreeOperation.COMMIT, "app evidence")
        val commit = runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
        runGit(target, GitWorktreeOperation.ARCHIVE, export.toAbsolutePath().toString())
        hierarchyLease.complete("commit=$commit tree_sha256=$treeSha256 evidence=${evidence.fileName}")
        return GeneratedAppProject(target.toString(), spec, relativePaths, evidence.toString(), commit, branch, treeSha256, export.toString(), planningDagId, plannedAtomIds)
        } catch (failure: Throwable) {
            hierarchyLease.fail(failure.message ?: failure.javaClass.simpleName)
            runCatching {
                if (targetExisted) {
                    writtenFiles.asReversed().forEach { file -> Files.deleteIfExists(file) }
                } else {
                    removeGeneratedTarget(target)
                }
            }
                .onFailure(failure::addSuppressed)
            throw failure
        }
    }

    private fun runGit(directory: Path, operation: GitWorktreeOperation, argument: String? = null): String {
        val result = gitRunner.run(operation, directory, argument)
        check(result.exitCode == 0) { "app git command failed: ${operation.name.lowercase()}: ${result.output.take(240)}" }
        return result.output
    }

    private fun runVerify(directory: Path): String {
        val actor = ActionActor.HierarchyNode("factory-worker", "factory-${directory.fileName}")
        val command = listOf("sh", "verify.sh")
        val targetPath = repoRoot.toAbsolutePath().normalize()
            .relativize(directory.resolve("verify.sh").toAbsolutePath().normalize())
            .toString()
        val proposal = ShellActionProposals.forCommand(command, directory, actor)
            .copy(targetPaths = listOf(targetPath))
        val authorization = agencyGate.evaluate(proposal)
        check(authorization.disposition == AgencyDisposition.ALLOWED) {
            "generated verification refused by policy: ${authorization.reason}"
        }
        val bounded = processRunner.run(
            command = command,
            directory = directory,
            timeoutMillis = 900_000L,
            maxOutputBytes = 64 * 1024,
            maxOutputLines = 4_000,
            removeEnvironmentKeys = setOf("KOTLIN_RUNNER")
        )
        val output = redactionFilter.redact(
            listOf(bounded.stdout, bounded.stderr)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trimEnd()
        )
        check(
            bounded.launchError == null &&
                !bounded.timedOut &&
                bounded.exitCode == 0 &&
                !bounded.outputTruncated &&
                output.contains("APP_FACTORY_VERIFY_OK")
        ) {
            val detail = bounded.launchError?.let(redactionFilter::redact)
                ?: output.replace(Regex("\\s+"), " ").trim().take(400)
            "generated app verification failed: $detail"
        }
        return output
    }

    private fun absolutePathsFor(target: Path, relativePaths: Collection<String>): List<Path> =
        relativePaths.map { target.resolve(it).normalize().toAbsolutePath() }

    private fun isEmptyDirectory(path: Path): Boolean = Files.isDirectory(path) && Files.list(path).use { !it.findAny().isPresent }

    private fun removeGeneratedTarget(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun writeAtomic(file: Path, content: String) {
        val temporary = Files.createTempFile(file.parent, ".${file.fileName}", ".tmp")
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun treeDigest(hashes: Map<String, String>): String {
        val canonical = hashes.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun targetPath(repoRoot: Path, spec: AppProjectSpec, projectId: String): Path =
            repoRoot.resolve(".atropos/generated-projects")
                .resolve("${safeName(spec.intent.name)}-${safeProjectId(projectId)}")
                .normalize()

        fun branchName(spec: AppProjectSpec, projectId: String): String =
            "${safeName(spec.intent.name)}-${safeProjectId(projectId)}"

        private fun safeName(value: String): String {
            val normalized = value.replace(Regex("[^A-Za-z0-9_]"), "_").lowercase()
            return if (normalized.firstOrNull()?.isLetter() == true || normalized.firstOrNull() == '_') normalized else "app_$normalized"
        }

        private fun safeProjectId(value: String): String {
            val normalized = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return if (normalized.isBlank()) "project" else normalized
        }

        fun localAgency(repoRoot: Path): BoundedAgencyGate {
            val root = repoRoot.toAbsolutePath().normalize()
            val director = DirectorService(DirectorStore(root), root)
            val territory = TerritoryGrantService(
                service = TerritoryService(TerritoryStore(root), director),
                rootPrefix = ".atropos/generated-projects"
            )
            return BoundedAgencyGate(
                policyEngine = ExecutionPolicyEngine(root),
                territory = territory
            )
        }
    }
}
