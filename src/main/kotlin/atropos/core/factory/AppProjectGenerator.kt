package atropos.core.factory

import atropos.ast.AstSymbolGraph
import atropos.ast.AstSymbolKind
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator
import atropos.core.auditor.AuditorService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.director.DriftSeverity
import atropos.core.director.ObservationKind
import atropos.core.hr.HrRouterAuditStore
import atropos.core.hr.HrRouterService
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
    val plannedAtomIds: List<String> = emptyList(),
    val proposalSha256: String = ""
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
    private val hierarchyGate: FactoryHierarchyGate = FactoryHierarchyGate(
        hrRouter = HrRouterService(auditStore = HrRouterAuditStore(repoRoot))
    )
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
        require(projectId.matches(PROJECT_ID_PATTERN)) {
            "factory project id must contain only portable identifier characters"
        }
        val effectiveLineage = (lineage ?: FactoryLineageFactory.prepare(repoRoot, projectId, spec.prompt, spec)).also {
            it.requireBoundTo(projectId, spec)
        }.let { prepared ->
            if (plannedAtomIds.isNotEmpty() && prepared.atomResearch.isEmpty()) {
                val markers = FactoryResearchService().researchOpenAtoms(
                    atomIds = plannedAtomIds,
                    promptFingerprint = prepared.promptFingerprint,
                    promptSpans = prepared.promptSpans,
                    researchDocumentSha256 = prepared.researchSha256
                )
                prepared.withPlan(
                    planId = planningDagId ?: "factory-$projectId",
                    atomIds = plannedAtomIds,
                    atomResearch = markers
                )
            } else {
                prepared
            }
        }
        // Generated repositories are durable evidence-bearing outputs, not
        // Gradle build products. Keep them in a policy-allowed ATROPOS area.
        val root = repoRoot.toAbsolutePath().normalize()
        val target = targetPath(root, spec, projectId).toAbsolutePath().normalize()
        require(target.startsWith(root)) { "app target escaped repository root" }
        require(!Files.isSymbolicLink(target)) { "app target cannot be a symbolic link" }
        val targetExisted = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
        require(!targetExisted || Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) && isEmptyDirectory(target)) {
            "app target already contains files: $target"
        }
        val director = DirectorService(DirectorStore(repoRoot), repoRoot)
        val relativeTarget = repoRoot.toAbsolutePath().normalize()
            .relativize(target.toAbsolutePath().normalize())
            .toString()
        val lineageCoordinate =
            "prompt:${effectiveLineage.promptFingerprint};spans:${effectiveLineage.promptSpans};dag:${planningDagId ?: "factory-$projectId"}"
        director.observe(
            kind = ObservationKind.MEMORY_WATERMARK,
            severity = DriftSeverity.INFO,
            source = "factory",
            details = "factory node=${planningDagId ?: "factory-$projectId"} mutation target proposed for bounded generation source=$lineageCoordinate",
            files = listOf(relativeTarget),
            goalId = planningDagId ?: "factory-$projectId",
            territoryId = relativeTarget,
            claimId = planningDagId ?: "factory-$projectId",
            worktreePath = target.toString(),
            sourceCoordinates = listOf(lineageCoordinate)
        )
        val hierarchyLease = hierarchyGate.dispatch(
            projectId = projectId,
            territory = relativeTarget,
            sourceCoordinate = lineageCoordinate,
            capabilities = listOf("app-factory", "code-generation")
        )
        try {
        mutationGate.requireAllowed(repoRoot, target)
        Files.createDirectories(target)
        require(!Files.isSymbolicLink(target) && target.toRealPath().startsWith(root.toRealPath())) {
            "app target escaped repository root during creation"
        }
        val files = scaffold.files(spec, effectiveLineage)
        val lineageFiles = effectiveLineage.projectFiles(planningDagId ?: "factory-$projectId", plannedAtomIds)
        // Persist the prompt, requirements, and atom lineage before source
        // emission so every generated file is rooted in an existing project
        // research record inside the same bounded territory.
        val allFiles = LinkedHashMap<String, String>()
        allFiles.putAll(lineageFiles)
        allFiles.putAll(files)
        val proposalSha256 = proposalDigest(allFiles)
        behaviorGuard.requireRealBehavior(spec, allFiles)
        allFiles.forEach { (relative, content) ->
            val file = target.resolve(relative).normalize()
            require(file.startsWith(target)) { "app file escaped target" }
            Files.createDirectories(file.parent)
            writeAtomic(file, content)
        }
        target.resolve("verify.sh").toFile().setExecutable(true)
        val verificationOutput = runVerify(target)
        val deterministicReport = DeterministicVerifier(repoRoot = target).verify(absolutePathsFor(target, allFiles.keys))
        check(deterministicReport.passed) {
            "generated deterministic verification failed: ${deterministicReport.render().take(800)}"
        }
        val astSymbols = AstSymbolGraph(repoRoot = target).build()
        check(astSymbols.any { it.kind != AstSymbolKind.FILE }) {
            "generated AST symbol graph found no source declarations"
        }
        val astVerification = "ast symbol graph: passed=true symbols=${astSymbols.size}"
        val completeVerificationOutput = buildString {
            appendLine(verificationOutput)
            appendLine(deterministicReport.render())
            appendLine(astVerification)
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
        val auditor = AuditorService(target)
        auditor.auditSecrets(absolutePaths)
        auditor.auditDeterministic(absolutePaths)
        val auditDecision = auditor.blockPromotion(claimedBy = "factory-generator", auditedBy = "auditor")
        check(auditDecision.allowed) {
            val findings = auditDecision.blockingFindings.joinToString("; ") {
                "${it.check}:${it.file.orEmpty()}:${it.message}"
            }
            "factory audit blocked promotion: ${auditDecision.message}; findings=$findings"
        }
        director.observe(
            kind = ObservationKind.MEMORY_WATERMARK,
            severity = DriftSeverity.INFO,
            source = "factory",
            details = "factory source and research prepared source=$lineageCoordinate",
            files = relativePaths,
            goalId = planningDagId ?: "factory-$projectId",
            territoryId = relativeTarget,
            claimId = planningDagId ?: "factory-$projectId",
            worktreePath = target.toString(),
            sourceCoordinates = listOf(lineageCoordinate),
            evidencePaths = listOf(target.resolve(".atropos/evidence/app-manifest.txt").toString())
        )
        val hashes = allFiles.keys.associateWith { sha256(target.resolve(it)) }
        val treeSha256 = treeDigest(hashes)
        val directorAdvisory = director.advisoryBeforePromotion(
            goalId = planningDagId ?: "factory-$projectId",
            territoryIds = listOf(relativeTarget),
            files = relativePaths
        )
        check(directorAdvisory.allowed) {
            "factory director blocked promotion: ${directorAdvisory.message}"
        }
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
                promptFingerprint = effectiveLineage.promptFingerprint,
                promptSpans = effectiveLineage.promptSpans,
                sourceCommitId = initialCommit,
                sourceTreeSha256 = treeSha256,
                directorAllowed = directorAdvisory.allowed,
                proposalSha256 = proposalSha256,
                plannedAtomIds = plannedAtomIds,
                atomResearch = effectiveLineage.atomResearch,
                projectRoot = target.toString(),
                factoryTerritory = relativeTarget,
                directorDecision = directorAdvisory.message,
                auditorDecision = auditDecision.message,
                auditorReportSha256 = auditDecision.reportEvidenceSha256
            )
        )
        check(gate.canComplete) { gate.message }
        val evidence = target.resolve(".atropos/evidence/app-manifest.txt")
        Files.createDirectories(evidence.parent)
        val export = target.parent.resolve("${safeName(spec.intent.name)}-${safeProjectId(projectId)}.tar")
        mutationGate.requireAllowed(repoRoot, export)
        val evidenceManifest = EvidenceManifest(
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
                directorDecision = directorAdvisory.message,
                auditorDecision = auditDecision.message,
                auditorReportSha256 = auditDecision.reportEvidenceSha256,
                completionGate = gate.message,
                promptSpans = effectiveLineage.promptSpans,
                researchChannels = effectiveLineage.researchChannels,
                contextHash = effectiveLineage.contextHash,
                atomResearch = effectiveLineage.atomResearch,
                memoryPointers = effectiveLineage.memoryPointers,
                atomizerStatus = effectiveLineage.atomizerStatus,
                journalRunId = projectId,
                hrRouterRequestId = hierarchyLease.hrRequestId,
                hrRouterAction = hierarchyLease.hrAction,
                proposalSha256 = proposalSha256,
                clarificationAnswersSha256 = effectiveLineage.clarificationAnswersSha256,
                clarificationLineageSha256 = effectiveLineage.clarificationLineageSha256
            )
        evidenceManifest.requireComplete(hashes)
        writeAtomic(evidence, evidenceManifest.render(hashes))
        runGit(target, GitWorktreeOperation.ADD_ALL)
        runGit(target, GitWorktreeOperation.COMMIT, "app evidence")
        val commit = runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
        runGit(target, GitWorktreeOperation.ARCHIVE, export.toAbsolutePath().toString())
        hierarchyGate.completeAfterVerification(
            hierarchyLease,
            "commit=$commit tree_sha256=$treeSha256 evidence=${evidence.fileName}",
            gate
        )
        return GeneratedAppProject(
            target.toString(),
            spec,
            relativePaths,
            evidence.toString(),
            commit,
            branch,
            treeSha256,
            export.toString(),
            planningDagId,
            plannedAtomIds,
            proposalSha256
        )
        } catch (failure: Throwable) {
            hierarchyLease.fail(failure.message ?: failure.javaClass.simpleName)
            runCatching {
                if (targetExisted) {
                    removeGeneratedContents(target)
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
            removeEnvironmentKeys = setOf("KOTLIN_RUNNER"),
            evidenceDirectory = directory.resolve(".atropos/evidence/build")
        )
        val output = redactionFilter.redact(
            listOf(bounded.stdout, bounded.stderr)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trimEnd()
        )
        val proofTail = redactionFilter.redact(
            listOf(bounded.stdoutTail, bounded.stderrTail)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        )
        val marker = "APP_FACTORY_VERIFY_OK"
        val markerInEvidence = containsMarker(bounded.stdoutLogPath, marker) ||
            containsMarker(bounded.stderrLogPath, marker)
        check(
            bounded.launchError == null &&
                !bounded.timedOut &&
                bounded.exitCode == 0 &&
                (output.contains(marker) || proofTail.contains(marker) || markerInEvidence)
        ) {
            val detail = bounded.launchError?.let(redactionFilter::redact)
                ?: (output + "\n" + proofTail).replace(Regex("\\s+"), " ").trim().take(400)
            "generated app verification failed: $detail"
        }
        return buildString {
            if (output.isNotBlank()) appendLine(output)
            appendLine(marker)
            appendLine("verification_output_bytes=${bounded.totalOutputBytes}")
            appendLine("verification_output_lines=${bounded.totalOutputLines}")
            appendLine("verification_output_truncated=${bounded.outputTruncated}")
            appendLine("verification_output_sha256=${bounded.outputSha256 ?: "unavailable"}")
            bounded.stdoutLogPath?.let { appendLine("verification_stdout_log=${it.fileName}") }
            bounded.stderrLogPath?.let { appendLine("verification_stderr_log=${it.fileName}") }
        }.trimEnd()
    }

    private fun containsMarker(path: Path?, marker: String): Boolean {
        if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false
        val needle = marker.toByteArray(StandardCharsets.UTF_8)
        if (needle.isEmpty()) return true
        var matched = 0
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return false
                for (index in 0 until count) {
                    if (buffer[index] == needle[matched]) {
                        matched++
                        if (matched == needle.size) return true
                    } else {
                        matched = if (buffer[index] == needle[0]) 1 else 0
                    }
                }
            }
        }
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

    private fun removeGeneratedContents(path: Path) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return
        val entries = mutableListOf<Path>()
        Files.list(path).use { stream -> stream.forEach(entries::add) }
        entries.forEach(::removeGeneratedTarget)
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

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun treeDigest(hashes: Map<String, String>): String {
        val canonical = hashes.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun proposalDigest(files: Map<String, String>): String {
        val canonical = files.toSortedMap().entries.joinToString("\n") { "${it.key}\u0000${it.value}" }
        return sha256(canonical.toByteArray(StandardCharsets.UTF_8))
    }

    companion object {
        fun targetPath(repoRoot: Path, spec: AppProjectSpec, projectId: String): Path =
            repoRoot.resolve(".atropos/generated-projects")
                .resolve("${safeName(spec.intent.name)}-${safeProjectId(projectId)}")
                .normalize()

        fun branchName(spec: AppProjectSpec, projectId: String): String =
            "${safeName(spec.intent.name)}-${safeProjectId(projectId)}"

        internal fun safeName(value: String): String {
            val normalized = value.replace(Regex("[^A-Za-z0-9_]"), "_").lowercase()
            if (normalized.isBlank() || normalized.all { it == '_' }) return "app"
            val startsAsIdentifier = normalized.firstOrNull()?.isLetter() == true || normalized.firstOrNull() == '_'
            return if (startsAsIdentifier && normalized !in KOTLIN_KEYWORDS) normalized else "app_$normalized"
        }

        private fun safeProjectId(value: String): String {
            val normalized = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return if (normalized.isBlank()) "project" else normalized
        }

        private val KOTLIN_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
            "in", "interface", "is", "null", "object", "package", "return", "super", "this",
            "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
            "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
            "get", "import", "init", "param", "property", "receiver", "set", "setparam",
            "where", "actual", "abstract", "annotation", "companion", "const", "crossinline",
            "data", "enum", "expect", "external", "final", "infix", "inline", "inner", "internal",
            "lateinit", "noinline", "open", "operator", "out", "override", "private", "protected",
            "public", "reified", "sealed", "suspend", "tailrec", "vararg"
        )

        private val PROJECT_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

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
