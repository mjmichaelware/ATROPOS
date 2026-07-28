package atropos.core.platform

import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class PlatformEnvironment(
    val platform: RuntimePlatform,
    val workDir: String = System.getProperty("user.dir", "/"),
    val tempDir: String = System.getProperty("java.io.tmpdir", "/tmp"),
    val homeDir: String = System.getProperty("user.home", "/root"),
    val pathSeparator: String = System.getProperty("path.separator", ":"),
    val fileSeparator: String = System.getProperty("file.separator", "/"),
    val availableMemoryMb: Long = Runtime.getRuntime().maxMemory() / (1024 * 1024),
    val availableCores: Int = Runtime.getRuntime().availableProcessors()
) {
    val workDirPath: Path get() = Path.of(workDir)
    val tempDirPath: Path get() = Path.of(tempDir)
    val homeDirPath: Path get() = Path.of(homeDir)

    fun resolvePath(path: String): Path {
        val inputPath = Path.of(path)
        return if (inputPath.isAbsolute) {
            inputPath
        } else {
            workDirPath.resolve(path)
        }
    }

    fun withTemporaryFile(
        content: () -> String,
        prefix: String = "tmp",
        suffix: String = ""
    ): Result<Path> = runCatching {
        val file = Files.createTempFile(prefix, suffix).apply {
            writeText(content(), StandardCharsets.UTF_8)
        }
        file
    }.map { path -> path }
}
