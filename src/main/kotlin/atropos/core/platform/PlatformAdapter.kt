package atropos.core.platform

import atropos.core.policy.BoundedProcessRunner
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

sealed class ToolResolutionResult {
    data class Found(val path: Path) : ToolResolutionResult()
    data class Missing(val binaryName: String, val searchedDirs: List<String>) : ToolResolutionResult()
}

interface PlatformAdapter {
    val targetPlatform: RuntimePlatform
    fun adapt(abstraction: PlatformAbstraction): PlatformAbstraction
    fun isAvailable(): Boolean
    val displayName: String

    fun resolveTool(binaryName: String): ToolResolutionResult {
        val pathEnv = System.getenv("PATH").orEmpty()
        val pathEntries = pathEnv.split(java.io.File.pathSeparator).filter { it.isNotBlank() }.toMutableList()
        val extraSearchDirs = listOfNotNull(
            System.getenv("PREFIX")?.let { "$it/bin" },
            "/system/bin",
            "/system/xbin",
            "/apex/com.android.runtime/bin",
            "/usr/local/bin",
            "/usr/bin",
            "/bin",
            "/opt/homebrew/bin"
        )
        for (extra in extraSearchDirs) {
            if (extra !in pathEntries && java.io.File(extra).isDirectory) {
                pathEntries.add(extra)
            }
        }
        for (dir in pathEntries) {
            val candidate = java.io.File(dir, binaryName)
            if (candidate.canExecute() || (candidate.exists() && candidate.isFile)) {
                return ToolResolutionResult.Found(candidate.toPath())
            }
        }
        return ToolResolutionResult.Missing(binaryName, pathEntries)
    }

    fun resolveToolPath(binaryName: String): Path {
        return when (val res = resolveTool(binaryName)) {
            is ToolResolutionResult.Found -> res.path
            is ToolResolutionResult.Missing -> Path.of(binaryName)
        }
    }

    fun getHomeDirectory(): Path {
        val candidate = listOfNotNull(
            System.getProperty("user.home"),
            System.getenv("HOME"),
            System.getenv("EXTERNAL_STORAGE"),
            "."
        ).firstOrNull { it.isNotBlank() } ?: "."
        return Path.of(candidate).toAbsolutePath().normalize()
    }

    fun getSystemTemp(): Path {
        val candidate = listOfNotNull(
            System.getenv("TMPDIR"),
            System.getProperty("java.io.tmpdir"),
            System.getenv("PREFIX")?.let { "$it/tmp" },
            "/data/local/tmp",
            "/tmp"
        ).firstOrNull { dir ->
            runCatching {
                val f = java.io.File(dir)
                f.exists() && f.canWrite()
            }.getOrDefault(false)
        } ?: System.getProperty("java.io.tmpdir") ?: "/tmp"
        return Path.of(candidate).toAbsolutePath().normalize()
    }
}

class ComposeDesktopAdapter : PlatformAdapter {
    override val targetPlatform: RuntimePlatform = RuntimePlatform.COMPOSE_DESKTOP
    override val displayName: String = "Compose Desktop Adapter"

    override fun isAvailable(): Boolean {
        val marker = "androidx/compose/runtime/Composable.class"
        val contextLoader = Thread.currentThread().contextClassLoader
        return contextLoader?.getResource(marker) != null ||
            ComposeDesktopAdapter::class.java.classLoader?.getResource(marker) != null
    }

    override fun adapt(abstraction: PlatformAbstraction): PlatformAbstraction {
        return ComposeDesktopAbstraction(abstraction)
    }
}

class JvmStandardAdapter : PlatformAdapter {
    override val targetPlatform: RuntimePlatform = RuntimePlatform.JVM_LINUX
    override val displayName: String = "JVM Standard Adapter"

    override fun isAvailable(): Boolean = true

    override fun adapt(abstraction: PlatformAbstraction): PlatformAbstraction = abstraction
}

class ComposeDesktopAbstraction(
    private val wrapped: PlatformAbstraction
) : PlatformAbstraction by wrapped {
    override val descriptor: PlatformDescriptor
        get() = wrapped.descriptor.copy(
            platform = RuntimePlatform.COMPOSE_DESKTOP,
            capabilities = wrapped.descriptor.capabilities + PlatformCapability.COMPOSE_RENDERING
        )

    override fun spawnProcess(command: List<String>, workingDir: String?): Result<ProcessOutput> {
        val augmented = if (command.firstOrNull()?.contains("compose") == true) {
            listOf("compose", "desktop", "run") + command
        } else command
        return wrapped.spawnProcess(augmented, workingDir)
    }
}

class AndroidShellAdapter : PlatformAdapter {
    override val targetPlatform: RuntimePlatform = RuntimePlatform.ANDROID
    override val displayName: String = "Android Shell Adapter"

    override fun isAvailable(): Boolean {
        val isAndroidVendor = System.getProperty("java.vendor", "").contains("Android", ignoreCase = true)
        val hasAndroidRoot = System.getenv("ANDROID_ROOT") != null || System.getenv("ANDROID_DATA") != null
        val hasTermuxPrefix = System.getenv("PREFIX")?.contains("com.termux") == true
        if (isAndroidVendor || hasAndroidRoot || hasTermuxPrefix) return true

        val result = runCatching {
            BoundedProcessRunner().run(
                command = listOf("adb", "shell", "echo", "available"),
                directory = Path.of("/"),
                timeoutMillis = 2_000L,
                maxOutputBytes = 1_024,
                maxOutputLines = 8
            )
        }.getOrNull() ?: return false
        val out = result.stdout.trim()
        return !result.timedOut && result.launchError == null && result.exitCode == 0 && out == "available"
    }

    override fun adapt(abstraction: PlatformAbstraction): PlatformAbstraction {
        return AndroidShellAbstraction(abstraction)
    }
}

class AndroidShellAbstraction(
    private val wrapped: PlatformAbstraction
) : PlatformAbstraction by wrapped {
    private val boundedProcessRunner = BoundedProcessRunner()

    override val descriptor: PlatformDescriptor
        get() = wrapped.descriptor.copy(
            platform = RuntimePlatform.ANDROID,
            capabilities = wrapped.descriptor.capabilities - setOf(PlatformCapability.PROCESS_SPAWN) +
                PlatformCapability.DISPLAY_OUTPUT
        )

    override val environment: PlatformEnvironment
        get() {
            val resolvedTemp = listOfNotNull(
                System.getenv("TMPDIR"),
                System.getProperty("java.io.tmpdir"),
                System.getenv("PREFIX")?.let { "$it/tmp" },
                "/data/local/tmp"
            ).firstOrNull { runCatching { val f = java.io.File(it); f.exists() && f.canWrite() }.getOrDefault(false) }
                ?: (System.getenv("HOME") ?: "/data/local/tmp")

            return wrapped.environment.copy(
                pathSeparator = ":",
                fileSeparator = "/",
                tempDir = resolvedTemp
            )
        }

    override fun spawnProcess(command: List<String>, workingDir: String?): Result<ProcessOutput> = runCatching {
        require(command.isNotEmpty()) { "android shell command must not be empty" }
        val isDirectAndroid = java.io.File("/system/bin/sh").exists() || System.getenv("PREFIX") != null
        val effectiveCmd = if (isDirectAndroid) {
            command
        } else {
            if (workingDir.isNullOrBlank()) {
                listOf("adb", "shell") + command
            } else {
                val remote = "cd -- ${shellQuote(workingDir)} && exec " + command.joinToString(" ", transform = ::shellQuote)
                listOf("adb", "shell", "sh", "-c", remote)
            }
        }
        val execDir = workingDir?.let { Path.of(it) } ?: Path.of("/")
        val bounded = boundedProcessRunner.run(
            command = effectiveCmd,
            directory = if (isDirectAndroid && workingDir != null) execDir else Path.of("/"),
            timeoutMillis = 1_800_000,
            maxOutputBytes = 256 * 1024,
            maxOutputLines = 4_000
        )
        val stderr = buildString {
            bounded.launchError?.let(::appendLine)
            if (bounded.timedOut) appendLine("android shell command timed out")
            append(bounded.stderr)
        }
        ProcessOutput(
            exitCode = bounded.exitCode ?: 124,
            stdout = bounded.stdout,
            stderr = stderr,
            command = effectiveCmd.joinToString(" ")
        )
    }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"
}

object PlatformAdapterRegistry {
    private var activeAdapter: PlatformAdapter = JvmStandardAdapter()

    fun current(): PlatformAdapter = activeAdapter

    fun setAdapter(adapter: PlatformAdapter) {
        activeAdapter = adapter
    }

    private val adapters: MutableList<PlatformAdapter> = mutableListOf(
        JvmStandardAdapter(),
        ComposeDesktopAdapter(),
        AndroidShellAdapter()
    )

    /** Keep one deterministic adapter owner per target platform. */
    @Synchronized
    fun register(adapter: PlatformAdapter) {
        val existing = adapters.indexOfFirst { it.targetPlatform == adapter.targetPlatform }
        if (existing >= 0) adapters[existing] = adapter else adapters += adapter
    }

    @Synchronized
    fun available(): List<PlatformAdapter> = adapters.toList().filter(::isAvailableSafely)

    @Synchronized
    fun forPlatform(platform: RuntimePlatform): PlatformAdapter? =
        adapters.firstOrNull { it.targetPlatform == platform && isAvailableSafely(it) }

    @Synchronized
    fun all(): List<PlatformAdapter> = adapters.toList()

    fun renderAvailable(): String {
        val avail = available()
        if (avail.isEmpty()) return "PlatformAdapters: none available"
        return avail.joinToString("\n") { "  ${it.displayName} (${it.targetPlatform.name})" }
    }

    private fun isAvailableSafely(adapter: PlatformAdapter): Boolean =
        runCatching { adapter.isAvailable() }.getOrDefault(false)
}
