package atropos.core.artifact

import atropos.core.execution.LocalWorkQueue
import atropos.core.memory.LocalMemoryStore
import atropos.core.platform.PlatformAbstraction
import atropos.core.platform.PlatformDescriptor
import atropos.core.platform.PlatformEnvironment
import atropos.core.platform.PlatformHealth
import atropos.core.platform.ProcessOutput
import atropos.core.platform.RuntimePlatform
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppFactoryRunProofTest {
    @Test
    fun runFactory_returns_plan_artifact_verification_install_and_commit_evidence() {
        val root = Files.createTempDirectory("atropos-factory-proof-")
        val platform = FakePlatform(root)
        val pipeline = ArtifactPipeline(
            store = ArtifactStore(root),
            platform = platform,
            memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap()),
            queue = LocalWorkQueue(File(root.toFile(), ".atropos/work-queue"), env = emptyMap())
        )

        val run = pipeline.runFactory("build a tiny local cli app", installDir = "installed")

        assertTrue(run.success)
        assertTrue(run.planId.startsWith("factory-"))
        assertTrue(run.projectId?.startsWith("project-") == true)
        assertTrue(run.artifacts.any { it.state == ArtifactState.READY })
        assertTrue(run.verifications.any { it.kind == VerificationKind.SIZE_CHECK && it.passed })
        assertTrue(run.installProof?.verified == true)
        assertEquals(run.artifacts.map { it.id }, run.commitCandidate?.artifactIds)
    }

    private class FakePlatform(private val root: Path) : PlatformAbstraction {
        override val descriptor = PlatformDescriptor(RuntimePlatform.JVM_LINUX, "fake", "1")
        override val environment = PlatformEnvironment(RuntimePlatform.JVM_LINUX, workDir = root.toString())
        override val health = PlatformHealth(RuntimePlatform.JVM_LINUX)

        override fun readFile(path: String): Result<String> = runCatching { Files.readString(root.resolve(path)) }
        override fun writeFile(path: String, content: String): Result<Unit> = runCatching {
            val target = root.resolve(path)
            Files.createDirectories(target.parent)
            Files.writeString(target, content)
        }
        override fun deleteFile(path: String): Result<Unit> = runCatching { Files.deleteIfExists(root.resolve(path)) }
        override fun listDirectory(path: String): Result<List<String>> = runCatching {
            Files.list(root.resolve(path)).use { stream -> stream.map { it.fileName.toString() }.toList() }
        }
        override fun fileExists(path: String): Boolean = Files.exists(root.resolve(path))
        override fun fileSize(path: String): Long = Files.size(root.resolve(path))
        override fun createDirectories(path: String): Result<Unit> = runCatching { Files.createDirectories(root.resolve(path)) }
        override fun spawnProcess(command: List<String>, workingDir: String?): Result<ProcessOutput> = runCatching {
            val outputPath = command.getOrNull(command.indexOf("-d") + 1)
                ?: error("missing -d output")
            writeFile(outputPath, "fake jar")
            ProcessOutput(0, "compiled", "", command.joinToString(" "))
        }
        override fun getEnv(key: String): String? = null
        override fun resolvePath(first: String, vararg rest: String): String =
            root.resolve(Path.of(first, *rest)).toString()
        override fun tempFile(prefix: String, suffix: String): Result<String> = runCatching {
            Files.createTempFile(root, prefix, suffix).toString()
        }
    }
}
