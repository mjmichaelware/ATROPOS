package atropos.core.platform

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
    private val repoRoot: Path = Path.of(System.getProperty("user.dir"))
) : PlatformAbstraction {
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
        Files.readString(repoRoot.resolve(path), StandardCharsets.UTF_8)
    }

    override fun writeFile(path: String, content: String): Result<Unit> = runCatching {
        Files.createDirectories(repoRoot.resolve(path).parent)
        Files.writeString(repoRoot.resolve(path), content, StandardCharsets.UTF_8)
    }

    override fun deleteFile(path: String): Result<Unit> = runCatching {
        Files.deleteIfExists(repoRoot.resolve(path))
    }

    override fun listDirectory(path: String): Result<List<String>> = runCatching {
        val dir = repoRoot.resolve(path).toFile()
        dir.list()?.toList() ?: throw IllegalArgumentException("not a directory: $path")
    }

    override fun fileExists(path: String): Boolean = repoRoot.resolve(path).toFile().exists()
    override fun fileSize(path: String): Long = repoRoot.resolve(path).toFile().length()

    override fun createDirectories(path: String): Result<Unit> = runCatching {
        Files.createDirectories(repoRoot.resolve(path))
    }

    override fun spawnProcess(command: List<String>, workingDir: String?): Result<ProcessOutput> = runCatching {
        val pb = ProcessBuilder(command)
            .redirectErrorStream(false)
            .directory(workingDir?.let { File(it) } ?: repoRoot.toFile())
        val proc = pb.start()
        val stdout = proc.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val stderr = proc.errorStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val exit = proc.waitFor()
        ProcessOutput(exitCode = exit, stdout = stdout, stderr = stderr, command = command.joinToString(" "))
    }

    override fun getEnv(key: String): String? = System.getenv(key)

    override fun resolvePath(first: String, vararg rest: String): String {
        return repoRoot.resolve(Paths.get(first, *rest)).toString()
    }

    override fun tempFile(prefix: String, suffix: String): Result<String> = runCatching {
        val tmp = Files.createTempFile(prefix, suffix)
        tmp.toAbsolutePath().toString()
    }
}

object Platform {
    val current: PlatformAbstraction = JvmPlatformAbstraction()
    val descriptor: PlatformDescriptor get() = current.descriptor
    val environment: PlatformEnvironment get() = current.environment
    val health: PlatformHealth get() = current.health
}
