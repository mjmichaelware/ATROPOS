package atropos.core.factory

import atropos.ast.AstSymbolGraph
import atropos.ast.AstSymbolKind
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
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
    private val gitHelper = AppGitHelper(gitRunner)
    private val verificationHelper = AppVerificationHelper(repoRoot, agencyGate, processRunner, redactionFilter)

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
        val root = repoRoot.toAbsolutePath().normalize()
        val target = freeTargetPath(root, spec, projectId).toAbsolutePath().normalize()
        require(target.startsWith(root)) { "app target escaped repository root" }
        require(!Files.isSymbolicLink(target)) { "app target cannot be a symbolic link" }
        val targetExisted = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
        require(!targetExisted || Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) && AppFileHelper.isEmptyDirectory(target)) {
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
            val allFiles = LinkedHashMap<String, String>()
            allFiles.putAll(lineageFiles)
            allFiles.putAll(files)
            val proposalSha256 = AppFileHelper.proposalDigest(allFiles)
            behaviorGuard.requireRealBehavior(spec, allFiles)
            allFiles.forEach { (relative, content) ->
                val file = target.resolve(relative).normalize()
                require(file.startsWith(target)) { "app file escaped target" }
                Files.createDirectories(file.parent)
                AppFileHelper.writeAtomic(file, content)
            }
            target.resolve("verify.sh").toFile().setExecutable(true)
            val verificationOutput = verificationHelper.runVerify(target)
            val deterministicReport = DeterministicVerifier(repoRoot = target).verify(AppFileHelper.absolutePathsFor(target, allFiles.keys))
            check(deterministicReport.passed) {
                "generated deterministic verification failed: ${deterministicReport.render().take(800)}"
            }
            val astSymbols = AstSymbolGraph(repoRoot = target).buildAndPersist()
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
            gitHelper.runGit(target, GitWorktreeOperation.INIT)
            gitHelper.runGit(target, GitWorktreeOperation.CHECKOUT_BRANCH, expectedBranch)
            gitHelper.runGit(target, GitWorktreeOperation.ADD_ALL)
            gitHelper.runGit(target, GitWorktreeOperation.COMMIT, "initial app scaffold")
            val initialCommit = gitHelper.runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
            val branch = gitHelper.runGit(target, GitWorktreeOperation.REV_PARSE_BRANCH).trim().ifBlank { "HEAD" }
            check(branch == expectedBranch) { "generated branch isolation failed: expected $expectedBranch, got $branch" }
            val relativePaths = allFiles.keys.toList()
            val auditor = AuditorService(target)
            auditor.auditSecrets(relativePaths.map { target.resolve(it).toAbsolutePath().toString() })
            auditor.auditDeterministic(relativePaths.map { target.resolve(it).toAbsolutePath().toString() })
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
            val hashes = relativePaths.associateWith { AppFileHelper.sha256(target.resolve(it)) }
            val treeSha256 = AppFileHelper.treeDigest(hashes)
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
                verificationOutputSha256 = AppFileHelper.sha256(completeVerificationOutput.toByteArray(StandardCharsets.UTF_8)),
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
            AppFileHelper.writeAtomic(evidence, evidenceManifest.render(hashes))
            gitHelper.runGit(target, GitWorktreeOperation.ADD_ALL)
            gitHelper.runGit(target, GitWorktreeOperation.COMMIT, "app evidence")
            val commit = gitHelper.runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
            gitHelper.runGit(target, GitWorktreeOperation.ARCHIVE, export.toAbsolutePath().toString())
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
                    AppFileHelper.removeGeneratedContents(target)
                } else {
                    AppFileHelper.removeGeneratedTarget(target)
                }
            }
                .onFailure(failure::addSuppressed)
            throw failure
        }
    }

    /**
     * The first unused generation directory for this prompt.
     *
     * [targetPath] is derived from the prompt fingerprint, so the same prompt
     * always names the same directory — which is correct, because the
     * fingerprint identifies the prompt. It is not correct as a *run*
     * identity: a second run of one prompt found the first run's output and
     * refused, so a prompt could be generated exactly once and re-running
     * after a fix was impossible. Iterating on a failure is the ordinary case,
     * not an exceptional one.
     *
     * Later runs take a numeric suffix rather than overwriting. Overwriting
     * would destroy the evidence bundle, the commit and the export of the run
     * before it, which are the things you go back to when comparing what
     * changed between attempts.
     */
    private fun freeTargetPath(repoRoot: Path, spec: AppProjectSpec, projectId: String): Path {
        val first = targetPath(repoRoot, spec, projectId)
        if (isFree(first)) return first
        for (attempt in 2..MAX_GENERATION_ATTEMPTS) {
            val candidate = first.resolveSibling("${first.fileName}-$attempt")
            if (isFree(candidate)) return candidate
        }
        // Falling back to the first path lets the existing require() produce
        // the original refusal, which names the directory. A new message here
        // would say the same thing less clearly.
        return first
    }

    private fun isFree(path: Path): Boolean =
        !Files.exists(path, LinkOption.NOFOLLOW_LINKS) ||
            (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && AppFileHelper.isEmptyDirectory(path))

    companion object {
        /**
         * How many times one prompt may be generated before the directory is
         * treated as contested. High enough for real iteration, low enough
         * that a runaway loop does not fill the device.
         */
        const val MAX_GENERATION_ATTEMPTS = 50

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
            val usable = startsAsIdentifier &&
                normalized !in KOTLIN_KEYWORDS &&
                normalized !in RESERVED_PACKAGE_ROOTS
            return if (usable) normalized else "app_$normalized"
        }

        private fun safeProjectId(value: String): String {
            val normalized = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return if (normalized.isBlank()) "project" else normalized
        }

        /**
         * Package roots the toolchain owns, which are not keywords.
         *
         * A prompt beginning "Build a Kotlin HTTP client" derives the app name
         * from its first meaningful word, so the generated file declared
         * `package kotlin` and kotlinc refused it: "only the Kotlin standard
         * library is allowed to use the kotlin package". The keyword list did
         * not catch it because `kotlin` is a perfectly ordinary identifier --
         * it is reserved by the *toolchain*, not by the grammar, and those are
         * two different lists.
         *
         * `java` and `javax` fail the same way on the JVM, and `kotlinx` is
         * owned by the Kotlin libraries even though nothing rejects it today;
         * generating into someone else's namespace is wrong before it is an
         * error.
         */
        private val RESERVED_PACKAGE_ROOTS = setOf(
            "kotlin", "kotlinx", "java", "javax", "jdk", "sun", "android", "androidx"
        )

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
