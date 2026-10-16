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

    private fun sanitizeEnvironment(): Map<String, String> {
        val allowedKeys = setOf(
            "PATH", "HOME", "USER", "LANG", "LC_ALL", "TERM",
            "JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT", "CARGO_HOME", "GOROOT", "GOPATH",
            "GRADLE_HOME", "GRADLE_USER_HOME", "NODE_PATH", "NPM_CONFIG_PREFIX", "PYTHONPATH"
        )
        val env = System.getenv().toMutableMap()
        val redactedKeys = env.keys.filterNot { it in allowedKeys }.toSet()
        for (key in redactedKeys) {
            env[key] = "[REDACTED]"
        }
        return env
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