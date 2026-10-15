package atropos.core.toolchain

import atropos.core.platform.Platform
import atropos.core.platform.RuntimePlatform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class LanguageToolchainDriverInstance(
    val driver: LanguageToolchainDriver,
    val discoveredAt: Long = System.currentTimeMillis()
)

class NativeToolchainRegistry(
    private val customSearchPaths: List<Path> = emptyList()
) {
    private val driverCache = mutableMapOf<ToolchainLanguage, LanguageToolchainDriverInstance>()

    fun discover(language: ToolchainLanguage): LanguageToolchainDriver {
        val cached = driverCache[language]?.driver
        if (cached != null) return cached

        val binaryPath = probeBinary(language)
            ?: throw IllegalStateException("Toolchain for $language unavailable on platform ${Platform.currentPlatform()}")

        val driver = when (language) {
            ToolchainLanguage.RUST -> RustCargoDriver(binaryPath)
            ToolchainLanguage.TYPESCRIPT -> TypescriptNodeDriver(binaryPath)
            ToolchainLanguage.GO -> GoToolchainDriver(binaryPath)
            ToolchainLanguage.PYTHON -> PythonFastApiDriver(binaryPath)
            ToolchainLanguage.KOTLIN -> KotlinGradleDriver(binaryPath)
            ToolchainLanguage.ANDROID -> AndroidSdkDriver(binaryPath, discoverAndroidHome(), KotlinGradleDriver(probeBinary(ToolchainLanguage.KOTLIN)!!))
        }

        driverCache[language] = LanguageToolchainDriverInstance(driver)
        return driver
    }

    fun availableToolchains(): Map<ToolchainLanguage, Path> {
        val result = mutableMapOf<ToolchainLanguage, Path>()
        for (lang in ToolchainLanguage.values()) {
            val path = probeBinary(lang)
            if (path != null) result[lang] = path
        }
        return result
    }

    private fun probeBinary(language: ToolchainLanguage): Path? {
        val binaryNames = when (language) {
            ToolchainLanguage.RUST -> listOf("cargo")
            ToolchainLanguage.TYPESCRIPT -> listOf("tsc", "node")
            ToolchainLanguage.GO -> listOf("go")
            ToolchainLanguage.PYTHON -> listOf("python3", "python")
            ToolchainLanguage.KOTLIN -> listOf("kotlinc", "gradle", "./gradlew")
            ToolchainLanguage.ANDROID -> listOf("gradle", "./gradlew")
        }

        val searchPaths = buildSearchPaths()

        for (binaryName in binaryNames) {
            for (basePath in searchPaths) {
                val candidate = basePath.resolve(binaryName)
                if (Files.isExecutable(candidate)) {
                    return candidate
                }
            }
        }

        return null
    }

    private fun buildSearchPaths(): List<Path> {
        val paths = mutableListOf<Path>()

        paths += customSearchPaths

        val pathEnv = System.getenv("PATH") ?: ""
        paths += pathEnv.split(":").map { Paths.get(it) }

        when (Platform.currentPlatform()) {
            RuntimePlatform.LINUX, RuntimePlatform.TERMUX -> {
                val cargoHome = System.getenv("CARGO_HOME") ?: System.getProperty("user.home") + "/.cargo"
                paths.add(Paths.get(cargoHome, "bin"))
                val goRoot = System.getenv("GOROOT") ?: "/usr/local/go"
                paths.add(Paths.get(goRoot, "bin"))
                val goPath = System.getenv("GOPATH") ?: System.getProperty("user.home") + "/go"
                paths.add(Paths.get(goPath, "bin"))
            }
            RuntimePlatform.MACOS -> {
                val cargoHome = System.getenv("CARGO_HOME") ?: System.getProperty("user.home") + "/.cargo"
                paths.add(Paths.get(cargoHome, "bin"))
                val goRoot = System.getenv("GOROOT") ?: "/usr/local/go"
                paths.add(Paths.get(goRoot, "bin"))
                val homebrew = "/opt/homebrew/bin"
                if (Files.isDirectory(Paths.get(homebrew))) paths.add(Paths.get(homebrew))
                paths.add(Paths.get("/usr/local/bin"))
            }
            RuntimePlatform.ANDROID -> {
                val cargoHome = System.getenv("CARGO_HOME") ?: System.getProperty("user.home") + "/.cargo"
                paths.add(Paths.get(cargoHome, "bin"))
            }
        }

        return paths.distinct()
    }

    private fun discoverAndroidHome(): Path {
        val androidHome = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: throw IllegalStateException("ANDROID_HOME or ANDROID_SDK_ROOT not set")
        return Paths.get(androidHome)
    }
}