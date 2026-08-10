package atropos.core.verification

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.AgentRunService
import atropos.core.auditor.AuditorService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.factory.FactoryLineage
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import atropos.core.security.SourceSecretScanner
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

data class GateResult(
    val nodeId: String,
    val passed: Boolean,
    val gateName: String,
    val detail: String,
    val timestamp: Instant
)

data class CompletionGateReport(
    val nodeId: String,
    val canComplete: Boolean,
    val gateResults: List<GateResult>,
    val message: String
)

class VerifiedCompletionGate(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val dagStore: DagStore = DagStore(repoRoot),
    private val runService: AgentRunService = AgentRunService(config),
    private val memoryStore: LocalMemoryStore? = runCatching {
        LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
    }.getOrNull(),
    private val auditorFactory: () -> atropos.core.auditor.AuditorService = { atropos.core.auditor.AuditorService(repoRoot) },
    private val clock: () -> Instant = { Instant.now() },
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val sourceSecretScanner = SourceSecretScanner(redactionFilter)
    private val checks = CompletionGateChecks(repoRoot, clock, processRunner, gitRunner, redactionFilter, sourceSecretScanner)
    private val evidence = CompletionGateEvidence(repoRoot, clock)

    fun evaluateNode(node: DagNode): CompletionGateReport {
        return IndependentVerificationGate(config, repoRoot, processRunner).verify(node)
    }

    fun evaluateFactory(input: FactoryCompletionInput): CompletionGateReport {
        val required = setOf("README.md", "LICENSE", ".gitignore", "AGENTS.md")
        val sourceFiles = input.files.filter { it.startsWith("src/main/") && it.endsWith(".kt") }
        val testFiles = input.files.filter { it.startsWith("src/test/") && it.endsWith(".kt") }
        val hasClassifiedPromptSpans = input.promptSpans != "none" && input.promptSpans.split(';').all {
            it.matches(Regex("[^@;]+@[0-9]+-[0-9]+\\|class=[a-z-]+"))
        }
        val atomResearchIsComplete = FactoryLineage.markersCover(
            input.plannedAtomIds,
            input.atomResearch,
            input.promptFingerprint,
            input.promptSpans,
            input.researchSha256
        )
        val surfaceAudit = auditFactorySurface(input)
        val checkResults = listOf(
            GateResult(input.nodeId, input.branch == input.expectedBranch, "Factory branch isolation", "branch=${input.branch}", clock()),
            GateResult(input.nodeId, surfaceAudit.territoryValid, "Factory territory", surfaceAudit.detail, clock()),
            GateResult(input.nodeId, surfaceAudit.lineageValid, "Factory lineage binding", surfaceAudit.detail, clock()),
            GateResult(input.nodeId, surfaceAudit.integrityValid, "Factory source integrity", surfaceAudit.detail, clock()),
            GateResult(input.nodeId, sourceFiles.isNotEmpty(), "Factory source", "Kotlin source files present", clock()),
            GateResult(input.nodeId, testFiles.isNotEmpty(), "Factory tests", "Kotlin test files present", clock()),
            GateResult(input.nodeId, required.all(input.files::contains), "Factory repository kit", "standard files present", clock()),
            GateResult(input.nodeId, input.files.contains("verify.sh"), "Factory verifier", "bounded verifier present", clock()),
            GateResult(
                input.nodeId,
                input.verificationOutput.contains("APP_FACTORY_VERIFY_OK") &&
                    input.verificationOutput.contains("deterministic verifier:") &&
                    input.verificationOutput.contains("passed: true"),
                "Factory verification",
                "generated tests and deterministic checks passed",
                clock()
            ),
            GateResult(
                input.nodeId,
                input.auditorAllowed &&
                    input.auditorDecision == surfaceAudit.auditorDecision &&
                    input.auditorReportSha256 == surfaceAudit.auditorReportSha256 &&
                    surfaceAudit.auditorAllowed,
                "Factory auditor",
                surfaceAudit.auditorDecision,
                clock()
            ),
            GateResult(
                input.nodeId,
                input.directorAllowed &&
                    input.directorDecision == surfaceAudit.directorDecision &&
                    surfaceAudit.directorAllowed,
                "Factory director",
                surfaceAudit.directorDecision,
                clock()
            ),
            GateResult(
                input.nodeId,
                input.sourceCommitId.matches(Regex("[0-9a-f]{40}")) && sourceCommitExists(input),
                "Factory source commit",
                "source commit exists in generated repository",
                clock()
            ),
            GateResult(input.nodeId, input.sourceTreeSha256.matches(Regex("[0-9a-f]{64}")), "Factory source digest", "source tree digest recorded", clock()),
            GateResult(input.nodeId, input.proposalSha256.matches(Regex("[0-9a-f]{64}")), "Factory source proposal", "authorized proposal digest recorded", clock()),
            GateResult(
                input.nodeId,
                    input.promptSha256.matches(Regex("[0-9a-f]{64}")) &&
                    input.researchSha256.matches(Regex("[0-9a-f]{64}")) &&
                    input.promptFingerprint.matches(Regex("prompt-[0-9a-f]{16}")) &&
                    hasClassifiedPromptSpans,
                "Factory lineage",
                "prompt hashes, fingerprint, and spans present",
                clock()
            ),
            GateResult(
                input.nodeId,
                listOf(
                    ".atropos/research/user-prompt.md",
                    ".atropos/research/requirements.md",
                    ".atropos/research/atoms.md"
                ).all(input.files::contains),
                "Factory lineage artifacts",
                "prompt, requirements, and atom artifacts present",
                clock()
            ),
            GateResult(
                input.nodeId,
                atomResearchIsComplete,
                "Factory atom lineage",
                "planned atoms have exact prompt, span, and requirements-hash markers",
                clock()
            )
        )
        val passed = checkResults.all { it.passed }
        return CompletionGateReport(input.nodeId, passed, checkResults, if (passed) "factory completion gate passed" else "factory gates failed: ${checkResults.filterNot { it.passed }.joinToString("; ") { it.gateName }}")
    }

    private fun auditFactorySurface(input: FactoryCompletionInput): FactorySurfaceAudit {
        val root = repoRoot.toAbsolutePath().normalize()
        val project = runCatching { Path.of(input.projectRoot).toAbsolutePath().normalize() }.getOrNull()
        if (project == null || !project.startsWith(root) || !isNonSymlinkDirectory(project, root)) {
            return FactorySurfaceAudit.refused("factory project root is missing or outside the repository")
        }

        val relativeProject = root.relativize(project).toString().replace('\\', '/')
        val expectedTerritory = input.factoryTerritory.replace('\\', '/').trim().trimEnd('/')
        val territoryValid = expectedTerritory == relativeProject &&
            expectedTerritory.startsWith(".atropos/generated-projects/") &&
            expectedTerritory.split('/').none { it == ".." || it.isBlank() }
        if (!territoryValid) return FactorySurfaceAudit.refused("factory territory is not the generated project root")

        val files = input.files.mapNotNull { relative ->
            val requested = runCatching { Path.of(relative) }.getOrNull() ?: return@mapNotNull null
            if (requested.isAbsolute) return@mapNotNull null
            val candidate = project.resolve(requested).normalize()
            if (!candidate.startsWith(project) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return@mapNotNull null
            }
            if (!isNonSymlinkPath(candidate, project)) return@mapNotNull null
            candidate
        }
        if (files.size != input.files.size || files.isEmpty()) {
            return FactorySurfaceAudit.refused("factory file surface is incomplete or redirected")
        }
        if (!surfaceContainsOnlyDeclaredFiles(project, input.files)) {
            return FactorySurfaceAudit.refused("factory project contains undeclared or redirected files")
        }
        if (!lineageArtifactsBind(project, input)) {
            return FactorySurfaceAudit.refused("factory lineage artifacts do not bind to the supplied hashes")
        }
        if (!sourceArtifactsBind(project, input)) {
            return FactorySurfaceAudit.refused("factory source and proposal digests do not bind to the generated files")
        }

        return runCatching {
            val auditor = AuditorService(project)
            auditor.auditSecrets(files.map(Path::toString))
            auditor.auditDeterministic(files.map(Path::toString))
            val auditDecision = auditor.blockPromotion(claimedBy = "factory-generator", auditedBy = "auditor")
            val directorDecision = DirectorService(DirectorStore(root), root).advisoryBeforePromotion(
                goalId = input.nodeId,
                territoryIds = listOf(expectedTerritory),
                files = input.files
            )
            FactorySurfaceAudit(
                territoryValid = true,
                lineageValid = true,
                integrityValid = true,
                auditorAllowed = auditDecision.allowed,
                auditorDecision = auditDecision.message,
                auditorReportSha256 = auditDecision.reportEvidenceSha256,
                directorAllowed = directorDecision.allowed,
                directorDecision = directorDecision.message,
                detail = "factory project, territory, and ${files.size} files independently audited"
            )
        }.getOrElse { failure ->
            FactorySurfaceAudit.refused("factory independent audit failed: ${failure.javaClass.simpleName}")
        }
    }

    private fun isNonSymlinkDirectory(path: Path, root: Path): Boolean =
        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && isNonSymlinkPath(path, root)

    private fun lineageArtifactsBind(project: Path, input: FactoryCompletionInput): Boolean {
        val promptPath = project.resolve(".atropos/research/user-prompt.md")
        val requirementsPath = project.resolve(".atropos/research/requirements.md")
        val atomsPath = project.resolve(".atropos/research/atoms.md")
        if (listOf(promptPath, requirementsPath, atomsPath).any {
                !Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) || !isNonSymlinkPath(it, project)
            }) return false
        val prompt = runCatching { Files.readString(promptPath) }.getOrNull() ?: return false
        val requirements = runCatching { Files.readString(requirementsPath) }.getOrNull() ?: return false
        val atoms = runCatching { Files.readString(atomsPath) }.getOrNull() ?: return false
        val promptSha = Regex("(?m)^sha256=([0-9a-f]{64})$").find(prompt)?.groupValues?.get(1)
        val promptFingerprint = Regex("(?m)^prompt_fingerprint=(prompt-[0-9a-f]{16})$").find(prompt)?.groupValues?.get(1)
        return promptSha == input.promptSha256 &&
            promptFingerprint == input.promptFingerprint &&
            FactoryLineage.sha256(requirements) == input.researchSha256 &&
            "prompt_fingerprint=${input.promptFingerprint}" in requirements &&
            "prompt_sha256=${input.promptSha256}" in requirements &&
            "prompt_spans=${input.promptSpans}" in atoms &&
            "prompt_fingerprint=${input.promptFingerprint}" in atoms &&
            "prompt_sha256=${input.promptSha256}" in atoms &&
            "research_sha256=${input.researchSha256}" in atoms
    }

    private fun sourceArtifactsBind(project: Path, input: FactoryCompletionInput): Boolean = runCatching {
        val files = input.files.associateWith { relative ->
            val path = project.resolve(relative).normalize()
            runCatching { sha256File(path) }.getOrNull()
        }
        if (files.values.any { it == null }) {
            false
        } else {
            val hashes = files.mapValues { it.value!! }
            val treeCanonical = hashes.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value}" }
            sha256(treeCanonical.toByteArray(StandardCharsets.UTF_8)) == input.sourceTreeSha256 &&
                proposalSha256(project, input.files) == input.proposalSha256
        }
    }.getOrDefault(false)

    private fun sourceCommitExists(input: FactoryCompletionInput): Boolean = runCatching {
        val project = Path.of(input.projectRoot).toAbsolutePath().normalize()
        gitRunner.run(GitWorktreeOperation.VERIFY_COMMIT, project, input.sourceCommitId).exitCode == 0
    }.getOrDefault(false)

    private fun surfaceContainsOnlyDeclaredFiles(project: Path, declaredFiles: List<String>): Boolean = runCatching {
        val declared = declaredFiles.map { it.replace('\\', '/') }.toSet()
        Files.walk(project).use { stream ->
            stream.filter { it != project }.allMatch { path ->
                val relative = project.relativize(path).toString().replace('\\', '/')
                if (Files.isSymbolicLink(path)) return@allMatch false
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return@allMatch true
                if (isDerivedVerifierPath(relative)) return@allMatch true
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && relative in declared
            }
        }
    }.getOrDefault(false)

    private fun isDerivedVerifierPath(relative: String): Boolean =
        relative == ".git" || relative.startsWith(".git/") ||
            relative == "build" || relative.startsWith("build/") ||
            relative == ".atropos/evidence/app-manifest.txt" ||
            relative == ".atropos/evidence/build" || relative.startsWith(".atropos/evidence/build/")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun sha256File(path: Path): String {
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

    private fun proposalSha256(project: Path, files: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.sorted().forEachIndexed { index, relative ->
            if (index > 0) digest.update('\n'.code.toByte())
            digest.update(relative.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
            Files.newInputStream(project.resolve(relative).normalize()).use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isNonSymlinkPath(path: Path, root: Path): Boolean {
        var cursor = root
        for (part in root.relativize(path)) {
            cursor = cursor.resolve(part)
            if (Files.isSymbolicLink(cursor)) return false
        }
        return true
    }

    private data class FactorySurfaceAudit(
        val territoryValid: Boolean,
        val lineageValid: Boolean,
        val integrityValid: Boolean,
        val auditorAllowed: Boolean,
        val auditorDecision: String,
        val auditorReportSha256: String,
        val directorAllowed: Boolean,
        val directorDecision: String,
        val detail: String
    ) {
        companion object {
            fun refused(detail: String) = FactorySurfaceAudit(
                territoryValid = false,
                lineageValid = false,
                integrityValid = false,
                auditorAllowed = false,
                auditorDecision = "",
                auditorReportSha256 = "",
                directorAllowed = false,
                directorDecision = "",
                detail = detail
            )
        }
    }

    fun evaluateNodeInternal(node: DagNode): CompletionGateReport {
        val gates = mutableListOf<GateResult>()
        gates.add(checks.checkBuildMatrix(node))
        gates.add(checks.checkImplementationExists(node))
        gates.add(checks.checkFocusedTests(node))
        gates.add(checks.checkDeterministicVerification(node))
        gates.add(checks.checkCompileGate(node))
        gates.add(checks.checkTerritoryAndSecrets(node))
        gates.add(evidence.checkAcceptanceEvidence(node))
        gates.add(checks.checkExpectedOutputs(node))
        gates.add(checks.checkUnresolvedDimensions(node))
        gates.add(evidence.checkAuditorFindings(node, auditorFactory))

        val allPassed = gates.all { it.passed }
        return CompletionGateReport(
            nodeId = node.id,
            canComplete = allPassed,
            gateResults = gates,
            message = if (allPassed) "all gates passed" else "gates failed: ${gates.filter { !it.passed }.joinToString("; ") { "${it.gateName}: ${it.detail}" }}"
        )
    }

    fun canNodeComplete(nodeId: String): Boolean {
        val node = dagStore.readNode(nodeId) ?: return false
        val report = evaluateNode(node)
        return report.canComplete
    }

    fun markCompleteAfterVerification(node: DagNode): DagNodeState {
        val report = evaluateNode(node)
        if (!report.canComplete) return DagNodeState.FAILED
        memoryStore?.rememberDetailed(
            kind = MemoryKind.VERIFICATION,
            title = "completion gate passed: ${node.id}",
            body = report.gateResults.joinToString("\n") { "${it.gateName}: ${if (it.passed) "PASS" else "FAIL"} - ${it.detail}" },
            tags = listOf("gate", "completion", "verified"),
            subjectType = "dag-node",
            subjectId = node.id
        )
        return DagNodeState.COMPLETE
    }

    fun reVerifyNode(dagId: String, nodeId: String): CompletionGateReport {
        val dag = dagStore.readDag(dagId) ?: return CompletionGateReport(nodeId, false, emptyList(), "DAG not found")
        val node = dag.findNode(nodeId) ?: return CompletionGateReport(nodeId, false, emptyList(), "node not found")
        return evaluateNode(node)
    }

    fun detectFalseCompletions(dagId: String): List<String> {
        val dag = dagStore.readDag(dagId) ?: return emptyList()
        val falseCompletions = mutableListOf<String>()
        for (node in dag.nodes) {
            if (node.state == DagNodeState.COMPLETE) {
                val report = evaluateNode(node)
                if (!report.canComplete) {
                    falseCompletions.add(node.id)
                }
            }
        }
        return falseCompletions
    }
}
