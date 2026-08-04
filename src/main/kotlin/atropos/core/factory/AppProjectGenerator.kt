package atropos.core.factory

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import atropos.core.auditor.AuditorService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.director.DriftSeverity
import atropos.core.director.ObservationKind
import atropos.core.verification.FactoryCompletionInput
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
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
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
        val target = repoRoot.resolve(".atropos/generated-projects").resolve("${safeName(spec.intent.name)}-${safeProjectId(projectId)}").normalize()
        require(target.startsWith(repoRoot.normalize())) { "app target escaped repository root" }
        require(!Files.exists(target) || isEmptyDirectory(target)) { "app target already contains files: $target" }
        mutationGate.requireAllowed(repoRoot, target)
        Files.createDirectories(target)
        val files = scaffold.files(spec)
        val lineageFiles = effectiveLineage.projectFiles(planningDagId ?: "factory-$projectId", plannedAtomIds)
        val allFiles = LinkedHashMap(files)
        allFiles.putAll(lineageFiles)
        allFiles.forEach { (relative, content) ->
            val file = target.resolve(relative).normalize()
            require(file.startsWith(target)) { "app file escaped target" }
            Files.createDirectories(file.parent)
            Files.writeString(file, content, StandardCharsets.UTF_8)
        }
        target.resolve("verify.sh").toFile().setExecutable(true)
        val verificationOutput = runVerify(target)
        val branchName = "${safeName(spec.intent.name)}-${safeProjectId(projectId)}"
        runGit(target, GitWorktreeOperation.INIT)
        runGit(target, GitWorktreeOperation.CHECKOUT_BRANCH, branchName)
        runGit(target, GitWorktreeOperation.ADD_ALL)
        runGit(target, GitWorktreeOperation.COMMIT, "initial app scaffold")
        val initialCommit = runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
        val branch = runGit(target, GitWorktreeOperation.REV_PARSE_BRANCH).trim().ifBlank { "HEAD" }
        check(branch == branchName) { "generated branch isolation failed: expected $branchName, got $branch" }
        val relativePaths = allFiles.keys.toList()
        val absolutePaths = relativePaths.map { target.resolve(it).toAbsolutePath().toString() }
        val auditor = AuditorService(repoRoot)
        auditor.auditSecrets(absolutePaths)
        auditor.auditDeterministic(absolutePaths)
        val auditDecision = auditor.blockPromotion(claimedBy = "factory-generator", auditedBy = "auditor")
        check(auditDecision.allowed) { "factory audit blocked promotion: ${auditDecision.message}" }
        val director = DirectorService(DirectorStore(repoRoot), repoRoot)
        director.observe(ObservationKind.MEMORY_WATERMARK, DriftSeverity.INFO, "factory", "factory source and research prepared", files = relativePaths)
        val hashes = allFiles.keys.associateWith { sha256(target.resolve(it)) }
        val treeSha256 = treeDigest(hashes)
        val gate = VerifiedCompletionGate(repoRoot = repoRoot).evaluateFactory(
            FactoryCompletionInput(
                nodeId = planningDagId ?: "factory-$projectId",
                branch = branch,
                expectedBranch = branchName,
                files = relativePaths,
                verificationOutput = verificationOutput,
                auditorAllowed = auditDecision.allowed,
                promptSha256 = effectiveLineage.promptSha256,
                researchSha256 = effectiveLineage.researchSha256
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
                verification = "generated-source-and-tests",
                exportPath = export.fileName.toString(),
                treeSha256 = treeSha256,
                planningDagId = planningDagId,
                plannedAtomIds = plannedAtomIds,
                verificationOutputSha256 = sha256(verificationOutput.toByteArray(StandardCharsets.UTF_8)),
                promptSha256 = effectiveLineage.promptSha256,
                promptFingerprint = effectiveLineage.promptFingerprint,
                researchSha256 = effectiveLineage.researchSha256,
                auditorDecision = auditDecision.message,
                completionGate = gate.message,
                promptSpans = effectiveLineage.promptSpans,
                contextHash = effectiveLineage.contextHash
            ).render(hashes),
            StandardCharsets.UTF_8
        )
        runGit(target, GitWorktreeOperation.ADD_ALL)
        runGit(target, GitWorktreeOperation.COMMIT, "app evidence")
        val commit = runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
        runGit(target, GitWorktreeOperation.ARCHIVE, export.toAbsolutePath().toString())
        return GeneratedAppProject(target.toString(), spec, relativePaths, evidence.toString(), commit, branch, treeSha256, export.toString(), planningDagId, plannedAtomIds)
    }

    private fun runGit(directory: Path, operation: GitWorktreeOperation, argument: String? = null): String {
        val result = gitRunner.run(operation, directory, argument)
        check(result.exitCode == 0) { "app git command failed: ${operation.name.lowercase()}: ${result.output.take(240)}" }
        return result.output
    }

    private fun runVerify(directory: Path): String {
        val command = ProcessBuilder("sh", "verify.sh")
            .directory(directory.toFile())
            .redirectErrorStream(true)
        // The Kotlin runner sets this flag for its own launcher. Generated
        // verification invokes the compiler, which must receive compiler mode.
        command.environment().remove("KOTLIN_RUNNER")
        val process = command.start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0 && output.contains("APP_FACTORY_VERIFY_OK")) {
            "generated app verification failed: ${output.replace(Regex("\\s+"), " ").trim().take(400)}"
        }
        return output
    }

    private fun isEmptyDirectory(path: Path): Boolean = Files.isDirectory(path) && Files.list(path).use { !it.findAny().isPresent }
    private fun safeName(value: String): String {
        val normalized = value.replace(Regex("[^A-Za-z0-9_]"), "_").lowercase()
        return if (normalized.firstOrNull()?.isLetter() == true || normalized.firstOrNull() == '_') normalized else "app_$normalized"
    }

    private fun safeProjectId(value: String): String {
        val normalized = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (normalized.isBlank()) "project" else normalized
    }
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun treeDigest(hashes: Map<String, String>): String {
        val canonical = hashes.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
