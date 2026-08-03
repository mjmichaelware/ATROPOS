package atropos.core.factory

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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
        plannedAtomIds: List<String> = emptyList()
    ): GeneratedAppProject {
        // Generated repositories are durable evidence-bearing outputs, not
        // Gradle build products. Keep them in a policy-allowed ATROPOS area.
        val target = repoRoot.resolve(".atropos/generated-projects").resolve("${safeId(spec.intent.name)}-${safeId(projectId)}").normalize()
        require(target.startsWith(repoRoot.normalize())) { "app target escaped repository root" }
        require(!Files.exists(target) || isEmptyDirectory(target)) { "app target already contains files: $target" }
        mutationGate.requireAllowed(repoRoot, target)
        Files.createDirectories(target)
        val files = scaffold.files(spec)
        files.forEach { (relative, content) ->
            val file = target.resolve(relative).normalize()
            require(file.startsWith(target)) { "app file escaped target" }
            Files.createDirectories(file.parent)
            Files.writeString(file, content, StandardCharsets.UTF_8)
        }
        target.resolve("verify.sh").toFile().setExecutable(true)
        val verificationOutput = runVerify(target)
        runGit(target, GitWorktreeOperation.INIT)
        runGit(target, GitWorktreeOperation.ADD_ALL)
        runGit(target, GitWorktreeOperation.COMMIT, "initial app scaffold")
        val initialCommit = runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
        val branch = runGit(target, GitWorktreeOperation.REV_PARSE_BRANCH).trim().ifBlank { "HEAD" }
        val hashes = files.keys.associateWith { sha256(target.resolve(it)) }
        val treeSha256 = treeDigest(hashes)
        val evidence = target.resolve(".atropos/evidence/app-manifest.txt")
        Files.createDirectories(evidence.parent)
        val export = target.parent.resolve("${safeId(spec.intent.name)}-${safeId(projectId)}.tar")
        mutationGate.requireAllowed(repoRoot, export)
        Files.writeString(
            evidence,
            EvidenceManifest(
                projectPath = ".",
                commitId = initialCommit,
                branch = branch,
                files = files.keys.toList(),
                verification = "generated-test-and-content-shape",
                exportPath = export.fileName.toString(),
                treeSha256 = treeSha256,
                planningDagId = planningDagId,
                plannedAtomIds = plannedAtomIds,
                verificationOutputSha256 = sha256(verificationOutput.toByteArray(StandardCharsets.UTF_8))
            ).render(hashes),
            StandardCharsets.UTF_8
        )
        runGit(target, GitWorktreeOperation.ADD_ALL)
        runGit(target, GitWorktreeOperation.COMMIT, "app evidence")
        val commit = runGit(target, GitWorktreeOperation.REV_PARSE_HEAD).trim()
        runGit(target, GitWorktreeOperation.ARCHIVE, export.toAbsolutePath().toString())
        return GeneratedAppProject(target.toString(), spec, files.keys.toList(), evidence.toString(), commit, branch, treeSha256, export.toString(), planningDagId, plannedAtomIds)
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
        check(process.waitFor() == 0 && output.contains("APP_SCAFFOLD_VERIFY_OK")) {
            "generated app verification failed: ${output.replace(Regex("\\s+"), " ").trim().take(400)}"
        }
        return output
    }

    private fun isEmptyDirectory(path: Path): Boolean = Files.isDirectory(path) && Files.list(path).use { !it.findAny().isPresent }
    private fun safeId(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun treeDigest(hashes: Map<String, String>): String {
        val canonical = hashes.toSortedMap().entries.joinToString("\n") { "${it.key} ${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
