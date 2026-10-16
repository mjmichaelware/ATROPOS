/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import java.nio.file.Path

class ToolchainExecutionDriver(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val processRunner = BoundedProcessRunner()

    fun executeCompile(lang: String, worktreeRoot: Path): ToolchainResult {
        val sanitizedEnv = sanitizeEnvironment()
        val driver = NativeToolchainRegistry.requireDriver(lang)
        val result = driver.compile(worktreeRoot)
        return redactResult(result)
    }

    fun executeTest(lang: String, worktreeRoot: Path): ToolchainResult {
        val sanitizedEnv = sanitizeEnvironment()
        val driver = NativeToolchainRegistry.requireDriver(lang)
        val result = driver.test(worktreeRoot)
        return redactResult(result)
    }

    fun executePackage(lang: String, worktreeRoot: Path): ToolchainResult {
        val sanitizedEnv = sanitizeEnvironment()
        val driver = NativeToolchainRegistry.requireDriver(lang)
        val result = driver.packageArtifact(worktreeRoot)
        return redactResult(result)
    }

    fun executeSandboxed(
        command: List<String>,
        worktreeRoot: Path,
        timeoutMillis: Long = 600_000L,
        maxOutputBytes: Int = 4 * 1024 * 1024
    ): ToolchainResult {
        val env = sanitizeEnvironment()
        val result = processRunner.run(
            command = command,
            directory = worktreeRoot,
            timeoutMillis = timeoutMillis,
            maxOutputBytes = maxOutputBytes,
            maxOutputLines = 8_000,
            environment = env
        )
        return ToolchainResult(
            exitCode = result.exitCode ?: 1,
            passed = result.exitCode == 0 && !result.timedOut,
            stdout = redactionFilter.redact(result.stdout),
            stderr = redactionFilter.redact(result.stderr),
            diagnostics = emptyList(),
            durationMs = result.durationMillis
        )
    }

    private fun sanitizeEnvironment(): Map<String, String> {
        val allowedKeys = setOf(
            "PATH", "HOME", "USER", "LANG", "LC_ALL", "TERM",
            "JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT", "CARGO_HOME", "GOROOT", "GOPATH",
            "GRADLE_HOME", "GRADLE_USER_HOME", "NODE_PATH", "NPM_CONFIG_PREFIX", "PYTHONPATH"
        )
        val cleanEnv = mutableMapOf<String, String>()
        for (key in allowedKeys) {
            val v = System.getenv(key)
            if (!v.isNullOrBlank()) {
                cleanEnv[key] = v
            }
        }
        return cleanEnv
    }

    private fun redactResult(result: ToolchainResult): ToolchainResult {
        return ToolchainResult(
            exitCode = result.exitCode,
            passed = result.passed,
            stdout = redactionFilter.redact(result.stdout),
            stderr = redactionFilter.redact(result.stderr),
            diagnostics = result.diagnostics,
            durationMs = result.durationMs
        )
    }
}