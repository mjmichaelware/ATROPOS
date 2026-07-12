package atropos.core.platform

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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
        return try {
            Class.forName("androidx.compose.runtime.Composable")
            true
        } catch (_: Exception) {
            false
        }
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
            val out = proc.inputStream.readAllBytes().toString(StandardCharsets.UTF_8).trim()
            proc.waitFor()
            proc.exitValue() == 0 && out.contains("available")
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
        val adbCmd = listOf("adb", "shell") + command
        val pb = ProcessBuilder(adbCmd)
            .redirectErrorStream(false)
            .directory(workingDir?.let { File(it) } ?: File("/"))
        val proc = pb.start()
        val stdout = proc.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val stderr = proc.errorStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val exit = proc.waitFor()
        ProcessOutput(exitCode = exit, stdout = stdout, stderr = stderr, command = adbCmd.joinToString(" "))
    }
}

object PlatformAdapterRegistry {
    private val adapters: MutableList<PlatformAdapter> = mutableListOf(
        JvmStandardAdapter(),
        ComposeDesktopAdapter(),
        AndroidShellAdapter()
    )

    fun register(adapter: PlatformAdapter) { adapters += adapter }

    fun available(): List<PlatformAdapter> = adapters.filter { it.isAvailable() }

    fun forPlatform(platform: RuntimePlatform): PlatformAdapter? = adapters.firstOrNull { it.targetPlatform == platform && it.isAvailable() }

    fun all(): List<PlatformAdapter> = adapters.toList()

    fun renderAvailable(): String {
        val avail = available()
        if (avail.isEmpty()) return "PlatformAdapters: none available"
        return avail.joinToString("\n") { "  ${it.displayName} (${it.targetPlatform.name})" }
    }
}
