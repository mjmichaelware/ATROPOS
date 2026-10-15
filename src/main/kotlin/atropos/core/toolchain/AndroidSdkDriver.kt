package atropos.core.toolchain

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import java.nio.file.Files
import java.nio.file.Path

class AndroidSdkDriver(
    override val binaryPath: Path,
    private val androidHome: Path,
    private val gradleDriver: KotlinGradleDriver
) : LanguageToolchainDriver {
    override val language = ToolchainLanguage.ANDROID

    init {
        validateSdk()
    }

    private fun validateSdk() {
        val platforms = androidHome.resolve("platforms")
        val buildTools = androidHome.resolve("build-tools")
        if (!Files.isDirectory(platforms) || !Files.isDirectory(buildTools)) {
            throw IllegalStateException("Invalid Android SDK at $androidHome: missing platforms or build-tools")
        }
    }

    override fun compile(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val env = mapOf("ANDROID_HOME" to androidHome.toString())
        val command = listOf("assembleDebug") + extraArgs
        val result = gradleDriver.processRunner.run(
            buildGradleCommand(workingDir, command),
            workingDir,
            300_000,
            20_000_000,
            4000,
            environment = env
        )
        return parseResult(result)
    }

    override fun test(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val env = mapOf("ANDROID_HOME" to androidHome.toString())
        val command = listOf("testDebugUnitTest") + extraArgs
        val result = gradleDriver.processRunner.run(
            buildGradleCommand(workingDir, command),
            workingDir,
            300_000,
            20_000_000,
            4000,
            environment = env
        )
        return parseResult(result)
    }

    override fun lint(workingDir: Path): ToolchainResult {
        val env = mapOf("ANDROID_HOME" to androidHome.toString())
        val command = listOf("lint") + extraArgs
        val result = gradleDriver.processRunner.run(
            buildGradleCommand(workingDir, command),
            workingDir,
            180_000,
            20_000_000,
            4000,
            environment = env
        )
        return parseResult(result)
    }

    override fun packageArtifact(workingDir: Path, outputDir: Path): ToolchainResult {
        val env = mapOf("ANDROID_HOME" to androidHome.toString())
        val command = listOf("assembleDebug") + extraArgs
        val result = gradleDriver.processRunner.run(
            buildGradleCommand(workingDir, command),
            workingDir,
            300_000,
            20_000_000,
            4000,
            environment = env
        )

        val apkPath = workingDir.resolve("build/outputs/apk/debug/app-debug.apk")
        if (!Files.isRegularFile(apkPath) || Files.size(apkPath) == 0) {
            return ToolchainResult(
                exitCode = 1,
                stdout = result.stdout,
                stderr = result.stderr + "\nAPK not found or empty at $apkPath",
                diagnostics = listOf(ToolchainDiagnostic("app-debug.apk", 0, 0, "APK_MISSING", "Generated APK is missing or empty", DiagnosticSeverity.ERROR)),
                durationMs = result.durationMillis
            )
        }

        Files.copy(apkPath, outputDir.resolve("app-debug.apk"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return parseResult(result)
    }

    private fun buildGradleCommand(workingDir: Path, tasks: List<String>): List<String> {
        val gradleWrapper = workingDir.resolve("gradlew")
        if (Files.isExecutable(gradleWrapper)) {
            return listOf(gradleWrapper.toString()) + tasks + listOf("--no-daemon", "--offline", "-Dorg.gradle.project.cachedir=${workingDir}/.gradle")
        } else {
            return listOf("gradle") + tasks + listOf("--no-daemon", "--offline", "-Dorg.gradle.project.cachedir=${workingDir}/.gradle")
        }
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseGradleDiagnostics(result.stdout + "\n" + result.stderr)
        val hasErrors = diagnostics.any { it.severity == DiagnosticSeverity.ERROR } || result.exitCode != 0
        return ToolchainResult(
            exitCode = if (hasErrors) 1 else result.exitCode ?: 1,
            stdout = result.stdout,
            stderr = result.stderr,
            diagnostics = diagnostics,
            durationMs = result.durationMillis
        )
    }

    private fun parseGradleDiagnostics(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        val pattern = java.util.regex.Pattern.compile("^e:\\s+(.+?):\\s+\\((\\d+),\\s*(\\d+)\\):\\s+(.+)$")

        for (line in output.lineSequence()) {
            val matcher = pattern.matcher(line.trim())
            if (matcher.matches()) {
                val file = matcher.group(1)
                val lineNum = matcher.group(2).toInt()
                val colNum = matcher.group(3).toInt()
                val message = matcher.group(4)

                diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, "ANDROID", message, DiagnosticSeverity.ERROR))
            }
        }
        return diagnostics
    }
}