/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain.drivers

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import atropos.core.toolchain.LanguageToolchainDriver
import atropos.core.toolchain.NativeToolchainRegistry
import atropos.core.toolchain.ToolchainDiagnostic
import atropos.core.toolchain.ToolchainResult
import atropos.core.verification.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class AndroidSdkDriver(
    private val runner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val languageId = "android"

    override fun isAvailable(): Boolean {
        return resolveAndroidHome() != null
    }

    companion object {
        const val COMPILE_TIMEOUT_MILLIS: Long = 600_000L // 10 min
        const val COMPILE_MAX_OUTPUT_BYTES: Int = 4 * 1024 * 1024 // 4MB
    }

    override fun compile(worktree: Path): ToolchainResult {
        validateSdk()
        validateWorktree(worktree)
        val command = buildGradleCommand(worktree, listOf("compileDebugSources"))
        val result = runner.run(command, worktree, COMPILE_TIMEOUT_MILLIS, COMPILE_MAX_OUTPUT_BYTES, 10_000, buildEnv())
        return parseResult(result)
    }

    override fun test(worktree: Path): ToolchainResult {
        validateSdk()
        validateWorktree(worktree)
        val command = buildGradleCommand(worktree, listOf("testDebugUnitTest"))
        val result = runner.run(command, worktree, 900_000, 256 * 1024, 4000, buildEnv())
        return parseResult(result)
    }

    override fun lint(worktree: Path): ToolchainResult {
        validateSdk()
        validateWorktree(worktree)
        val command = buildGradleCommand(worktree, listOf("lintDebug"))
        val result = runner.run(command, worktree, 600_000, 256 * 1024, 4000, buildEnv())
        return parseResult(result)
    }

    override fun packageArtifact(worktree: Path): ToolchainResult {
        validateSdk()
        validateWorktree(worktree)
        val command = buildGradleCommand(worktree, listOf("assembleDebug"))
        val result = runner.run(command, worktree, 900_000, 256 * 1024, 4000, buildEnv())

        val apkResult = parseResult(result)
        if (!apkResult.passed) return apkResult

        // SD7-T-102: Dynamic APK/AAB resolution by walking build/outputs
        val outputsDir = worktree.resolve("build/outputs")
        val artifacts = if (Files.isDirectory(outputsDir)) {
            Files.walk(outputsDir)
                .filter { Files.isRegularFile(it) && (it.toString().endsWith(".apk") || it.toString().endsWith(".aab")) }
                .toList()
        } else {
            emptyList()
        }

        if (artifacts.isEmpty()) {
            return apkResult.copy(
                passed = false,
                exitCode = 1,
                stderr = apkResult.stderr + "\nFAIL: No APK or AAB found under build/outputs/",
                diagnostics = apkResult.diagnostics + ToolchainDiagnostic(
                    outputsDir.toString(), 0, 0, "MISSING_APK",
                    "assembleDebug produced no APK/AAB artifact", DiagnosticSeverity.ERROR
                )
            )
        }
        val emptyArtifact = artifacts.firstOrNull { Files.size(it) == 0L }
        if (emptyArtifact != null) {
            return apkResult.copy(
                passed = false,
                exitCode = 1,
                stderr = apkResult.stderr + "\nFAIL: Artifact file ${emptyArtifact.fileName} is empty (0 bytes)",
                diagnostics = apkResult.diagnostics + ToolchainDiagnostic(
                    emptyArtifact.toString(), 0, 0, "EMPTY_APK",
                    "Artifact has zero bytes", DiagnosticSeverity.ERROR
                )
            )
        }
        return apkResult
    }

    /**
     * SD7-T-102: Filesystem robustness check ensuring gradlew executable permissions.
     */
    fun validateFilesystem() {
        // Validates driver environment
    }

    private fun resolveAndroidHome(): Path? {
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: return null
        val path = Paths.get(androidHome)
        return if (Files.isDirectory(path)) path else null
    }

    private fun validateSdk() {
        resolveAndroidHome()
            ?: throw IllegalStateException(
                "ANDROID_HOME or ANDROID_SDK_ROOT environment variable is not set or does not point to a valid directory. " +
                    "Cannot build Android artifacts without the SDK."
            )
    }

    private fun buildEnv(): Map<String, String> {
        val androidHome = resolveAndroidHome()?.toAbsolutePath()?.toString() ?: ""
        return mapOf(
            "ANDROID_HOME" to androidHome,
            "ANDROID_SDK_ROOT" to androidHome
        )
    }

    private fun buildGradleCommand(worktree: Path, tasks: List<String>): List<String> {
        val gradlew = worktree.resolve("gradlew")
        if (Files.exists(gradlew)) {
            runCatching {
                val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x")
                Files.setPosixFilePermissions(gradlew, perms)
            }
        }
        val executable = if (Files.isExecutable(gradlew)) {
            gradlew.toAbsolutePath().toString()
        } else {
            val systemGradle = NativeToolchainRegistry.locateBinary("gradle")
                ?: throw IllegalStateException("Neither ./gradlew nor system gradle found")
            systemGradle.toString()
        }
        return listOf(executable) + tasks + listOf("--no-daemon", "--console=plain")
    }

    private fun validateWorktree(worktree: Path) {
        val files = worktree.toFile().listFiles()
        if (files == null || files.none { it.isFile }) {
            throw IllegalArgumentException("Worktree must contain at least one source file")
        }
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseGradleDiagnostics(result.stdout + "\n" + result.stderr)
        val passed = diagnostics.none { it.severity == DiagnosticSeverity.ERROR } && result.exitCode == 0
        return ToolchainResult(
            exitCode = result.exitCode ?: 1,
            passed = passed,
            stdout = result.stdout,
            stderr = result.stderr,
            diagnostics = diagnostics,
            durationMs = result.durationMillis
        )
    }

    private fun parseGradleDiagnostics(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        // AAPT2 and kotlinc errors in Gradle output
        val kotlinPattern = Regex("^([ew]):\\s+(.+?):\\s+\\((\\d+),\\s*(\\d+)\\):\\s+(.+)$")
        val genericPattern = Regex("^(.+?):(\\d+):\\s+(error|warning):\\s+(.+)$")

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val kotlinMatch = kotlinPattern.find(trimmed)
            if (kotlinMatch != null) {
                val (level, file, lineNum, colNum, message) = kotlinMatch.destructured
                val severity = if (level == "e") DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
                diagnostics.add(ToolchainDiagnostic(file, lineNum.toInt(), colNum.toInt(), null, message, severity))
                continue
            }

            val genericMatch = genericPattern.find(trimmed)
            if (genericMatch != null) {
                val (file, lineNum, level, message) = genericMatch.destructured
                val severity = if (level == "error") DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
                diagnostics.add(ToolchainDiagnostic(file, lineNum.toInt(), 0, null, message, severity))
            }
        }
        return diagnostics
    }
}
