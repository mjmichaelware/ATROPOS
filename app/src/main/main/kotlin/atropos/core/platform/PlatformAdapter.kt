package atropos.core.platform

import atropos.core.policy.BoundedProcessRunner
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit

interface PlatformAdapter {
    val targetPlatform: RuntimePlatform
    fun adapt(abstraction: PlatformAbstraction): PlatformAbstraction
    fun isAvailable(): Boolean
    val displayName: String
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
        get() = wrapped.descriptor.copy(platform = RuntimePlatform.COMPOSE_DESKTOP, capabilities = wrapped.descriptor.capabilities + PlatformCapability.COMPOSE_RENDERING)

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
        return try {
            val proc = ProcessBuilder("adb", "shell", "echo", "available")
                .redirectErrorStream(true)
                .start()
            val completed = proc.waitFor(2, TimeUnit.SECONDS)
            if (!completed) {
                proc.destroyForcibly()
                return false
            }
            val out = proc.inputStream.readNBytes(1024).toString(StandardCharsets.UTF_8).trim()
            proc.exitValue() == 0 && out == "available"
        } catch (_: Exception) {
            false
        }
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
        get() = wrapped.environment.copy(
            pathSeparator = ":",
            fileSeparator = "/",
            tempDir = "/data/local/tmp"
        )

    override fun spawnProcess(command: List<String>, workingDir: String?): Result<ProcessOutput> = runCatching {
        require(command.isNotEmpty()) { "android shell command must not be empty" }
        val adbCmd = if (workingDir.isNullOrBlank()) {
            listOf("adb", "shell") + command
        } else {
            val remote = "cd -- ${shellQuote(workingDir)} && exec " + command.joinToString(" ", transform = ::shellQuote)
            listOf("adb", "shell", "sh", "-c", remote)
        }
        val bounded = boundedProcessRunner.run(
            command = adbCmd,
            directory = Path.of("/"),
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
            command = adbCmd.joinToString(" ")
        )
    }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"
}

object PlatformAdapterRegistry {
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
