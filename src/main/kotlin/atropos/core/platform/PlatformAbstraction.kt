package atropos.core.platform

import atropos.core.AtroposRepoRootLocator
import atropos.core.policy.BoundedProcessRunner
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths

interface PlatformAbstraction {
    val descriptor: PlatformDescriptor
    val environment: PlatformEnvironment
    val health: PlatformHealth

    fun readFile(path: String): Result<String>
    fun writeFile(path: String, content: String): Result<Unit>
    fun deleteFile(path: String): Result<Unit>
    fun listDirectory(path: String): Result<List<String>>
    fun fileExists(path: String): Boolean
    fun fileSize(path: String): Long
    fun createDirectories(path: String): Result<Unit>
    fun spawnProcess(command: List<String>, workingDir: String? = null): Result<ProcessOutput>
    fun getEnv(key: String): String?
    fun resolvePath(first: String, vararg rest: String): String
    fun tempFile(prefix: String, suffix: String): Result<String>
    fun currentTimeMillis(): Long = System.currentTimeMillis()
    fun nanoTime(): Long = System.nanoTime()
}

data class ProcessOutput(val exitCode: Int, val stdout: String, val stderr: String, val command: String)

class JvmPlatformAbstraction(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve()
) : PlatformAbstraction {
    private val normalizedRepoRoot = repoRoot.toAbsolutePath().normalize()
    private val boundedProcessRunner = BoundedProcessRunner()

    override val descriptor: PlatformDescriptor = PlatformDescriptor.detect()
    override val environment: PlatformEnvironment = PlatformEnvironment(platform = descriptor.platform)
    override val health: PlatformHealth by lazy {
        PlatformHealth(
            platform = descriptor.platform,
            networkReachable = try { java.net.URL("https://google.com").openConnection().connectTimeout = 2000; true }
                catch (_: Exception) { false }
        )
    }

    override fun readFile(path: String): Result<String> = runCatching {
        Files.readString(resolveRepoPath(path), StandardCharsets.UTF_8)
    }

    override fun writeFile(path: String, content: String): Result<Unit> = runCatching {
        val target = resolveRepoPath(path)
        Files.createDirectories(target.parent)
        Files.writeString(target, content, StandardCharsets.UTF_8)
    }

    override fun deleteFile(path: String): Result<Unit> = runCatching {
        Files.deleteIfExists(resolveRepoPath(path))
    }

    override fun listDirectory(path: String): Result<List<String>> = runCatching {
        val dir = resolveRepoPath(path).toFile()
        dir.list()?.toList() ?: throw IllegalArgumentException("not a directory: $path")
    }

    override fun fileExists(path: String): Boolean = runCatching {
        Files.exists(resolveRepoPath(path), LinkOption.NOFOLLOW_LINKS)
    }.getOrDefault(false)

    override fun fileSize(path: String): Long = runCatching {
        Files.size(resolveRepoPath(path))
    }.getOrDefault(0L)

    override fun createDirectories(path: String): Result<Unit> = runCatching {
        Files.createDirectories(resolveRepoPath(path))
    }

    override fun spawnProcess(command: List<String>, workingDir: String?): Result<ProcessOutput> = runCatching {
        val bounded = boundedProcessRunner.run(
            command = command,
            directory = workingDir?.let(::resolveRepoPath) ?: normalizedRepoRoot,
            timeoutMillis = 1_800_000,
            maxOutputBytes = 256 * 1024,
            maxOutputLines = 4_000
        )
        val stderr = buildString {
            bounded.launchError?.let(::appendLine)
            if (bounded.timedOut) appendLine("JVM process timed out")
            append(bounded.stderr)
        }
        ProcessOutput(
            exitCode = bounded.exitCode ?: 124,
            stdout = bounded.stdout,
            stderr = stderr,
            command = command.joinToString(" ")
        )
    }

    override fun getEnv(key: String): String? = System.getenv(key)

    override fun resolvePath(first: String, vararg rest: String): String {
        return resolveRepoPath(Paths.get(first, *rest).toString()).toString()
    }

    override fun tempFile(prefix: String, suffix: String): Result<String> = runCatching {
        val tmp = Files.createTempFile(prefix, suffix)
        tmp.toAbsolutePath().toString()
    }

    private fun resolveRepoPath(rawPath: String): Path {
        require(rawPath.isNotBlank()) { "platform path is blank" }
        val candidate = Paths.get(rawPath).let { path ->
            if (path.isAbsolute) path else normalizedRepoRoot.resolve(path)
        }.normalize()
        require(candidate.startsWith(normalizedRepoRoot)) {
            "platform path escapes repository root"
        }
        var cursor: Path? = normalizedRepoRoot
        for (part in normalizedRepoRoot.relativize(candidate)) {
            cursor = cursor!!.resolve(part)
            require(!Files.isSymbolicLink(cursor)) {
                "platform path crosses a symbolic link"
            }
        }
        return candidate
    }
}

object Platform {
    val current: PlatformAbstraction = JvmPlatformAbstraction()
    val descriptor: PlatformDescriptor get() = current.descriptor
    val environment: PlatformEnvironment get() = current.environment
    val health: PlatformHealth get() = current.health
}
